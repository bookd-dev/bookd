package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class BookmarkDTO(
    val id: Int? = null,
    val bookId: Int,
    val positionType: String,
    val positionValue: String,
    val chapterId: String? = null,
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
    val chapterId: String?,
    val title: String?,
    val note: String?,
    val color: String,
    val createdAt: LocalDateTime
)
