package com.bookd.domain.service

import com.bookd.domain.model.BookDetailResponse
import org.slf4j.LoggerFactory

/**
 * 书籍详情服务
 * 
 * 聚合书籍的完整详情信息，包括标签、阅读进度、书架信息等
 */
class BookDetailService(
    private val bookService: BookService,
    private val tagService: TagService,
    private val readingService: ReadingService,
    private val bookshelfService: BookshelfService
) {
    private val logger = LoggerFactory.getLogger("BookDetailService")
    
    /**
     * 获取书籍详情
     * 
     * @param bookId 书籍ID
     * @param userId 用户ID（用于获取阅读进度和书架信息）
     * @return 书籍详情，如果书籍不存在则返回 null
     */
    fun getBookDetail(bookId: Int, userId: Int): BookDetailResponse? {
        // 获取书籍基本信息
        val book = bookService.getBookById(bookId) ?: return null
        
        // 获取书籍标签
        val tags = try {
            tagService.getTagsForBook(bookId)
        } catch (e: Exception) {
            logger.warn("获取书籍标签失败: bookId=$bookId, error=${e.message}")
            emptyList()
        }
        
        // 获取阅读进度
        val readingProgress = try {
            readingService.getProgress(userId, bookId)
        } catch (e: Exception) {
            logger.warn("获取阅读进度失败: bookId=$bookId, userId=$userId, error=${e.message}")
            null
        }
        
        // 获取书籍所在的书架
        val bookshelves = try {
            bookshelfService.getBookshelvesForBook(userId, bookId)
        } catch (e: Exception) {
            logger.warn("获取书籍书架信息失败: bookId=$bookId, userId=$userId, error=${e.message}")
            emptyList()
        }
        
        // 检查是否在默认书架（"全部"）中
        val inDefaultBookshelf = try {
            bookshelfService.isBookInDefaultBookshelf(userId, bookId)
        } catch (e: Exception) {
            logger.warn("检查默认书架状态失败: bookId=$bookId, userId=$userId, error=${e.message}")
            false
        }
        
        return BookDetailResponse(
            book = book,
            tags = tags,
            readingProgress = readingProgress,
            bookshelves = bookshelves,
            inDefaultBookshelf = inDefaultBookshelf
        )
    }
}
