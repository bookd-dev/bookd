package com.bookd.domain.service.metadata

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO

/**
 * PDF 元数据提取器
 * 使用 PDFBox 直接读取 PDF 文档信息
 */
class PdfMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(PdfMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        return try {
            Loader.loadPDF(file).use { document ->
                val info = document.documentInformation
                
                if (info == null) {
                    logger.debug("No document information found in PDF: ${file.name}")
                    return null
                }
                
                BookMetadata(
                    title = info.title?.trim()?.takeIf { it.isNotBlank() },
                    author = info.author?.trim()?.takeIf { it.isNotBlank() },
                    publisher = info.producer?.trim()?.takeIf { it.isNotBlank() },
                    description = info.subject?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to extract PDF metadata from ${file.name}: ${e.message}")
            null
        }
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        return try {
            Loader.loadPDF(file).use { document ->
                if (document.numberOfPages == 0) {
                    logger.debug("PDF has no pages: ${file.name}")
                    return null
                }
                
                // 渲染第一页作为封面
                val renderer = PDFRenderer(document)
                
                // 渲染为 150 DPI 的图片 (适合封面显示)
                val image = renderer.renderImageWithDPI(0, 150f, ImageType.RGB)
                
                // 保存封面
                val coversDir = File("covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }
                
                val outputFile = File(coversDir, "book_${bookId}.png")
                ImageIO.write(image, "PNG", outputFile)
                
                logger.info("Extracted PDF cover for book $bookId")
                return "/covers/book_${bookId}.png"
            }
        } catch (e: Exception) {
            logger.error("Failed to extract PDF cover from ${file.name}: ${e.message}")
            null
        }
    }
}
