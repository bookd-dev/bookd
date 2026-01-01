package com.bookd.domain.service.metadata

import org.slf4j.LoggerFactory
import java.io.File

/**
 * TXT 元数据提取器
 * TXT 文件通常没有元数据,保留用于后续刮削器扩展
 */
class TxtMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(TxtMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        logger.debug("TXT files don't have embedded metadata, skipping for ${file.name}")
        return null
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        // TXT 文件没有封面
        return null
    }
}
