package com.bookd.domain.service

import com.bookd.domain.model.Book
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.BookshelfMembershipSummary
import com.bookd.domain.model.ReadingProgressResponse
import com.bookd.domain.model.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookDetailServiceTest {

    private lateinit var bookDetailService: BookDetailService
    private lateinit var bookService: BookService
    private lateinit var tagService: TagService
    private lateinit var readingService: ReadingService
    private lateinit var bookshelfService: BookshelfService

    private val bookId = 1
    private val userId = 1

    @BeforeEach
    fun setUp() {
        bookService = mockk()
        tagService = mockk()
        readingService = mockk()
        bookshelfService = mockk()

        bookDetailService = BookDetailService(
            bookService,
            tagService,
            readingService,
            bookshelfService
        )
    }

    @Test
    fun `given all services succeed when loading detail then data is aggregated with one bookshelf summary call`() {
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"), createTag(2, "Fantasy"))
        val progress = createProgress(bookId)
        val membership = BookshelfMembershipSummary(
            bookshelves = listOf(createBookshelf(1, "全部"), createBookshelf(2, "Favorites")),
            inDefaultBookshelf = true
        )

        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        coEvery { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelfMembershipSummary(userId, bookId) } returns membership

        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }

        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(tags, result.tags)
        assertEquals(progress, result.readingProgress)
        assertEquals(2, result.bookshelves.size)
        assertTrue(result.inDefaultBookshelf)
        coVerify(exactly = 1) { bookService.getBookById(bookId) }
        verify(exactly = 1) { bookshelfService.getBookshelfMembershipSummary(userId, bookId) }
        verify(exactly = 0) { bookshelfService.getBookshelvesForBook(any(), any()) }
        verify(exactly = 0) { bookshelfService.isBookInDefaultBookshelf(any(), any()) }
    }

    @Test
    fun `given missing book when loading detail then optional services are not called`() {
        coEvery { bookService.getBookById(bookId) } returns null

        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }

        assertNull(result)
        coVerify(exactly = 1) { bookService.getBookById(bookId) }
        verify(exactly = 0) { tagService.getTagsForBook(any()) }
        coVerify(exactly = 0) { readingService.getProgress(any(), any()) }
        verify(exactly = 0) { bookshelfService.getBookshelfMembershipSummary(any(), any()) }
    }

    @Test
    fun `given optional service failures when loading detail then base book is still returned`() {
        val book = createBook(bookId, "Test Book")

        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } throws RuntimeException("Tag service error")
        coEvery { readingService.getProgress(userId, bookId) } throws RuntimeException("Reading service error")
        every { bookshelfService.getBookshelfMembershipSummary(userId, bookId) } throws RuntimeException("Bookshelf service error")

        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }

        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertTrue(result.tags.isEmpty())
        assertNull(result.readingProgress)
        assertTrue(result.bookshelves.isEmpty())
        assertFalse(result.inDefaultBookshelf)
    }

    @Test
    fun `given no reading progress and no bookshelf membership when loading detail then nullable fields are preserved`() {
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"))

        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        coEvery { readingService.getProgress(userId, bookId) } returns null
        every { bookshelfService.getBookshelfMembershipSummary(userId, bookId) } returns BookshelfMembershipSummary(
            bookshelves = emptyList(),
            inDefaultBookshelf = false
        )

        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }

        assertNotNull(result)
        assertEquals(tags, result!!.tags)
        assertNull(result.readingProgress)
        assertTrue(result.bookshelves.isEmpty())
        assertFalse(result.inDefaultBookshelf)
    }

    private fun createBook(id: Int, title: String): Book {
        val now = LocalDateTime(2026, 1, 21, 0, 0)
        return Book(
            id = id,
            title = title,
            author = "Test Author",
            format = "epub",
            filePath = "/books/$id.epub",
            fileSize = 1024L,
            sourceId = 1,
            chapterCount = 10,
            totalWordCount = 50000,
            totalImageCount = 0,
            chaptersParsed = true,
            chaptersCount = 10,
            lastParsedAt = now,
            parseStatus = "completed",
            parseProgress = 100,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun createTag(id: Int, name: String): Tag {
        val now = LocalDateTime(2026, 1, 21, 0, 0)
        return Tag(id = id, name = name, createdAt = now)
    }

    private fun createProgress(bookId: Int): ReadingProgressResponse {
        return ReadingProgressResponse(
            id = bookId,
            bookId = bookId,
            progress = 0.5,
            currentPage = 50,
            totalPages = 100,
            cfiLocation = null,
            documentId = null,
            deviceId = null,
            lastReadAt = LocalDateTime(2026, 1, 21, 10, 0),
            chapterPageIndex = null,
            chapterTotalPages = null,
            chapterScrollPercent = null
        )
    }

    private fun createBookshelf(id: Int, name: String): Bookshelf {
        val now = LocalDateTime(2026, 1, 21, 0, 0)
        return Bookshelf(
            id = id,
            userId = userId,
            name = name,
            description = null,
            sortOrder = id,
            bookCount = 0,
            isSystemDefault = name == "全部",
            createdAt = now,
            updatedAt = now
        )
    }
}
