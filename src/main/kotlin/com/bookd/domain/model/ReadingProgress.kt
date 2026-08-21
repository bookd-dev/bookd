package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgressDTO(
    val id: Int? = null,
    val bookId: Int,
    val progress: Double,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val cfiLocation: String? = null,
    val documentId: String? = null,
    val deviceId: String? = null,
    val lastReadAt: String? = null,
    val chapterIndex: Int? = null,
    val anchorId: String? = null,
    val paragraphIndex: Int? = null,
    val scrollOffset: Int? = null,
    // 章节详细进度（用于计算章节阅读百分比）
    val chapterPageIndex: Int? = null,      // 翻页模式：当前页索引
    val chapterTotalPages: Int? = null,     // 翻页模式：章节总页数
    val chapterScrollPercent: Double? = null // 滚动模式：滚动百分比 0.0-1.0
)

@Serializable
data class ReadingProgressResponse(
    val id: Int,
    val bookId: Int,
    val progress: Double,
    val currentPage: Int,
    val totalPages: Int?,
    val cfiLocation: String?,
    val documentId: String?,
    val deviceId: String?,
    val lastReadAt: LocalDateTime,
    val chapterIndex: Int = currentPage,
    val anchorId: String? = null,
    val paragraphIndex: Int? = null,
    val scrollOffset: Int? = null,
    // 章节详细进度
    val chapterPageIndex: Int?,
    val chapterTotalPages: Int?,
    val chapterScrollPercent: Double?
)

@Serializable
data class ReadingHistoryItemDTO(
    val bookId: Int,
    val bookTitle: String,
    val bookAuthor: String?,
    val bookCoverPath: String?,
    val bookFormat: String,
    val progress: Double,
    val currentPage: Int,
    val totalPages: Int?,
    val lastReadAt: String
)
