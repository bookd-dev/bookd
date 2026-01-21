package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookshelfItemRepository
import com.bookd.data.repository.BookshelfRepository
import com.bookd.domain.model.*
import org.slf4j.LoggerFactory

/**
 * 书架服务
 * 
 * 负责书架管理和书架-书籍关联的业务逻辑
 * 
 * 核心规则:
 * 1. 每个用户有一个系统默认的"全部"书架,不可删除、不可重命名
 * 2. 添加书到任意书架时,自动同时添加到"全部"书架
 * 3. 从"全部"书架移除书籍时,级联删除该书在用户所有书架中的关联
 */
class BookshelfService(
    private val bookshelfRepository: BookshelfRepository,
    private val bookshelfItemRepository: BookshelfItemRepository,
    private val bookRepository: BookRepository
) {
    private val logger = LoggerFactory.getLogger("BookshelfService")
    
    companion object {
        const val DEFAULT_BOOKSHELF_NAME = "全部"
    }
    
    /**
     * 初始化用户书架(用户注册时调用)
     * 创建默认的"全部"书架
     */
    fun initializeUserBookshelves(userId: Int): Bookshelf {
        logger.info("初始化用户书架, userId: $userId")
        
        // 检查是否已存在默认书架
        val existing = bookshelfRepository.getDefaultBookshelf(userId)
        if (existing != null) {
            logger.info("用户默认书架已存在, userId: $userId")
            return existing
        }
        
        // 创建默认"全部"书架
        return bookshelfRepository.create(
            userId = userId,
            name = DEFAULT_BOOKSHELF_NAME,
            description = null,
            isSystemDefault = true,
            sortOrder = -1  // 确保排在最前面
        )
    }
    
    /**
     * 获取用户的所有书架
     * 返回结果包含每个书架的书籍数量
     */
    fun getUserBookshelves(userId: Int): List<Bookshelf> {
        val bookshelves = bookshelfRepository.findByUserId(userId)
        
        // 填充每个书架的书籍数量
        return bookshelves.map { bookshelf ->
            val count = bookshelfItemRepository.countByBookshelf(bookshelf.id)
            bookshelf.copy(bookCount = count.toInt())
        }
    }
    
    /**
     * 获取用户的默认书架("全部"书架)
     */
    fun getDefaultBookshelf(userId: Int): Bookshelf? {
        return bookshelfRepository.getDefaultBookshelf(userId)
    }
    
    /**
     * 创建新书架
     */
    fun createBookshelf(userId: Int, request: CreateBookshelfRequest): Result<Bookshelf> {
        // 验证名称不为空
        if (request.name.isBlank()) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EMPTY))
        }
        
        // 验证名称不与系统书架冲突
        if (request.name == DEFAULT_BOOKSHELF_NAME) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EXISTS))
        }
        
        // 验证名称唯一性
        val existing = bookshelfRepository.findByUserIdAndName(userId, request.name)
        if (existing != null) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EXISTS))
        }
        
        // 获取最大排序值
        val maxSortOrder = bookshelfRepository.getMaxSortOrder(userId)
        
        // 创建书架
        val bookshelf = bookshelfRepository.create(
            userId = userId,
            name = request.name,
            description = request.description,
            isSystemDefault = false,
            sortOrder = maxSortOrder + 1
        )
        
        logger.info("创建书架成功, userId: $userId, bookshelfId: ${bookshelf.id}, name: ${bookshelf.name}")
        return Result.success(bookshelf)
    }
    
    /**
     * 更新书架信息
     */
    fun updateBookshelf(userId: Int, bookshelfId: Int, request: UpdateBookshelfRequest): Result<Bookshelf> {
        // 获取书架
        val bookshelf = bookshelfRepository.findById(bookshelfId)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        // 验证所有权
        if (bookshelf.userId != userId) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        }
        
        // 不能修改系统书架
        if (bookshelf.isSystemDefault) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_CANNOT_MODIFY_SYSTEM))
        }
        
        // 验证新名称
        if (request.name != null) {
            if (request.name.isBlank()) {
                return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EMPTY))
            }
            if (request.name == DEFAULT_BOOKSHELF_NAME) {
                return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EXISTS))
            }
            // 验证名称唯一性(排除自己)
            val existing = bookshelfRepository.findByUserIdAndName(userId, request.name)
            if (existing != null && existing.id != bookshelfId) {
                return Result.failure(BookshelfException(ErrorCode.SHELF_NAME_EXISTS))
            }
        }
        
        // 更新书架
        val updated = bookshelfRepository.update(bookshelfId, request.name, request.description)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        logger.info("更新书架成功, userId: $userId, bookshelfId: $bookshelfId")
        return Result.success(updated)
    }
    
    /**
     * 删除书架
     * 不能删除系统默认书架
     */
    fun deleteBookshelf(userId: Int, bookshelfId: Int): Result<Unit> {
        // 获取书架
        val bookshelf = bookshelfRepository.findById(bookshelfId)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        // 验证所有权
        if (bookshelf.userId != userId) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        }
        
        // 不能删除系统书架
        if (bookshelf.isSystemDefault) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_CANNOT_DELETE_SYSTEM))
        }
        
        // 删除书架(BookshelfItems 会级联删除)
        bookshelfRepository.delete(bookshelfId)
        
        logger.info("删除书架成功, userId: $userId, bookshelfId: $bookshelfId")
        return Result.success(Unit)
    }
    
    /**
     * 调整书架顺序
     */
    fun reorderBookshelves(userId: Int, request: ReorderBookshelvesRequest): Result<Unit> {
        // 验证所有书架都属于该用户
        val userBookshelves = bookshelfRepository.findByUserId(userId)
        val userBookshelfIds = userBookshelves.filter { !it.isSystemDefault }.map { it.id }.toSet()
        
        for (id in request.bookshelfIds) {
            if (id !in userBookshelfIds) {
                return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
            }
        }
        
        // 更新排序
        bookshelfRepository.reorderBookshelves(userId, request.bookshelfIds)
        
        logger.info("调整书架顺序成功, userId: $userId, bookshelfIds: ${request.bookshelfIds}")
        return Result.success(Unit)
    }
    
    /**
     * 获取书架中的书籍列表
     */
    fun getBooksInBookshelf(userId: Int, bookshelfId: Int, limit: Int = 20, offset: Long = 0): Result<BooksInBookshelfResponse> {
        // 验证书架存在且属于该用户
        val bookshelf = bookshelfRepository.findById(bookshelfId)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        if (bookshelf.userId != userId) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        }
        
        // 获取书籍列表
        val books = bookshelfItemRepository.findByBookshelf(bookshelfId, limit, offset)
        val total = bookshelfItemRepository.countByBookshelf(bookshelfId)
        
        return Result.success(BooksInBookshelfResponse(
            books = books,
            total = total.toInt(),
            limit = limit,
            offset = offset,
            hasMore = offset + books.size < total
        ))
    }
    
    /**
     * 添加书籍到书架
     * 如果是非系统书架,同时添加到"全部"书架
     */
    fun addBookToBookshelf(userId: Int, bookshelfId: Int, bookId: Int): Result<Unit> {
        // 验证书架存在且属于该用户
        val bookshelf = bookshelfRepository.findById(bookshelfId)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        if (bookshelf.userId != userId) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        }
        
        // 验证书籍存在
        val book = bookRepository.findById(bookId)
            ?: return Result.failure(BookshelfException(ErrorCode.BOOK_NOT_FOUND))
        
        // 添加到目标书架
        bookshelfItemRepository.addBook(bookshelfId, bookId)
        
        // 如果不是系统默认书架,同时添加到"全部"书架
        if (!bookshelf.isSystemDefault) {
            val defaultBookshelf = bookshelfRepository.getDefaultBookshelf(userId)
            if (defaultBookshelf != null) {
                // 尝试添加到"全部",如果已存在会返回 null,不影响操作
                bookshelfItemRepository.addBook(defaultBookshelf.id, bookId)
            }
        }
        
        logger.info("添加书籍到书架成功, userId: $userId, bookshelfId: $bookshelfId, bookId: $bookId")
        return Result.success(Unit)
    }
    
    /**
     * 从书架移除书籍
     * 如果从"全部"书架移除,级联删除所有书架中的该书
     */
    fun removeBookFromBookshelf(userId: Int, bookshelfId: Int, bookId: Int): Result<Unit> {
        // 验证书架存在且属于该用户
        val bookshelf = bookshelfRepository.findById(bookshelfId)
            ?: return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        
        if (bookshelf.userId != userId) {
            return Result.failure(BookshelfException(ErrorCode.SHELF_NOT_FOUND))
        }
        
        // 如果是系统默认书架("全部"),从所有书架中移除
        if (bookshelf.isSystemDefault) {
            val deleted = bookshelfItemRepository.removeBookFromAllUserBookshelves(userId, bookId)
            logger.info("从所有书架移除书籍成功(级联删除), userId: $userId, bookId: $bookId, deletedCount: $deleted")
        } else {
            // 只从当前书架移除
            val deleted = bookshelfItemRepository.removeBook(bookshelfId, bookId)
            if (deleted == 0) {
                return Result.failure(BookshelfException(ErrorCode.SHELF_BOOK_NOT_FOUND))
            }
            logger.info("从书架移除书籍成功, userId: $userId, bookshelfId: $bookshelfId, bookId: $bookId")
        }
        
        return Result.success(Unit)
    }
    
    /**
     * 批量添加书籍到多个书架
     */
    fun addBookToBookshelves(userId: Int, bookId: Int, bookshelfIds: List<Int>): Result<Unit> {
        // 验证书籍存在
        val book = bookRepository.findById(bookId)
            ?: return Result.failure(BookshelfException(ErrorCode.BOOK_NOT_FOUND))
        
        // 逐个添加到书架
        for (bookshelfId in bookshelfIds) {
            val result = addBookToBookshelf(userId, bookshelfId, bookId)
            if (result.isFailure) {
                // 如果某个书架添加失败(如不存在),继续处理其他书架
                logger.warn("批量添加书籍失败, userId: $userId, bookshelfId: $bookshelfId, bookId: $bookId")
            }
        }
        
        return Result.success(Unit)
    }
    
    /**
     * 获取书籍所在的所有书架
     */
    fun getBookshelvesForBook(userId: Int, bookId: Int): List<Bookshelf> {
        val bookshelves = bookshelfItemRepository.findUserBookshelvesForBook(userId, bookId)
        
        // 填充书籍数量
        return bookshelves.map { bookshelf ->
            val count = bookshelfItemRepository.countByBookshelf(bookshelf.id)
            bookshelf.copy(bookCount = count.toInt())
        }
    }
    
    /**
     * 检查书籍是否在用户的默认书架("全部")中
     */
    fun isBookInDefaultBookshelf(userId: Int, bookId: Int): Boolean {
        val defaultBookshelf = bookshelfRepository.getDefaultBookshelf(userId)
            ?: return false
        return bookshelfItemRepository.isBookInBookshelf(defaultBookshelf.id, bookId)
    }
}

/**
 * 书架相关异常
 */
class BookshelfException(val errorCode: ErrorCode) : Exception(errorCode.zhCN)

/**
 * 书架中书籍列表响应
 */
@kotlinx.serialization.Serializable
data class BooksInBookshelfResponse(
    val books: List<Book>,
    val total: Int,
    val limit: Int,
    val offset: Long,
    val hasMore: Boolean
)
