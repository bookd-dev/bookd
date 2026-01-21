package com.bookd.domain.model

import kotlinx.serialization.Serializable

/**
 * 书籍详情响应
 * 
 * 包含书籍完整信息、标签、阅读进度和所在书架信息
 */
@Serializable
data class BookDetailResponse(
    val book: Book,
    val tags: List<Tag> = emptyList(),
    val readingProgress: ReadingProgressResponse? = null,
    val bookshelves: List<Bookshelf> = emptyList(),
    val inDefaultBookshelf: Boolean = false
)
