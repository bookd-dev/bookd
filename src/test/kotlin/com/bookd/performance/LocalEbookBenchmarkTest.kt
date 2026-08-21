package com.bookd.performance

import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.BookSources
import com.bookd.data.entity.BookTags
import com.bookd.data.entity.Bookmarks
import com.bookd.data.entity.Books
import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Bookshelves
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import com.bookd.data.entity.FolderPermissions
import com.bookd.data.entity.InviteTokens
import com.bookd.data.entity.ReaderSettings
import com.bookd.data.entity.ReadingProgress
import com.bookd.data.entity.Sessions
import com.bookd.data.entity.Tags
import com.bookd.data.entity.TxtParseRules
import com.bookd.data.entity.Users
import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookshelfItemRepository
import com.bookd.data.repository.BookshelfRepository
import com.bookd.data.repository.BookmarkRepository
import com.bookd.data.repository.ReaderSettingsRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.data.repository.TagRepository
import com.bookd.data.repository.TxtParseRuleRepository
import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.UserRole
import com.bookd.domain.service.BookContentService
import com.bookd.domain.service.BookDetailService
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookshelfService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.domain.service.ReadingService
import com.bookd.domain.service.TagService
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.domain.service.UserService
import com.bookd.domain.service.parser.TxtParser
import com.bookd.infrastructure.storage.BookImageStorage
import com.bookd.infrastructure.time.TimeProvider
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.math.roundToLong
import kotlin.time.measureTime

class LocalEbookBenchmarkTest {

    @Test
    fun `benchmark local ebook library hot paths`() = runBlocking {
        val ebookDirPath = benchmarkSetting("bookd.benchmark.ebookDir", "BOOKD_BENCHMARK_EBOOK_DIR")
        assumeTrue(
            !ebookDirPath.isNullOrBlank(),
            "Set bookd.benchmark.ebookDir or BOOKD_BENCHMARK_EBOOK_DIR to run the local ebook benchmark."
        )
        val ebookDir = File(ebookDirPath)
        require(ebookDir.isDirectory) { "E-book directory does not exist: ${ebookDir.absolutePath}" }

        val parseLimit = benchmarkSetting("bookd.benchmark.parseLimit", "BOOKD_BENCHMARK_PARSE_LIMIT")?.toIntOrNull() ?: 60
        val files = ebookDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("epub", "txt") }
            .sortedBy { it.absolutePath }
            .toList()
        require(files.isNotEmpty()) { "No EPUB/TXT files found under ${ebookDir.absolutePath}" }

