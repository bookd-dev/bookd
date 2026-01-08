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
    val lastReadAt: String? = null
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
    val lastReadAt: LocalDateTime
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
