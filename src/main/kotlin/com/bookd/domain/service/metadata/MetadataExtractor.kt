package com.bookd.domain.service.metadata

import java.io.File

/**
 * 元数据提取器接口
 */
interface MetadataExtractor {
    /**
     * 提取元数据
     */
    suspend fun extractMetadata(file: File): BookMetadata?
    
    /**
     * 提取封面
     */
    suspend fun extractCover(file: File, bookId: Int): String?
}

/**
 * 书籍元数据
 */
data class BookMetadata(
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val tags: List<String> = emptyList()
)
