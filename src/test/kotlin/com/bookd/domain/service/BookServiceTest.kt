package com.bookd.domain.service

import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookDocumentStats
import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.Book
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun createBook(
        id: Int,
        title: String,
        coverWidth: Int? = null,
        coverHeight: Int? = null
    ): Book = Book(
        id = id,
        title = title,
        author = null,
        format = "epub",
        filePath = "/books/$title.epub",
        fileSize = 1024,
        coverWidth = coverWidth,
        coverHeight = coverHeight
    )

}
