package com.bookd.domain.service

import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.data.repository.AdjacentDocumentIndexes
import com.bookd.data.repository.ResourceInfo
import com.bookd.domain.model.Book
import com.bookd.domain.model.BookDocument
import com.bookd.domain.model.ChapterContent
import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import com.bookd.domain.service.parser.TxtParser
import com.bookd.infrastructure.cache.BookCacheService
import com.bookd.infrastructure.storage.BookImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookContentServiceTest {

    private lateinit var bookRepository: BookRepository
    private lateinit var documentRepository: BookDocumentRepository
    private lateinit var readingProgressRepository: ReadingProgressRepository
    private lateinit var txtParser: TxtParser
    private lateinit var imageStorage: BookImageStorage
    private lateinit var bookContentService: BookContentService

    @BeforeEach
    fun setUp() {
        bookRepository = mockk()
        documentRepository = mockk()
        readingProgressRepository = mockk()
        txtParser = mockk()
        imageStorage = mockk()
        bookContentService = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = txtParser,
            imageStorage = imageStorage,
            cacheService = null
        )
    }

    @AfterEach
    fun tearDown() {
        bookContentService.shutdown()
    }

    @Test
    fun `given chapter with images when loading content then image urls dimensions and navigation are preserved`() {
        val document = createDocument(id = 20, index = 1, title = "Chapter Two")
        coEvery { documentRepository.findByBookIdAndIndexAsync(bookId = 1, index = 1) } returns document
        coEvery { documentRepository.getDocumentContentAsync(20) } returns listOf(
            ContentElement.Paragraph(listOf(TextSpan("Intro"))),
            ContentElement.Image(src = "images/pic.jpg", alt = "pic"),
            ContentElement.Footnote(
                footnoteId = "fn-1",
                footnoteImage = "notes/note.png",
                contentSpans = listOf(TextSpan("Note"))
            ),
            ContentElement.Image(src = "images/missing.jpg", alt = "missing")
        )
        coEvery {
            documentRepository.findResourcesByBookIdAndPaths(
                1,
                setOf("images/pic.jpg", "notes/note.png", "images/missing.jpg")
            )
        } returns mapOf(
            "images/pic.jpg" to ResourceInfo(
                storedPath = "1/pic-stored.jpg",
                mediaType = "image/jpeg",
                width = 600,
                height = 300
            ),
            "notes/note.png" to ResourceInfo(
                storedPath = "1/note-stored.png",
                mediaType = "image/png",
                width = 100,
                height = 200
            )
        )
        coEvery { documentRepository.findAdjacentIndexes(bookId = 1, index = 1) } returns AdjacentDocumentIndexes(
            prevIndex = 0,
            nextIndex = 2
        )

        val result = runBlocking {
            bookContentService.getChapterContent(
                bookId = 1,
                index = 1,
                baseUrl = "http://localhost:7919"
            )
        }

        assertEquals(1, result?.index)
        assertEquals("Chapter Two", result?.title)
        assertEquals(0, result?.prevIndex)
        assertEquals(2, result?.nextIndex)
        val image = result.imageAt(1)
        assertEquals("http://localhost:7919/book_images/1/pic-stored.jpg", image.src)
        assertEquals(600, image.width)
        assertEquals(300, image.height)
        assertEquals(2.0, image.aspectRatio)
        val footnote = result.footnoteAt(2)
        assertEquals("http://localhost:7919/book_images/1/note-stored.png", footnote.footnoteImage)
        assertEquals(100, footnote.width)
        assertEquals(200, footnote.height)
        assertEquals(0.5, footnote.aspectRatio)
        val missingImage = result.imageAt(3)
        assertEquals("images/missing.jpg", missingImage.src)
        assertNull(missingImage.width)
        assertNull(missingImage.height)
        coVerify(exactly = 1) {
            documentRepository.findResourcesByBookIdAndPaths(
                1,
                setOf("images/pic.jpg", "notes/note.png", "images/missing.jpg")
            )
        }
        coVerify(exactly = 1) { documentRepository.findAdjacentIndexes(bookId = 1, index = 1) }
        verify(exactly = 0) { documentRepository.findResource(any(), any()) }
    }

    @Test
    fun `given missing chapter when loading content then null is returned`() {
        coEvery { documentRepository.findByBookIdAndIndexAsync(bookId = 1, index = 99) } returns null

        val result = runBlocking { bookContentService.getChapterContent(bookId = 1, index = 99) }

        assertNull(result)
        verify(exactly = 0) { documentRepository.getDocumentContent(any()) }
        verify(exactly = 0) { documentRepository.findByBookId(1) }
    }

    @Test
    fun `given missing file when parsing on demand then parse fails and parsed cache is not written`() {
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val service = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = txtParser,
            imageStorage = imageStorage,
            cacheService = cacheService
        )
        val missingPath = "/tmp/bookd-missing-file.epub"

        every { cacheService.isBookParsed(11) } returns null
        every { cacheService.tryAcquireParseLock(11) } returns true
        coEvery { bookRepository.findByIdAsync(11) } returns Book(
            id = 11,
            title = "Missing",
            author = null,
            format = "epub",
            filePath = missingPath,
            fileSize = 0L,
            chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(11, "parsing", 0) } returns 1
        coEvery { bookRepository.updateParseStatusAsync(11, "failed", 0) } returns 1

        val result = runBlocking { service.parseOnDemand(11, missingPath) }

        assertEquals(false, result)
        coVerify(exactly = 1) { bookRepository.updateParseStatusAsync(11, "failed", 0) }
        verify(exactly = 0) { cacheService.setBookParsed(11, true) }
        verify(exactly = 1) { cacheService.releaseParseLock(11) }
        service.shutdown()
    }

    @Test
    fun `given reparse is queued when book exists then parsed state is reset before launching parse`() {
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val taskCoordinator = mockk<BookTaskCoordinator>()
        val service = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = txtParser,
            imageStorage = imageStorage,
            cacheService = cacheService,
            taskCoordinator = taskCoordinator
        )
        coEvery { bookRepository.findByIdAsync(11) } returns Book(
            id = 11,
            title = "Parsed",
            author = null,
            format = "epub",
            filePath = "/books/parsed.epub",
            fileSize = 1024L,
            chaptersParsed = true
        )
        every { taskCoordinator.isContentParseInProgress(11) } returns false
        coEvery { bookRepository.resetChaptersParsedAsync(11) } returns 1
        every { taskCoordinator.launchContentParse(eq(11), any()) } returns true

        val result = runBlocking { service.queueForReparse(11) }

        assertEquals(true, result)
        verify(exactly = 1) { cacheService.clearBookCache(11) }
        coVerify(exactly = 1) { bookRepository.resetChaptersParsedAsync(11) }
        verify(exactly = 1) { taskCoordinator.launchContentParse(eq(11), any()) }
    }

    private fun ChapterContent?.imageAt(position: Int): ContentElement.Image {
        assertTrue(this?.elements?.get(position) is ContentElement.Image)
        return this!!.elements[position] as ContentElement.Image
    }

    private fun ChapterContent?.footnoteAt(position: Int): ContentElement.Footnote {
        assertTrue(this?.elements?.get(position) is ContentElement.Footnote)
        return this!!.elements[position] as ContentElement.Footnote
    }

    private fun createDocument(id: Int, index: Int, title: String): BookDocument = BookDocument(
        id = id,
        bookId = 1,
        index = index,
        inToc = true,
        title = title,
        wordCount = 100,
        imageCount = 1
    )
}
