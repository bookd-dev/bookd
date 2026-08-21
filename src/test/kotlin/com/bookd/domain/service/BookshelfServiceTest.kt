package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookshelfBooksPage
import com.bookd.data.repository.BookshelfItemRepository
import com.bookd.data.repository.BookshelfRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.BookWithProgress
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.BookshelfMembershipSummary
import com.bookd.domain.model.ReadingProgressResponse
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

class BookshelfServiceTest {

    private lateinit var bookshelfService: BookshelfService
    private lateinit var bookshelfRepository: BookshelfRepository
    private lateinit var bookshelfItemRepository: BookshelfItemRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var readingProgressRepository: ReadingProgressRepository

    private val userId = 1
    private val bookshelfId = 1

    @BeforeEach
    fun setUp() {
        bookshelfRepository = mockk()
        bookshelfItemRepository = mockk()
        bookRepository = mockk()
        readingProgressRepository = mockk()

        bookshelfService = BookshelfService(
            bookshelfRepository,
            bookshelfItemRepository,
            bookRepository,
            readingProgressRepository
        )
    }

    @Test
    fun `given ordered repository page when listing bookshelf books then response preserves order and progress`() {
        val bookshelf = createBookshelf()
        val progress2 = createProgress(2, LocalDateTime(2026, 1, 21, 10, 0))
        val progress1 = createProgress(1, LocalDateTime(2026, 1, 20, 10, 0))
        val page = BookshelfBooksPage(
            books = listOf(
                BookWithProgress(createBook(2, "Book 2"), progress2),
                BookWithProgress(createBook(1, "Book 1"), progress1),
                BookWithProgress(createBook(3, "Book 3"), null)
            ),
            total = 3
        )

        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every {
            bookshelfItemRepository.findBooksWithProgressByBookshelf(userId, bookshelfId, 20, 0)
        } returns page

        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertEquals(listOf(2, 1, 3), response.books.map { it.book.id })
        assertNotNull(response.books[0].progress)
        assertNull(response.books[2].progress)
        assertEquals(3, response.total)
        assertFalse(response.hasMore)
        verify(exactly = 1) {
            bookshelfItemRepository.findBooksWithProgressByBookshelf(userId, bookshelfId, 20, 0)
        }
    }

    @Test
    fun `given ordered repository page when async listing bookshelf books then response preserves order and progress`() = runBlocking {
        val bookshelf = createBookshelf()
        val progress2 = createProgress(2, LocalDateTime(2026, 1, 21, 10, 0))
        val progress1 = createProgress(1, LocalDateTime(2026, 1, 20, 10, 0))
        val page = BookshelfBooksPage(
            books = listOf(
                BookWithProgress(createBook(2, "Book 2"), progress2),
                BookWithProgress(createBook(1, "Book 1"), progress1),
                BookWithProgress(createBook(3, "Book 3"), null)
            ),
            total = 3
        )

        coEvery { bookshelfRepository.findByIdAsync(bookshelfId) } returns bookshelf
        coEvery {
            bookshelfItemRepository.findBooksWithProgressByBookshelfAsync(userId, bookshelfId, 20, 0)
        } returns page

        val result = bookshelfService.getBooksInBookshelfAsync(userId, bookshelfId, 20, 0)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertEquals(listOf(2, 1, 3), response.books.map { it.book.id })
        assertNotNull(response.books[0].progress)
        assertNull(response.books[2].progress)
        assertEquals(3, response.total)
        assertFalse(response.hasMore)
        coVerify(exactly = 1) {
            bookshelfItemRepository.findBooksWithProgressByBookshelfAsync(userId, bookshelfId, 20, 0)
        }
    }

