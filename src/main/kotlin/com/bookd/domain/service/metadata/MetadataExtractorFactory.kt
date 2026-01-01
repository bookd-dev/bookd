package com.bookd.domain.service.metadata

import org.slf4j.LoggerFactory
import java.io.File

/**
 * 元数据提取器工厂
 */
class MetadataExtractorFactory {
    
    private val logger = LoggerFactory.getLogger(MetadataExtractorFactory::class.java)
    
    // 缓存提取器实例
    private val tikaExtractor = TikaMetadataExtractor()
    private val epubExtractor = EpubMetadataExtractor()
    private val txtExtractor = TxtMetadataExtractor()
    
    /**
     * 根据文件格式创建对应的元数据提取器
     * 策略:
     * 1. EPUB/PDF/MOBI/AZW3: 先尝试格式特定提取器,失败则使用 Tika
     * 2. TXT: 使用 TXT 提取器(当前返回 null,等待刮削器)
     */
    fun createExtractor(file: File): MetadataExtractor {
        val format = file.extension.lowercase()
        
        return when (format) {
            "epub" -> CompositeMetadataExtractor(listOf(epubExtractor, tikaExtractor))
            "pdf", "mobi", "azw3", "azw" -> tikaExtractor
            "txt" -> txtExtractor
            else -> {
                logger.warn("Unsupported format: $format, using Tika as fallback")
                tikaExtractor
            }
        }
    }
}

/**
 * 组合元数据提取器
 * 依次尝试多个提取器,直到成功提取到元数据
 */
private class CompositeMetadataExtractor(
    private val extractors: List<MetadataExtractor>
) : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(CompositeMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        for (extractor in extractors) {
            val metadata = extractor.extractMetadata(file)
            if (metadata != null && (metadata.title != null || metadata.author != null)) {
                logger.debug("Successfully extracted metadata using ${extractor.javaClass.simpleName}")
                return metadata
            }
        }
        return null
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        for (extractor in extractors) {
            val cover = extractor.extractCover(file, bookId)
            if (cover != null) {
                logger.debug("Successfully extracted cover using ${extractor.javaClass.simpleName}")
                return cover
            }
        }
        return null
    }
}
