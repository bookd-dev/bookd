package com.bookd.domain.service

import com.bookd.domain.model.Book
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.ReadingProgressResponse
import com.bookd.domain.model.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 书籍详情服务单元测试
 * 
 * 测试重点：
 * 1. 成功聚合所有服务的数据
 * 2. 处理书籍不存在的情况
 * 3. 处理各个服务失败的场景（TagService, ReadingService, BookshelfService）
 * 4. 处理多个服务同时失败的场景
 * 5. 验证错误不会导致整体响应失败
 */
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
    fun `should aggregate all data successfully when all services work`() {
        // Given: 所有服务都成功返回数据
        val book = createBook(bookId, "Test Book")
        val tags = listOf(
            createTag(1, "Fiction"),
            createTag(2, "Fantasy")
        )
        val progress = createProgress(bookId)
        val bookshelves = listOf(
            createBookshelf(1, "Reading"),
            createBookshelf(2, "Favorites")
        )
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        every { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns bookshelves
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns true
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(2, result.tags.size)
        assertEquals("Fiction", result.tags[0].name)
        assertEquals("Fantasy", result.tags[1].name)
        assertNotNull(result.readingProgress)
        assertEquals(bookId, result.readingProgress!!.bookId)
        assertEquals(2, result.bookshelves.size)
        assertTrue(result.inDefaultBookshelf)
        
        // 验证所有服务都被调用
        coVerify(exactly = 1) { bookService.getBookById(bookId) }
        verify(exactly = 1) { tagService.getTagsForBook(bookId) }
        verify(exactly = 1) { readingService.getProgress(userId, bookId) }
        verify(exactly = 1) { bookshelfService.getBookshelvesForBook(userId, bookId) }
        verify(exactly = 1) { bookshelfService.isBookInDefaultBookshelf(userId, bookId) }
    }
    
    @Test
    fun `should return null when book does not exist`() {
        // Given: 书籍不存在
        coEvery { bookService.getBookById(bookId) } returns null
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then
        assertNull(result)
        
        // 验证只调用了 bookService，其他服务不应被调用
        coVerify(exactly = 1) { bookService.getBookById(bookId) }
        verify(exactly = 0) { tagService.getTagsForBook(any()) }
        verify(exactly = 0) { readingService.getProgress(any(), any()) }
        verify(exactly = 0) { bookshelfService.getBookshelvesForBook(any(), any()) }
        verify(exactly = 0) { bookshelfService.isBookInDefaultBookshelf(any(), any()) }
    }
    
    @Test
    fun `should handle TagService failure gracefully`() {
        // Given: TagService 抛出异常
        val book = createBook(bookId, "Test Book")
        val progress = createProgress(bookId)
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } throws RuntimeException("Tag service error")
        every { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns emptyList()
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns false
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then: 应该返回结果，但标签为空列表
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertTrue(result.tags.isEmpty())  // 标签为空
        assertNotNull(result.readingProgress)  // 其他数据正常
        assertFalse(result.inDefaultBookshelf)
    }
    
    @Test
    fun `should handle ReadingService failure gracefully`() {
        // Given: ReadingService 抛出异常
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"))
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        every { readingService.getProgress(userId, bookId) } throws RuntimeException("Reading service error")
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns emptyList()
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns false
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then: 应该返回结果，但阅读进度为 null
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(1, result.tags.size)  // 标签正常
        assertNull(result.readingProgress)  // 阅读进度为 null
        assertFalse(result.inDefaultBookshelf)
    }
    
    @Test
    fun `should handle BookshelfService getBookshelvesForBook failure gracefully`() {
        // Given: BookshelfService.getBookshelvesForBook 抛出异常
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"))
        val progress = createProgress(bookId)
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        every { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } throws RuntimeException("Bookshelf service error")
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns true
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then: 应该返回结果，但书架列表为空
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(1, result.tags.size)
        assertNotNull(result.readingProgress)
        assertTrue(result.bookshelves.isEmpty())  // 书架列表为空
        assertTrue(result.inDefaultBookshelf)  // 默认书架状态仍然正常
    }
    
    @Test
    fun `should handle BookshelfService isBookInDefaultBookshelf failure gracefully`() {
        // Given: BookshelfService.isBookInDefaultBookshelf 抛出异常
        val book = createBook(bookId, "Test Book")
        val bookshelves = listOf(createBookshelf(1, "Reading"))
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns emptyList()
        every { readingService.getProgress(userId, bookId) } returns null
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns bookshelves
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } throws RuntimeException("Default bookshelf check error")
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then: 应该返回结果，默认书架状态为 false
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(1, result.bookshelves.size)
        assertFalse(result.inDefaultBookshelf)  // 默认为 false
    }
    
    @Test
    fun `should handle multiple service failures gracefully`() {
        // Given: 多个服务同时失败
        val book = createBook(bookId, "Test Book")
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } throws RuntimeException("Tag service error")
        every { readingService.getProgress(userId, bookId) } throws RuntimeException("Reading service error")
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } throws RuntimeException("Bookshelf service error")
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } throws RuntimeException("Default bookshelf error")
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then: 应该返回基本的书籍信息，所有可选数据为空/false
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertTrue(result.tags.isEmpty())
        assertNull(result.readingProgress)
        assertTrue(result.bookshelves.isEmpty())
        assertFalse(result.inDefaultBookshelf)
    }
    
    @Test
    fun `should handle partial data scenario with no reading progress`() {
        // Given: 书籍存在但没有阅读进度
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"))
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        every { readingService.getProgress(userId, bookId) } returns null  // 没有阅读进度
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns emptyList()
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns false
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(1, result.tags.size)
        assertNull(result.readingProgress)  // 正常返回 null
        assertTrue(result.bookshelves.isEmpty())
        assertFalse(result.inDefaultBookshelf)
    }
    
    @Test
    fun `should handle partial data scenario with no tags`() {
        // Given: 书籍存在但没有标签
        val book = createBook(bookId, "Test Book")
        val progress = createProgress(bookId)
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns emptyList()  // 没有标签
        every { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns emptyList()
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns false
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertTrue(result.tags.isEmpty())  // 正常返回空列表
        assertNotNull(result.readingProgress)
    }
    
    @Test
    fun `should handle partial data scenario with no bookshelves`() {
        // Given: 书籍存在但不在任何书架中
        val book = createBook(bookId, "Test Book")
        val tags = listOf(createTag(1, "Fiction"))
        val progress = createProgress(bookId)
        
        coEvery { bookService.getBookById(bookId) } returns book
        every { tagService.getTagsForBook(bookId) } returns tags
        every { readingService.getProgress(userId, bookId) } returns progress
        every { bookshelfService.getBookshelvesForBook(userId, bookId) } returns emptyList()  // 没有书架
        every { bookshelfService.isBookInDefaultBookshelf(userId, bookId) } returns false
        
        // When
        val result = runBlocking { bookDetailService.getBookDetail(bookId, userId) }
        
        // Then
        assertNotNull(result)
        assertEquals(book, result!!.book)
        assertEquals(1, result.tags.size)
        assertNotNull(result.readingProgress)
        assertTrue(result.bookshelves.isEmpty())  // 正常返回空列表
        assertFalse(result.inDefaultBookshelf)
    }
    
    // ========== Helper Methods ==========
    
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
    
    private fun createTag(id: Int, name: String): Tag {
        val now = LocalDateTime(2026, 1, 21, 0, 0)
        return Tag(
            id = id,
            name = name,
            createdAt = now
        )
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
            sortOrder = 0,
            bookCount = 0,
            isSystemDefault = false,
            createdAt = now,
            updatedAt = now
        )
    }
}