    @Test
    fun `given paged repository result when listing bookshelf books then pagination metadata is preserved`() {
        val bookshelf = createBookshelf()
        val page = BookshelfBooksPage(
            books = listOf(
                BookWithProgress(createBook(3, "Book 3"), createProgress(3, LocalDateTime(2026, 1, 19, 10, 0))),
                BookWithProgress(createBook(4, "Book 4"), createProgress(4, LocalDateTime(2026, 1, 18, 10, 0)))
            ),
            total = 5
        )

        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every {
            bookshelfItemRepository.findBooksWithProgressByBookshelf(userId, bookshelfId, 2, 2)
        } returns page

        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, limit = 2, offset = 2)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertEquals(2, response.books.size)
        assertEquals(5, response.total)
        assertEquals(2, response.limit)
        assertEquals(2, response.offset)
        assertTrue(response.hasMore)
        assertEquals(listOf(3, 4), response.books.map { it.book.id })
    }

    @Test
    fun `given empty repository page when listing bookshelf books then empty response is returned`() {
        every { bookshelfRepository.findById(bookshelfId) } returns createBookshelf()
        every {
            bookshelfItemRepository.findBooksWithProgressByBookshelf(userId, bookshelfId, 20, 0)
        } returns BookshelfBooksPage(emptyList(), total = 0)

        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        assertTrue(response.books.isEmpty())
        assertEquals(0, response.total)
        assertFalse(response.hasMore)
    }

    @Test
    fun `given user bookshelves when listing then counts are loaded in one batch`() {
        val shelves = listOf(
            createBookshelf(id = 1, name = "全部"),
            createBookshelf(id = 2, name = "Favorites")
        )
        every { bookshelfRepository.findByUserId(userId) } returns shelves
        every { bookshelfItemRepository.countByBookshelfIds(listOf(1, 2)) } returns mapOf(1 to 10L, 2 to 3L)

        val result = bookshelfService.getUserBookshelves(userId)

        assertEquals(listOf(10, 3), result.map { it.bookCount })
        verify(exactly = 1) { bookshelfItemRepository.countByBookshelfIds(listOf(1, 2)) }
        verify(exactly = 0) { bookshelfItemRepository.countByBookshelf(any()) }
    }

    @Test
    fun `given book membership when loading summary then repository summary is returned`() {
        val summary = BookshelfMembershipSummary(
            bookshelves = listOf(createBookshelf(id = 1, name = "全部").copy(bookCount = 5)),
            inDefaultBookshelf = true
        )
        every { bookshelfItemRepository.findUserBookshelfMembershipSummary(userId, 7) } returns summary

        val result = bookshelfService.getBookshelfMembershipSummary(userId, 7)

        assertEquals(summary, result)
        assertTrue(bookshelfService.isBookInDefaultBookshelf(userId, 7))
        assertEquals(1, bookshelfService.getBookshelvesForBook(userId, 7).size)
        verify(exactly = 3) { bookshelfItemRepository.findUserBookshelfMembershipSummary(userId, 7) }
    }

    @Test
    fun `given missing bookshelf when listing books then failure is returned`() {
        every { bookshelfRepository.findById(bookshelfId) } returns null

        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)

        assertTrue(result.isFailure)
    }

    @Test
    fun `given bookshelf owned by another user when listing books then failure is returned`() {
        every { bookshelfRepository.findById(bookshelfId) } returns createBookshelf(userId = 999)

        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)

        assertTrue(result.isFailure)
    }

    private fun createBookshelf(
        id: Int = bookshelfId,
        userId: Int = this.userId,
        name: String = "全部"
    ): Bookshelf {
        val now = LocalDateTime(2026, 1, 21, 0, 0)
        return Bookshelf(
            id = id,
            userId = userId,
            name = name,
            description = null,
            sortOrder = 0,
            bookCount = 0,
            isSystemDefault = name == "全部",
            createdAt = now,
            updatedAt = now
        )
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

    private fun createProgress(bookId: Int, lastReadAt: LocalDateTime): ReadingProgressResponse {
        return ReadingProgressResponse(
            id = bookId,
            bookId = bookId,
            progress = 0.5,
            currentPage = 50,
            totalPages = 100,
            cfiLocation = null,
            documentId = null,
            deviceId = null,
            lastReadAt = lastReadAt,
            chapterPageIndex = null,
            chapterTotalPages = null,
            chapterScrollPercent = null
        )
    }
}
