package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val fileSize: Long,
    val coverPath: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val sourceId: Int? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
