package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * 书架模型
 */
@Serializable
data class Bookshelf(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val bookCount: Int = 0,
    val isSystemDefault: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * 创建书架请求
 */
@Serializable
data class CreateBookshelfRequest(
    val name: String,
    val description: String? = null
)

/**
 * 更新书架请求
 */
@Serializable
data class UpdateBookshelfRequest(
    val name: String? = null,
    val description: String? = null
)

/**
 * 调整书架顺序请求
 */
@Serializable
data class ReorderBookshelvesRequest(
    val bookshelfIds: List<Int>
)

/**
 * 添加书籍到书架请求
 */
@Serializable
data class AddBookToBookshelfRequest(
    val bookId: Int
)

/**
 * 批量添加书籍到多个书架请求（旧版，保留兼容）
 * @deprecated Use [BatchAddToBookshelvesRequest] instead
 */
@Deprecated(
    message = "Use BatchAddToBookshelvesRequest instead",
    replaceWith = ReplaceWith("BatchAddToBookshelvesRequest(bookshelfIds)")
)
@Serializable
data class AddToBookshelvesRequest(
    val bookshelfIds: List<Int>
)

/**
 * 批量添加书籍到多个书架请求
 */
@Serializable
data class BatchAddToBookshelvesRequest(
    val bookshelfIds: List<Int>
)

/**
 * 批量从多个书架移除书籍请求
 */
@Serializable
data class BatchRemoveFromBookshelvesRequest(
    val bookshelfIds: List<Int>
)

/**
 * 书架-书籍关联项
 */
@Serializable
data class BookshelfItem(
    val id: Int,
    val bookshelfId: Int,
    val bookId: Int,
    val addedAt: LocalDateTime
)

/**
 * 书籍及其阅读进度
 * 用于书架书籍列表展示
 */
@Serializable
data class BookWithProgress(
    val book: Book,
    val progress: ReadingProgressResponse?  // null = 未开始阅读
)

/**
 * 书架中书籍列表响应
 * 书籍按最新阅读时间排序，未读书籍排在最后
 */
@Serializable
data class BooksInBookshelfResponse(
    val books: List<BookWithProgress>,
    val total: Int,
    val limit: Int,
    val offset: Long,
    val hasMore: Boolean
)

data class BookshelfMembershipSummary(
    val bookshelves: List<Bookshelf>,
    val inDefaultBookshelf: Boolean
)
