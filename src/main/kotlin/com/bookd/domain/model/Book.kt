package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val fileSize: Long,
    val coverPath: String? = null,
    val coverWidth: Int? = null,         // 封面宽度
    val coverHeight: Int? = null,        // 封面高度
    val coverAspectRatio: Double? = null, // 封面宽高比（由 Service 层计算）
    val isbn: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val sourceId: Int? = null,
    val chapterCount: Int = 0,
    val totalWordCount: Int = 0,
    val totalImageCount: Int = 0,
    
    // 章节解析状态
    val chaptersParsed: Boolean = false,
    val chaptersCount: Int = 0,
    val lastParsedAt: LocalDateTime? = null,
    val parseStatus: String? = null,
    val parseProgress: Int = 0,
    
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,

    @Transient
    val statsUpdatedAt: LocalDateTime? = null
)
