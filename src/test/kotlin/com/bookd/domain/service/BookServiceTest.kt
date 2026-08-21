package com.bookd.domain.service

import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookDocumentStats
import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.Book
import com.bookd.infrastructure.storage.BookImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookServiceTest {

    private lateinit var bookRepository: BookRepository
    private lateinit var documentRepository: BookDocumentRepository
    private lateinit var bookService: BookService

    @BeforeEach
    fun setUp() {
        bookRepository = mockk()
        documentRepository = mockk()
        bookService = BookService(bookRepository, documentRepository)
    }

    @Test
    fun `given books with and without documents when listing books then statistics and cover ratio are preserved`() {
        val parsedBook = createBook(
            id = 1,
            title = "Parsed Book",
            coverWidth = 600,
            coverHeight = 300
        )
        val emptyBook = createBook(
            id = 2,
            title = "Empty Book",
            coverWidth = 1200,
            coverHeight = 0
        )
        coEvery { bookRepository.findAllAsync(limit = 20, offset = 0) } returns listOf(parsedBook, emptyBook)
        coEvery { documentRepository.findStatsByBookIds(listOf(1, 2)) } returns mapOf(
            1 to BookDocumentStats(chapterCount = 2, totalWordCount = 175, totalImageCount = 3)
        )

        val result = runBlocking { bookService.getAllBooks(limit = 20, offset = 0) }

        assertEquals(2, result.size)
        assertEquals(2, result[0].chapterCount)
        assertEquals(175, result[0].totalWordCount)
        assertEquals(3, result[0].totalImageCount)
        assertEquals(2.0, result[0].coverAspectRatio)
        assertEquals(0, result[1].chapterCount)
        assertEquals(0, result[1].totalWordCount)
        assertEquals(0, result[1].totalImageCount)
        assertNull(result[1].coverAspectRatio)
        coVerify(exactly = 1) { documentRepository.findStatsByBookIds(listOf(1, 2)) }
    }

    @Test
    fun `given a parsed book when loading by id then document statistics are preserved`() {
        val book = createBook(id = 3, title = "Book Detail", coverWidth = 900, coverHeight = 600)
        coEvery { bookRepository.findByIdAsync(3) } returns book
        coEvery { documentRepository.findStatsByBookIds(listOf(3)) } returns mapOf(
            3 to BookDocumentStats(chapterCount = 2, totalWordCount = 100, totalImageCount = 1)
        )

        val result = runBlocking { bookService.getBookById(3) }

        assertEquals(2, result?.chapterCount)
        assertEquals(100, result?.totalWordCount)
        assertEquals(1, result?.totalImageCount)
        assertEquals(1.5, result?.coverAspectRatio)
    }

    @Test
    fun `given precomputed statistics when listing books then document aggregation is skipped`() {
        val statsUpdatedAt = LocalDateTime(2026, 5, 25, 10, 0)
        val book = createBook(
            id = 4,
            title = "Precomputed Book",
            coverWidth = 300,
            coverHeight = 150,
            chapterCount = 8,
            totalWordCount = 12345,
            totalImageCount = 6,
            statsUpdatedAt = statsUpdatedAt
        )
        coEvery { bookRepository.findAllAsync(limit = 20, offset = 0) } returns listOf(book)

        val result = runBlocking { bookService.getAllBooks(limit = 20, offset = 0) }

        assertEquals(1, result.size)
        assertEquals(8, result[0].chapterCount)
        assertEquals(12345, result[0].totalWordCount)
        assertEquals(6, result[0].totalImageCount)
        assertEquals(2.0, result[0].coverAspectRatio)
        coVerify(exactly = 0) { documentRepository.findStatsByBookIds(any()) }
    }

    @Test
    fun `given search results missing statistics when searching then document statistics are enriched`() {
        val book = createBook(id = 5, title = "Searchable Book")
        coEvery { bookRepository.searchAsync("search", 20, 0, null) } returns listOf(book)
        coEvery { bookRepository.countSearchResultsAsync("search", null) } returns 1
        coEvery { documentRepository.findStatsByBookIds(listOf(5)) } returns mapOf(
            5 to BookDocumentStats(chapterCount = 3, totalWordCount = 900, totalImageCount = 2)
        )

        val result = runBlocking { bookService.searchBooks("search", 20, 0) }
        val count = runBlocking { bookService.getSearchCount("search") }

        assertEquals(1, result.size)
        assertEquals(3, result[0].chapterCount)
        assertEquals(900, result[0].totalWordCount)
        assertEquals(2, result[0].totalImageCount)
        assertEquals(1, count)
        coVerify(exactly = 1) { bookRepository.searchAsync("search", 20, 0, null) }
        coVerify(exactly = 1) { bookRepository.countSearchResultsAsync("search", null) }
        coVerify(exactly = 1) { documentRepository.findStatsByBookIds(listOf(5)) }
    }

    @Test
    fun `given valid cover upload when saving then storage dimensions and book cover are updated`() {
        val imageStorage = mockk<BookImageStorage>()
        val service = BookService(bookRepository, documentRepository, imageStorage)
        val bytes = byteArrayOf(1, 2, 3)

        coEvery { bookRepository.findByIdAsync(9) } returns createBook(id = 9, title = "Cover Book")
        every { imageStorage.extractImageDimensions(bytes) } returns (600 to 900)
        every { imageStorage.saveCover(9, "cover.png", bytes, isGenerated = false) } returns "/book_images/covers/cover.png"
        coEvery { bookRepository.updateCoverPathAsync(9, "/book_images/covers/cover.png", 600, 900) } returns 1

        val result = runBlocking { service.uploadCover(9, "cover.png", bytes) }

        assertTrue(result is CoverUploadResult.Saved)
        result as CoverUploadResult.Saved
        assertEquals("/book_images/covers/cover.png", result.coverPath)
        assertEquals(600, result.width)
        assertEquals(900, result.height)
        coVerify(exactly = 1) { bookRepository.updateCoverPathAsync(9, "/book_images/covers/cover.png", 600, 900) }
    }

    @Test
    fun `given valid generated cover when generating then book cover is updated`() {
        val coverGenerator = mockk<CoverGeneratorService>()
        val service = BookService(bookRepository, documentRepository, coverGeneratorService = coverGenerator)

        coEvery { bookRepository.findByIdAsync(12) } returns createBook(id = 12, title = "Generated")
        every { coverGenerator.generateCover(12, "Generated", null) } returns ("/book_images/covers/generated.png" to (400 to 600))
        coEvery { bookRepository.updateCoverPathAsync(12, "/book_images/covers/generated.png", 400, 600) } returns 1

        val result = runBlocking { service.generateCover(12) }

        assertTrue(result is CoverGenerateResult.Generated)
        result as CoverGenerateResult.Generated
        assertEquals("/book_images/covers/generated.png", result.coverPath)
        assertEquals(400, result.width)
        assertEquals(600, result.height)
        coVerify(exactly = 1) { bookRepository.updateCoverPathAsync(12, "/book_images/covers/generated.png", 400, 600) }
    }

    private fun createBook(
        id: Int,
        title: String,
        coverWidth: Int? = null,
        coverHeight: Int? = null,
        chapterCount: Int = 0,
        totalWordCount: Int = 0,
        totalImageCount: Int = 0,
        statsUpdatedAt: LocalDateTime? = null
    ): Book = Book(
        id = id,
        title = title,
        author = null,
        format = "epub",
        filePath = "/books/$title.epub",
        fileSize = 1024,
        coverWidth = coverWidth,
        coverHeight = coverHeight,
        chapterCount = chapterCount,
        totalWordCount = totalWordCount,
        totalImageCount = totalImageCount,
        statsUpdatedAt = statsUpdatedAt
    )

}
