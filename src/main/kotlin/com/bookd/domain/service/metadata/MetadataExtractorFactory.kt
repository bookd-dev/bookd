package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.EbookFormat
import com.bookd.domain.service.parser.EbookFormatRegistry
import com.bookd.domain.service.parser.mobi.ProcessMobiNormalizer
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
    private val pdfExtractor = PdfMetadataExtractor()
    private val mobiExtractor = MobiMetadataExtractor(ProcessMobiNormalizer())
    
    /**
     * 根据文件格式创建对应的元数据提取器
     * 策略:
     * 1. EPUB: EPUB专用提取器 → Tika备选
     * 2. TXT: 返回null,使用文件名(等待刮削器)
     */
    fun createExtractor(file: File): MetadataExtractor {
        return when (val format = EbookFormatRegistry.detect(file)) {
            EbookFormat.EPUB -> CompositeMetadataExtractor(listOf(epubExtractor, tikaExtractor))
            EbookFormat.TXT -> txtExtractor
            EbookFormat.PDF -> pdfExtractor
            EbookFormat.MOBI -> mobiExtractor
            else -> {
                logger.warn("Unsupported or invalid ebook format: ${file.extension.lowercase()}")
                if (EbookFormatRegistry.fromExtension(file.extension) != null) {
                    NullMetadataExtractor
                } else {
                    tikaExtractor
                }
            }
        }
    }
}

private object NullMetadataExtractor : MetadataExtractor {
    override fun extractMetadata(file: File): BookMetadata? = null
    override fun extractCover(file: File, bookId: Int): String? = null
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
