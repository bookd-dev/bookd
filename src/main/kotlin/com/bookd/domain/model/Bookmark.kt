package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class BookmarkDTO(
    val id: Int? = null,
    val bookId: Int? = null,
    val positionType: String? = null,
    val positionValue: String? = null,
    val documentId: String? = null,
    val chapterIndex: Int? = null,
    val anchorId: String? = null,
    val paragraphIndex: Int? = null,
    val scrollOffset: Int? = null,
    val title: String? = null,
    val note: String? = null,
    val color: String = "#FFD700"
)

@Serializable
data class BookmarkResponse(
    val id: Int,
    val bookId: Int,
    val positionType: String,
    val positionValue: String,
    val documentId: String?,
    val chapterIndex: Int? = null,
    val anchorId: String? = null,
    val paragraphIndex: Int? = null,
    val scrollOffset: Int? = null,
    val title: String?,
    val note: String?,
    val color: String,
    val createdAt: LocalDateTime
)
