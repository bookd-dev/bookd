package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookshelfItemRepository
import com.bookd.data.repository.BookshelfRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.ReadingProgressResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 书架服务单元测试
 * 
 * 测试重点：
 * 1. 书籍按最新阅读时间排序
 * 2. 未读书籍排在最后
 * 3. 分页逻辑
 * 4. 批量查询优化
 */
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
    fun `should sort books by lastReadAt descending`() {
        // Given: 3本书，阅读时间分别为昨天、今天、前天
        val bookshelf = createBookshelf()
        val bookIds = listOf(1, 2, 3)
        
        val book1 = createBook(1, "Book 1")
        val book2 = createBook(2, "Book 2")
        val book3 = createBook(3, "Book 3")
        
        // 阅读时间：book2(今天) > book1(昨天) > book3(前天)
        val progress1 = createProgress(1, LocalDateTime(2026, 1, 20, 10, 0))  // 昨天
        val progress2 = createProgress(2, LocalDateTime(2026, 1, 21, 10, 0))  // 今天
        val progress3 = createProgress(3, LocalDateTime(2026, 1, 19, 10, 0))  // 前天
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns bookIds
        every { readingProgressRepository.findByUserAndBooks(userId, bookIds) } returns mapOf(
            1 to progress1,
            2 to progress2,
            3 to progress3
        )
        every { bookRepository.findAllById(listOf(2, 1, 3)) } returns listOf(book2, book1, book3)
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        
        assertEquals(3, response.books.size)
        // 排序后：book2(今天), book1(昨天), book3(前天)
        assertEquals(2, response.books[0].book.id)
        assertEquals(1, response.books[1].book.id)
        assertEquals(3, response.books[2].book.id)
        
        // 验证进度也正确返回
        assertNotNull(response.books[0].progress)
        assertEquals(2, response.books[0].progress!!.bookId)
    }
    
    @Test
    fun `should put unread books at the end`() {
        // Given: 3本书，其中1本未读
        val bookshelf = createBookshelf()
        val bookIds = listOf(1, 2, 3)
        
        val book1 = createBook(1, "Book 1")
        val book2 = createBook(2, "Book 2 - Unread")  // 未读
        val book3 = createBook(3, "Book 3")
        
        // book2 没有阅读进度
        val progress1 = createProgress(1, LocalDateTime(2026, 1, 20, 10, 0))
        val progress3 = createProgress(3, LocalDateTime(2026, 1, 19, 10, 0))
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns bookIds
        every { readingProgressRepository.findByUserAndBooks(userId, bookIds) } returns mapOf(
            1 to progress1,
            3 to progress3
            // book2 没有进度记录
        )
        every { bookRepository.findAllById(listOf(1, 3, 2)) } returns listOf(book1, book3, book2)
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        
        assertEquals(3, response.books.size)
        // 排序后：book1(昨天), book3(前天), book2(未读排最后)
        assertEquals(1, response.books[0].book.id)
        assertEquals(3, response.books[1].book.id)
        assertEquals(2, response.books[2].book.id)
        
        // 验证未读书籍的 progress 为 null
        assertNull(response.books[2].progress)
    }
    
    @Test
    fun `should handle all books unread`() {
        // Given: 3本书都未读
        val bookshelf = createBookshelf()
        val bookIds = listOf(1, 2, 3)
        
        val book1 = createBook(1, "Book 1")
        val book2 = createBook(2, "Book 2")
        val book3 = createBook(3, "Book 3")
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns bookIds
        every { readingProgressRepository.findByUserAndBooks(userId, bookIds) } returns emptyMap()
        every { bookRepository.findAllById(listOf(1, 2, 3)) } returns listOf(book1, book2, book3)
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        
        assertEquals(3, response.books.size)
        // 全部未读，保持原有顺序
        assertEquals(1, response.books[0].book.id)
        assertEquals(2, response.books[1].book.id)
        assertEquals(3, response.books[2].book.id)
        
        // 所有 progress 都为 null
        response.books.forEach { assertNull(it.progress) }
    }
    
    @Test
    fun `should apply pagination correctly`() {
        // Given: 5本书，请求第2页(offset=2, limit=2)
        val bookshelf = createBookshelf()
        val bookIds = listOf(1, 2, 3, 4, 5)
        
        // 创建 5 本书，都有阅读进度
        val books = (1..5).map { createBook(it, "Book $it") }
        val progresses = (1..5).map { 
            it to createProgress(it, LocalDateTime(2026, 1, 21 - it, 10, 0))  // 1最新，5最旧
        }.toMap()
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns bookIds
        every { readingProgressRepository.findByUserAndBooks(userId, bookIds) } returns progresses
        every { bookRepository.findAllById(listOf(3, 4)) } returns listOf(books[2], books[3])
        
        // When: 请求第2页
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, limit = 2, offset = 2)
        
        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        
        assertEquals(2, response.books.size)
        assertEquals(5, response.total)
        assertEquals(2, response.limit)
        assertEquals(2, response.offset)
        assertTrue(response.hasMore)
        
        // 排序后：1,2,3,4,5 -> 取 offset=2, limit=2 -> [3, 4]
        assertEquals(3, response.books[0].book.id)
        assertEquals(4, response.books[1].book.id)
    }
    
    @Test
    fun `should handle empty bookshelf`() {
        // Given: 空书架
        val bookshelf = createBookshelf()
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns emptyList()
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()!!
        
        assertTrue(response.books.isEmpty())
        assertEquals(0, response.total)
        assertFalse(response.hasMore)
    }
    
    @Test
    fun `should call batch query for reading progress`() {
        // Given
        val bookshelf = createBookshelf()
        val bookIds = listOf(1, 2, 3)
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        every { bookshelfItemRepository.findBookIdsByBookshelf(bookshelfId, Int.MAX_VALUE, 0) } returns bookIds
        every { readingProgressRepository.findByUserAndBooks(userId, bookIds) } returns emptyMap()
        every { bookRepository.findAllById(bookIds) } returns listOf(createBook(1, "Book"), createBook(2, "Book"), createBook(3, "Book"))
        
        // When
        bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then: 验证调用了批量查询方法（而不是单个查询）
        verify(exactly = 1) { readingProgressRepository.findByUserAndBooks(userId, bookIds) }
    }
    
    @Test
    fun `should return error when bookshelf not found`() {
        // Given
        every { bookshelfRepository.findById(bookshelfId) } returns null
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `should return error when bookshelf belongs to another user`() {
        // Given: 书架属于另一个用户
        val bookshelf = createBookshelf(userId = 999)  // 不同的用户
        
        every { bookshelfRepository.findById(bookshelfId) } returns bookshelf
        
        // When
        val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, 20, 0)
        
        // Then
        assertTrue(result.isFailure)
    }
    
    // ========== Helper Methods ==========
    
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
            coverPath = null,
            isbn = null,
            publisher = null,
            description = null,
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
