package com.bookd.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val coverPath: String? = null,
    val fileSize: Long,
    val isbn: String? = null,
    val publisher: String? = null,
    val description: String? = null
)