        Database.connect(
            url = "jdbc:h2:mem:local_ebook_benchmark_${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                Users,
                Books,
                BookSources,
                Tags,
                BookTags,
                ReadingProgress,
                FolderPermissions,
                Sessions,
                InviteTokens,
                Bookmarks,
                ReaderSettings,
                BookDocuments,
                DocumentContents,
                DocumentResources,
                TxtParseRules,
                Bookshelves,
                BookshelfItems
            )
        }

        val imageDir = Files.createTempDirectory("bookd-local-ebook-benchmark-images").toFile()
        val bookRepository = BookRepository()
        val documentRepository = BookDocumentRepository()
        val userRepository = UserRepository()
        val tagRepository = TagRepository()
        val readingProgressRepository = ReadingProgressRepository()
        val bookmarkRepository = BookmarkRepository()
        val readerSettingsRepository = ReaderSettingsRepository()
        val bookshelfRepository = BookshelfRepository()
        val bookshelfItemRepository = BookshelfItemRepository()
        val txtParseRuleRepository = TxtParseRuleRepository()

        val bookService = BookService(bookRepository, documentRepository)
        val tagService = TagService(tagRepository, bookRepository)
        val readingService = ReadingService(readingProgressRepository, bookmarkRepository, readerSettingsRepository)
        val bookshelfService = BookshelfService(bookshelfRepository, bookshelfItemRepository, bookRepository, readingProgressRepository)
        val userService = UserService(userRepository)
        val imageStorage = BookImageStorage(imageDir.absolutePath)
        val contentService = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = TxtParser(TxtParseRuleService(txtParseRuleRepository)),
            imageStorage = imageStorage,
            cacheService = null
        )

        try {
            val importTime = measureTime {
                files.forEachIndexed { index, file ->
                    bookRepository.create(
                        title = file.nameWithoutExtension,
                        author = null,
                        format = file.extension.lowercase(),
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        sourceId = null
                    )
                }
            }
            val allBooks = bookRepository.findAll(limit = files.size, offset = 0)
            val parseCandidates = allBooks.take(parseLimit.coerceAtMost(allBooks.size))
            var parsedCount = 0
            val heapMonitor = PeakHeapMonitor()
            val parseTime = try {
                measureTime {
                    parseCandidates.forEach { book ->
                        if (contentService.parseOnDemand(book.id, book.filePath)) {
                            parsedCount++
                        }
                    }
                }
            } finally {
                heapMonitor.close()
            }
            val totalDocuments = transaction { BookDocuments.selectAll().count() }
            val totalDocumentContents = transaction { DocumentContents.selectAll().count() }
            val backfilled = bookRepository.backfillMissingStatistics()

            val user = userRepository.create(
                username = "bench",
                hashedPassword = "hash",
                email = null,
                role = UserRole.ADMIN.value
            )
            val token = userRepository.createSession(user.id)
            val defaultShelf = bookshelfRepository.create(
                userId = user.id,
                name = BookshelfService.DEFAULT_BOOKSHELF_NAME,
                isSystemDefault = true,
                sortOrder = -1
            )
            val shelves = mutableListOf(defaultShelf)
            repeat(24) { index ->
                shelves += bookshelfRepository.create(
                    userId = user.id,
                    name = "Benchmark Shelf ${index + 1}",
                    isSystemDefault = false,
                    sortOrder = index
                )
            }
            allBooks.forEachIndexed { bookIndex, book ->
                bookshelfItemRepository.addBook(defaultShelf.id, book.id)
                shelves.drop(1).forEachIndexed { shelfIndex, shelf ->
                    if (bookIndex == 0 || (bookIndex + shelfIndex) % 4 == 0) {
                        bookshelfItemRepository.addBook(shelf.id, book.id)
                    }
                }
            }
            allBooks.take(100).forEachIndexed { index, book ->
                readingProgressRepository.upsert(
                    userId = user.id,
                    bookId = book.id,
                    progress = (index + 1) / 100.0,
                    currentPage = index,
                    totalPages = 100,
                    cfiLocation = null,
                    documentId = null,
                    deviceId = null
                )
            }

            val bookDetailService = BookDetailService(bookService, tagService, readingService, bookshelfService)
            val detailBookId = allBooks.first().id
            val benchmarkTagNames = (1..40).map { "benchmark-v3-tag-$it" }
            val benchmarkResourcePaths = transaction {
                DocumentResources.selectAll()
                    .limit(100)
                    .map { it[DocumentResources.storedPath] }
            }

            val scenarios = listOf(
                benchmarkScenario("Book list limit=100 current") {
                    runBlocking { bookService.getAllBooks(limit = 100, offset = 0) }
                },
                benchmarkScenario("Book list limit=100 legacy simulated") {
                    legacyBookList(bookRepository, documentRepository, limit = 100, offset = 0)
                },
                benchmarkScenario("Bookshelf page limit=50 current") {
                    bookshelfService.getBooksInBookshelf(user.id, defaultShelf.id, limit = 50, offset = 0)
                },
                benchmarkScenario("Bookshelf page limit=50 legacy simulated") {
                    legacyBookshelfPage(
                        userId = user.id,
                        bookshelfId = defaultShelf.id,
                        limit = 50,
                        offset = 0,
                        bookRepository = bookRepository,
                        bookshelfItemRepository = bookshelfItemRepository,
                        readingProgressRepository = readingProgressRepository
                    )
                },
                benchmarkScenario("Book detail current") {
                    runBlocking { bookDetailService.getBookDetail(detailBookId, user.id) }
                },
                benchmarkScenario("Book detail legacy simulated") {
                    runBlocking {
                        legacyBookDetail(
                            bookId = detailBookId,
                            userId = user.id,
                            bookService = bookService,
                            tagService = tagService,
                            readingService = readingService,
                            bookshelfRepository = bookshelfRepository,
                            bookshelfItemRepository = bookshelfItemRepository
                        )
                    }
                },
                benchmarkScenario("Duplicate detection current batched") {
                    bookRepository.findByFilePaths(files.map { it.absolutePath })
                },
                benchmarkScenario("Duplicate detection legacy per-file") {
                    files.forEach { bookRepository.findByFilePath(it.absolutePath) }
                },
                benchmarkScenario("Token validation current cached x1000") {
                    repeat(1000) { userService.validateToken(token) }
                },
                benchmarkScenario("Token validation legacy repository x1000") {
                    repeat(1000) { userRepository.findUserByToken(token) }
                },
                benchmarkScenario("Tag link current batched x40") {
                    val tagsByName = tagRepository.findOrCreateTags(benchmarkTagNames)
                    tagRepository.addTagsToBook(detailBookId, tagsByName.values.map { it.id })
                },
                benchmarkScenario("Tag link legacy per-tag x40") {
                    benchmarkTagNames.forEach { tagName ->
                        val tag = tagRepository.findOrCreateTag(tagName)
                        tagRepository.addTagToBook(detailBookId, tag.id)
                    }
                },
                benchmarkScenario("Resource dimension IO current outside transaction") {
                    benchmarkResourcePaths.forEach { storedPath ->
                        imageStorage.extractImageDimensionsFromFile(storedPath)
                    }
                },
                benchmarkScenario("Resource dimension IO legacy transaction wrapped") {
                    transaction {
                        benchmarkResourcePaths.forEach { storedPath ->
                            imageStorage.extractImageDimensionsFromFile(storedPath)
                        }
                    }
                }
            )

            println(
                """
                |LOCAL_EBOOK_BENCHMARK
                |path=${ebookDir.absolutePath}
                |files=${files.size}
                |books=${allBooks.size}
                |bookshelves=${shelves.size}
                |parsed=$parsedCount
                |documents=$totalDocuments
                |documentContents=$totalDocumentContents
                |resourceDimensionSamples=${benchmarkResourcePaths.size}
                |backfilled=$backfilled
                |import_ms=${importTime.inWholeMilliseconds}
                |parse_ms=${parseTime.inWholeMilliseconds}
                |parse_peak_heap_mib=${heapMonitor.peakBytes / 1024.0 / 1024.0}
                |${scenarios.joinToString(separator = "\n") { it.format() }}
                """.trimMargin()
            )
        } finally {
            contentService.shutdown()
            imageDir.deleteRecursively()
        }
    }

    private fun benchmarkScenario(name: String, block: () -> Unit): BenchmarkResult {
        repeat(5) { block() }
        val measurements = (1..30).map {
            measureTime { block() }.inWholeNanoseconds / 1_000_000.0
        }.sorted()
        return BenchmarkResult(
            name = name,
            medianMs = measurements[measurements.size / 2],
            minMs = measurements.first(),
            maxMs = measurements.last()
        )
    }

    private fun legacyBookList(
        bookRepository: BookRepository,
        documentRepository: BookDocumentRepository,
        limit: Int,
        offset: Long
    ): List<Book> {
        return bookRepository.findAll(limit, offset).map { book ->
            val documents = documentRepository.findByBookId(book.id)
            book.copy(
                chapterCount = documents.count { it.inToc },
                totalWordCount = documents.sumOf { it.wordCount },
                totalImageCount = documents.sumOf { it.imageCount },
                coverAspectRatio = if (book.coverWidth != null && book.coverHeight != null && book.coverHeight > 0) {
                    book.coverWidth.toDouble() / book.coverHeight
                } else {
                    null
                }
            )
        }
    }

    private fun legacyBookshelfPage(
        userId: Int,
        bookshelfId: Int,
        limit: Int,
        offset: Long,
        bookRepository: BookRepository,
        bookshelfItemRepository: BookshelfItemRepository,
        readingProgressRepository: ReadingProgressRepository
    ) {
        val allBookIds = bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0)
        val progressMap = runBlocking { readingProgressRepository.findByUserAndBooks(userId, allBookIds) }
        val (readBooks, unreadBooks) = allBookIds.partition { progressMap.containsKey(it) }
        val sortedBookIds = readBooks.sortedByDescending { progressMap[it]?.lastReadAt } + unreadBooks
        val pagedBookIds = sortedBookIds.drop(offset.toInt()).take(limit)
        bookRepository.findAllById(pagedBookIds)
    }

    private suspend fun legacyBookDetail(
        bookId: Int,
        userId: Int,
        bookService: BookService,
        tagService: TagService,
        readingService: ReadingService,
        bookshelfRepository: BookshelfRepository,
        bookshelfItemRepository: BookshelfItemRepository
    ) {
        bookService.getBookById(bookId) ?: return
        tagService.getTagsForBook(bookId)
        readingService.getProgress(userId, bookId)
        val bookshelves = bookshelfItemRepository.findUserBookshelvesForBook(userId, bookId)
        bookshelves.forEach { bookshelfItemRepository.countByBookshelf(it.id) }
        val defaultBookshelf = bookshelfRepository.getDefaultBookshelf(userId)
        if (defaultBookshelf != null) {
            bookshelfItemRepository.isBookInBookshelf(defaultBookshelf.id, bookId)
        }
    }

    private data class BenchmarkResult(
        val name: String,
        val medianMs: Double,
        val minMs: Double,
        val maxMs: Double
    ) {
        fun format(): String {
            return "$name median_ms=${medianMs.round(3)} min_ms=${minMs.round(3)} max_ms=${maxMs.round(3)}"
        }

        private fun Double.round(decimals: Int): String {
            val scale = Math.pow(10.0, decimals.toDouble())
            return (this * scale).roundToLong().toDouble().div(scale).toString()
        }
    }

    private class PeakHeapMonitor : AutoCloseable {
        private val running = AtomicBoolean(true)
        private val peak = AtomicLong(ManagementFactory.getMemoryMXBean().heapMemoryUsage.used)
        private val monitor = Thread.ofPlatform().daemon().name("bookd-heap-monitor").start {
            while (running.get()) {
                val used = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                peak.accumulateAndGet(used, ::maxOf)
                LockSupport.parkNanos(2_000_000L)
            }
        }

        val peakBytes: Long
            get() = peak.get()

        override fun close() {
            running.set(false)
            monitor.join()
            peak.accumulateAndGet(ManagementFactory.getMemoryMXBean().heapMemoryUsage.used, ::maxOf)
        }
    }

    private fun benchmarkSetting(systemProperty: String, environmentVariable: String): String? {
        return System.getProperty(systemProperty)?.takeIf { it.isNotBlank() }
            ?: System.getenv(environmentVariable)?.takeIf { it.isNotBlank() }
    }
}
