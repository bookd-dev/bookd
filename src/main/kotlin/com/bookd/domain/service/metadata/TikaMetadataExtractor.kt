package com.bookd.domain.service.metadata

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * 基于 Tika 的通用元数据提取器
 */
class TikaMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(TikaMetadataExtractor::class.java)
    private val parser = AutoDetectParser()
    
    override fun extractMetadata(file: File): BookMetadata? {
        return try {
            val metadata = Metadata()
            val handler = BodyContentHandler(10 * 1024 * 1024) // 10MB limit
            
            FileInputStream(file).use { stream ->
                parser.parse(stream, handler, metadata)
            }
            
            // Log available metadata keys
            val keys = metadata.names()
            if (keys.isNotEmpty()) {
                logger.debug("Tika found ${keys.size} metadata keys for ${file.name}")
            } else {
                logger.warn("Tika found no metadata in ${file.name}")
                return null
            }
            
            // 提取标签/关键词
            val tags = mutableListOf<String>()
            val keywordsFields = listOf("keywords", "meta:keyword", "Keywords", "subject", "dc:subject")
            for (field in keywordsFields) {
                val value = metadata.get(field)
                if (!value.isNullOrBlank()) {
                    value.split(',', ';', '/', '|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && it.length <= 50 }
                        .forEach { tags.add(it) }
                }
            }
            
            val distinctTags = tags.distinct()
            if (distinctTags.isNotEmpty()) {
                logger.debug("Tika extracted ${distinctTags.size} tags: ${distinctTags.joinToString(", ")}")
            }
            
            BookMetadata(
                title = extractField(metadata, "title", "dc:title", "Title"),
                author = extractField(metadata, "author", "creator", "dc:creator", "meta:author", "Author"),
                publisher = extractField(metadata, "publisher", "dc:publisher", "Publisher"),
                description = extractField(metadata, "description", "dc:description", "Description"),
                isbn = extractField(metadata, "isbn", "ISBN"),
                tags = distinctTags
            )
        } catch (e: Exception) {
            logger.error("Tika failed to extract metadata from ${file.name}: ${e.message}")
            null
        }
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        // Tika 不支持封面提取
        return null
    }
    
    private fun extractField(metadata: Metadata, vararg keys: String): String? {
        for (key in keys) {
            val value = metadata.get(key)?.trim()?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }
}
