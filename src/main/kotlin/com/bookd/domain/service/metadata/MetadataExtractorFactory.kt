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
    private val pdfExtractor = PdfMetadataExtractor()
    private val mobiExtractor = MobiMetadataExtractor()
    private val txtExtractor = TxtMetadataExtractor()
    private val htmlExtractor = HtmlMetadataExtractor()
    
    /**
     * 根据文件格式创建对应的元数据提取器
     * 策略:
     * 1. EPUB: EPUB专用提取器 → Tika备选
     * 2. PDF: PDF专用提取器 → Tika备选
     * 3. MOBI/AZW3: MOBI专用提取器 → Tika备选
     * 4. HTML: HTML专用提取器
     * 5. TXT: 返回null,使用文件名(等待刮削器)
     */
    fun createExtractor(file: File): MetadataExtractor {
        return when (val format = file.extension.lowercase()) {
            "epub" -> CompositeMetadataExtractor(listOf(epubExtractor, tikaExtractor))
            "pdf" -> CompositeMetadataExtractor(listOf(pdfExtractor, tikaExtractor))
            "mobi", "azw3", "azw" -> CompositeMetadataExtractor(listOf(mobiExtractor, tikaExtractor))
            "html", "htm" -> htmlExtractor
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
