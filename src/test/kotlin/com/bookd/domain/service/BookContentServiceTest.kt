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
import com.bookd.domain.service.parser.ParserFactory
import com.bookd.domain.service.parser.BookParser
import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import com.bookd.domain.service.parser.mobi.MobiTestFixtures
import com.bookd.infrastructure.cache.BookCacheService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
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
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class BookContentServiceTest {

    @TempDir
    lateinit var tempDir: Path

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
    fun `given image resource without base url when loading content then local image path and nullable aspect ratio are preserved`() {
        val document = createDocument(id = 21, index = 2, title = "Chapter Three")
        coEvery { documentRepository.findByBookIdAndIndexAsync(bookId = 1, index = 2) } returns document
        coEvery { documentRepository.getDocumentContentAsync(21) } returns listOf(
            ContentElement.Image(src = "images/zero-height.png", alt = null)
        )
        coEvery {
            documentRepository.findResourcesByBookIdAndPaths(1, setOf("images/zero-height.png"))
        } returns mapOf(
            "images/zero-height.png" to ResourceInfo(
                storedPath = "1/zero-height.png",
                mediaType = "image/png",
                width = 100,
                height = 0
            )
        )
        coEvery { documentRepository.findAdjacentIndexes(bookId = 1, index = 2) } returns AdjacentDocumentIndexes(
            prevIndex = null,
            nextIndex = null
        )

        val result = runBlocking { bookContentService.getChapterContent(bookId = 1, index = 2) }

        val image = result.imageAt(0)
        assertEquals("/book_images/1/zero-height.png", image.src)
        assertEquals(100, image.width)
        assertEquals(0, image.height)
        assertNull(image.aspectRatio)
    }

    @Test
    fun `given epub documents when loading manifest then document hrefs and anchor prefixes are exposed`() {
        coEvery { bookRepository.findByIdAsync(7) } returns Book(
            id = 7,
            title = "Linked EPUB",
            author = "Author",
            format = "epub",
            filePath = "/books/linked.epub",
            fileSize = 2048L,
            chaptersParsed = true
        )
        coEvery { documentRepository.findByBookIdAsync(7) } returns listOf(
            createDocument(id = 70, index = 0, title = "目录", href = "Text/nav.xhtml"),
            createDocument(id = 71, index = 1, title = "第一章", href = "Text/Chapter_1.xhtml"),
        )

        val result = runBlocking { bookContentService.getBookManifest(7) }

        requireNotNull(result)
        assertEquals(listOf(0, 1), result.spine)
        assertEquals(2, result.documents.size)
        assertEquals("Text/nav.xhtml", result.documents[0].href)
        assertEquals("Text/Chapter_1.xhtml", result.documents[1].href)
        assertTrue(result.documents[1].anchorPrefix?.startsWith("epub:") == true)
        assertTrue(result.documents[1].anchorPrefix?.endsWith("#") == true)
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
    fun `given one chapter fails when parsing on demand then no partial result is published`() {
        val file = File.createTempFile("bookd-partial-", ".txt").apply {
            writeText("Chapter 1\nfirst\nChapter 2\nsecond")
            deleteOnExit()
        }
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val service = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = txtParser,
            imageStorage = imageStorage,
            cacheService = cacheService
        )
        val chapters = listOf(
            TxtParser.ChapterInfo(index = 0, title = "Chapter 1", startPos = 0, endPos = 16),
            TxtParser.ChapterInfo(index = 1, title = "Chapter 2", startPos = 16, endPos = 32)
        )
        val structure = TxtParser.TxtStructure(chapters, file.readText())

        every { cacheService.isBookParsed(12) } returns null
        every { cacheService.tryAcquireParseLock(12) } returns true
        coEvery { bookRepository.findByIdAsync(12) } returns Book(
            id = 12,
            title = "Partial",
            author = null,
            format = "txt",
            filePath = file.path,
            fileSize = file.length(),
            chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(12, "parsing", 0) } returns 1
        coEvery { bookRepository.updateParseStatusAsync(12, "failed", 0) } returns 1
        coEvery { txtParser.parseStructure(file) } returns structure
        every { txtParser.extractChapterContent(structure.fullText, chapters[0]) } returns listOf(
            ContentElement.Paragraph(listOf(TextSpan("first")))
        )
        every { txtParser.extractChapterContent(structure.fullText, chapters[1]) } throws
            IllegalStateException("broken chapter")

        val result = runBlocking { service.parseOnDemand(12, file.path) }

        assertEquals(false, result)
        coVerify(exactly = 1) { bookRepository.updateParseStatusAsync(12, "failed", 0) }
        coVerify(exactly = 0) { documentRepository.replaceParsedBook(any(), any(), any()) }
        verify(exactly = 0) { cacheService.setBookParsed(12, true) }
        service.shutdown()
    }

    @Test
    fun `given supported extension with spoofed signature when parsing then no content or parsed cache is published`() {
        val file = tempDir.resolve("spoofed.pdf").toFile().apply { writeText("not a pdf") }
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val service = BookContentService(
            bookRepository = bookRepository,
            documentRepository = documentRepository,
            readingProgressRepository = readingProgressRepository,
            txtParser = txtParser,
            imageStorage = imageStorage,
            cacheService = cacheService
        )
        every { cacheService.isBookParsed(13) } returns null
        every { cacheService.tryAcquireParseLock(13) } returns true
        coEvery { bookRepository.findByIdAsync(13) } returns Book(
            id = 13,
            title = "Spoofed",
            author = null,
            format = "pdf",
            filePath = file.path,
            fileSize = file.length(),
            chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(13, any(), any()) } returns 1

        val result = runBlocking { service.parseOnDemand(13, file.path) }

        assertEquals(false, result)
        coVerify(exactly = 0) { documentRepository.replaceParsedBook(any(), any(), any()) }
        verify(exactly = 0) { cacheService.setBookParsed(13, true) }
        verify(exactly = 1) { cacheService.releaseParseLock(13) }
        service.shutdown()
    }

    @Test
    fun `given text PDF when parsing succeeds then complete content is published before parsed cache`() {
        val file = tempDir.resolve("text.pdf").toFile()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(72f, 720f)
                stream.showText("PDF service content")
                stream.endText()
            }
            document.save(file)
        }
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val service = BookContentService(
            bookRepository, documentRepository, readingProgressRepository, txtParser, imageStorage, cacheService
        )
        every { cacheService.isBookParsed(14) } returns null
        every { cacheService.tryAcquireParseLock(14) } returns true
        coEvery { bookRepository.findByIdAsync(14) } returns Book(
            id = 14, title = "PDF", author = null, format = "pdf", filePath = file.path,
            fileSize = file.length(), chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(14, any(), any()) } returns 1
        coEvery { documentRepository.replaceParsedBook(14, any(), emptyList()) } returns 1

        val result = runBlocking { service.parseOnDemand(14, file.path) }

        assertTrue(result)
        coVerify(exactly = 1) {
            documentRepository.replaceParsedBook(
                14,
                match { drafts -> drafts.single().elements.any { it is ContentElement.Paragraph } },
                emptyList()
            )
        }
        verify(exactly = 1) { cacheService.setBookParsed(14, true) }
        service.shutdown()
    }

    @Test
    fun `given normalized MOBI when parsing succeeds then EPUB content and resources are atomically published`() {
        val mobi = MobiTestFixtures.createMobi(tempDir.resolve("service.mobi")).toFile()
        val epub = MobiTestFixtures.createEpub(tempDir.resolve("service.epub")).toFile()
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val parserFactory = ParserFactory(txtParser, mobiNormalizer = { epub })
        val service = BookContentService(
            bookRepository, documentRepository, readingProgressRepository, txtParser, imageStorage, cacheService,
            parserFactory = parserFactory
        )
        every { cacheService.isBookParsed(15) } returns null
        every { cacheService.tryAcquireParseLock(15) } returns true
        coEvery { bookRepository.findByIdAsync(15) } returns Book(
            id = 15, title = "MOBI", author = null, format = "mobi", filePath = mobi.path,
            fileSize = mobi.length(), chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(15, any(), any()) } returns 1
        every { imageStorage.saveImage(15, any(), any()) } returns "15/image.png"
        every { imageStorage.detectMediaType(any()) } returns "image/png"
        every { imageStorage.extractImageDimensions(any()) } returns null
        coEvery { documentRepository.replaceParsedBook(15, any(), any()) } returns 1

        val result = runBlocking { service.parseOnDemand(15, mobi.path) }

        assertTrue(result)
        coVerify(exactly = 1) {
            documentRepository.replaceParsedBook(
                15,
                match { drafts -> drafts.single().elements.any { it is ContentElement.Heading } },
                match { resources -> resources.single().path == "image.png" }
            )
        }
        verify(exactly = 1) { cacheService.setBookParsed(15, true) }
        service.shutdown()
    }

    @Test
    fun `given image only PDF when parsing fails then no partial content or success cache is published`() {
        val file = tempDir.resolve("image-only.pdf").toFile()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(file)
        }
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val service = BookContentService(
            bookRepository, documentRepository, readingProgressRepository, txtParser, imageStorage, cacheService
        )
        every { cacheService.isBookParsed(16) } returns null
        every { cacheService.tryAcquireParseLock(16) } returns true
        coEvery { bookRepository.findByIdAsync(16) } returns Book(
            id = 16, title = "Image only", author = null, format = "pdf", filePath = file.path,
            fileSize = file.length(), chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(16, any(), any()) } returns 1

        val result = runBlocking { service.parseOnDemand(16, file.path) }

        assertEquals(false, result)
        coVerify(exactly = 0) { documentRepository.replaceParsedBook(any(), any(), any()) }
        verify(exactly = 0) { cacheService.setBookParsed(16, true) }
        service.shutdown()
    }

    @Test
    fun `given protected PDF when chapter list is requested then protected reason is preserved`() {
        val file = tempDir.resolve("protected.pdf").toFile().apply { writeText("%PDF-1.7") }
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val pdfParser = mockk<BookParser>()
        val service = BookContentService(
            bookRepository, documentRepository, readingProgressRepository, txtParser, imageStorage, cacheService,
            parserFactory = ParserFactory(txtParser, pdfParser = pdfParser)
        )
        val book = Book(
            id = 18, title = "Protected PDF", author = null, format = "pdf", filePath = file.path,
            fileSize = file.length(), chaptersParsed = false
        )
        every { cacheService.isBookParsed(18) } returns null
        every { cacheService.tryAcquireParseLock(18) } returns true
        coEvery { bookRepository.findByIdAsync(18) } returns book
        coEvery { bookRepository.updateParseStatusAsync(18, any(), any()) } returns 1
        coEvery { pdfParser.parseStructure(file) } throws EbookParseException(
            EbookParseFailureReason.PDF_PROTECTED,
            "PDF content extraction is not permitted"
        )

        val result = runBlocking { service.getChapterList(18) }

        assertEquals(ChapterListResult.ParseFailed(EbookParseFailureReason.PDF_PROTECTED), result)
        coVerify(exactly = 0) { documentRepository.replaceParsedBook(any(), any(), any()) }
        verify(exactly = 1) { cacheService.releaseParseLock(18) }
        service.shutdown()
    }

    @Test
    fun `given MOBI normalization failure when parsing then no partial content or success cache is published`() {
        val mobi = MobiTestFixtures.createMobi(tempDir.resolve("failed-service.mobi")).toFile()
        val cacheService = mockk<BookCacheService>(relaxed = true)
        val parserFactory = ParserFactory(txtParser, mobiNormalizer = {
            throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZATION_FAILED, "failed")
        })
        val service = BookContentService(
            bookRepository, documentRepository, readingProgressRepository, txtParser, imageStorage, cacheService,
            parserFactory = parserFactory
        )
        every { cacheService.isBookParsed(17) } returns null
        every { cacheService.tryAcquireParseLock(17) } returns true
        coEvery { bookRepository.findByIdAsync(17) } returns Book(
            id = 17, title = "Failed MOBI", author = null, format = "mobi", filePath = mobi.path,
            fileSize = mobi.length(), chaptersParsed = false
        )
        coEvery { bookRepository.updateParseStatusAsync(17, any(), any()) } returns 1

        val result = runBlocking { service.parseOnDemand(17, mobi.path) }

        assertEquals(false, result)
        coVerify(exactly = 0) { documentRepository.replaceParsedBook(any(), any(), any()) }
        verify(exactly = 0) { cacheService.setBookParsed(17, true) }
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

    private fun createDocument(id: Int, index: Int, title: String, href: String? = null): BookDocument = BookDocument(
        id = id,
        bookId = 1,
        index = index,
        href = href,
        inToc = true,
        title = title,
        wordCount = 100,
        imageCount = 1
    )
}
