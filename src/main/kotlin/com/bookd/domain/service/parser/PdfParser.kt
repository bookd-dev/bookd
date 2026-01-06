package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class PdfParser {
    
    private val logger = LoggerFactory.getLogger(PdfParser::class.java)
    
    data class PdfStructure(
        val chapters: List<ChapterInfo>,
        val pageImages: Map<Int, ByteArray> = emptyMap()
    )
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val startPage: Int,
        val endPage: Int,
        val level: Int = 0
    )
    
    /**
     * 解析 PDF 文件结构
     */
    fun parseStructure(file: File, extractImages: Boolean = false): PdfStructure? {
        return try {
            Loader.loadPDF(file).use { document ->
                val chapters = extractChapters(document)
                logger.info("Extracted ${chapters.size} chapters from PDF")
                
                val pageImages = if (extractImages && chapters.size <= 10) {
                    // 只为小章节数的 PDF 提取首页缩略图
                    extractThumbnails(document, chapters)
                } else {
                    emptyMap()
                }
                
                PdfStructure(chapters, pageImages)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse PDF structure: ${file.name}", e)
            null
        }
    }
    
    /**
     * 提取章节信息（基于书签/大纲）
     */
    private fun extractChapters(document: PDDocument): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        // 尝试从 PDF 书签中提取章节
        val outline = document.documentCatalog.documentOutline
        if (outline != null) {
            var chapterIndex = 0
            
            try {
                extractOutlineItems(document, outline.firstChild, chapters, chapterIndex, 0)
            } catch (e: Exception) {
                logger.warn("Failed to extract outline items", e)
            }
            
            // 更新每章的结束页
            if (chapters.isNotEmpty()) {
                for (i in 0 until chapters.size - 1) {
                    chapters[i] = chapters[i].copy(endPage = chapters[i + 1].startPage - 1)
                }
                // 最后一章到文档结束
                val lastChapter = chapters.last()
                chapters[chapters.size - 1] = lastChapter.copy(endPage = document.numberOfPages - 1)
            }
        }
        
        // 如果没有书签，使用基于页码的分割策略
        if (chapters.isEmpty()) {
            logger.info("No bookmarks found, using page-based chapter division")
            chapters.addAll(createPageBasedChapters(document))
        }
        
        return chapters
    }
    
    /**
     * 递归提取大纲项目
     */
    private fun extractOutlineItems(
        document: PDDocument,
        item: PDOutlineItem?,
        chapters: MutableList<ChapterInfo>,
        startIndex: Int,
        level: Int
    ): Int {
        var currentIndex = startIndex
        var currentItem = item
        
        while (currentItem != null) {
            val title = currentItem.title ?: "Chapter ${currentIndex + 1}"
            
            // 尝试获取页码（简化方式）
            var pageNumber = 0
            try {
                // 从 destination 字符串中尝试提取页码
                val dest = currentItem.destination
                if (dest != null) {
                    // 大多数 PDF 目标都有页码引用
                    // 这里使用简单的估算：根据在大纲中的顺序
                    pageNumber = currentIndex * (document.numberOfPages / maxOf(1, startIndex + 10))
                }
            } catch (e: Exception) {
                logger.debug("Could not extract page number for: $title", e)
            }
            
            chapters.add(
                ChapterInfo(
                    index = currentIndex,
                    title = title,
                    startPage = pageNumber,
                    endPage = pageNumber,
                    level = level
                )
            )
            currentIndex++
            
            currentItem = currentItem.nextSibling
        }
        
        return currentIndex
    }
    
    /**
     * 基于页码创建章节（每 10-20 页一章）
     */
    private fun createPageBasedChapters(document: PDDocument): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        val totalPages = document.numberOfPages
        val pagesPerChapter = when {
            totalPages <= 50 -> 10
            totalPages <= 200 -> 20
            else -> 30
        }
        
        var chapterIndex = 0
        var startPage = 0
        
        while (startPage < totalPages) {
            val endPage = minOf(startPage + pagesPerChapter - 1, totalPages - 1)
            chapters.add(
                ChapterInfo(
                    index = chapterIndex++,
                    title = "第 ${chapterIndex} 部分 (页 ${startPage + 1}-${endPage + 1})",
                    startPage = startPage,
                    endPage = endPage,
                    level = 0
                )
            )
            startPage = endPage + 1
        }
        
        return chapters
    }
    
    /**
     * 提取章节内容
     */
    fun extractChapterContent(file: File, chapterInfo: ChapterInfo): List<ContentElement>? {
        return try {
            Loader.loadPDF(file).use { document ->
                val stripper = PDFTextStripper()
                stripper.startPage = chapterInfo.startPage + 1 // PDFBox 页码从 1 开始
                stripper.endPage = chapterInfo.endPage + 1
                
                val text = stripper.getText(document)
                
                // 将文本解析为段落
                parseTextToElements(text, chapterInfo.title)
            }
        } catch (e: Exception) {
            logger.error("Failed to extract PDF chapter content", e)
            null
        }
    }
    
    /**
     * 将 PDF 文本解析为结构化元素
     */
    private fun parseTextToElements(text: String, chapterTitle: String?): List<ContentElement> {
        val elements = mutableListOf<ContentElement>()
        
        // 添加章节标题
        if (!chapterTitle.isNullOrBlank()) {
            elements.add(ContentElement.Heading(1, chapterTitle))
        }
        
        // 按空行分割段落
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        paragraphs.forEach { paragraph ->
            // 检测是否为标题（简单启发式：短且首字母大写或全大写）
            if (paragraph.length < 100 && (paragraph.all { it.isUpperCase() || it.isWhitespace() } || 
                paragraph.matches(Regex("^(第[零一二三四五六七八九十百千万0-9]+[章节]|Chapter\\s+\\d+|\\d+\\.).*")))) {
                elements.add(ContentElement.Heading(2, paragraph))
            } else {
                // 普通段落，按换行符分割成多个 TextSpan
                val lines = paragraph.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                if (lines.isNotEmpty()) {
                    val spans = lines.map { TextSpan(it) }
                    elements.add(ContentElement.Paragraph(spans))
                }
            }
        }
        
        return elements
    }
    
    /**
     * 提取页面缩略图（可选）
     */
    private fun extractThumbnails(document: PDDocument, chapters: List<ChapterInfo>): Map<Int, ByteArray> {
        val thumbnails = mutableMapOf<Int, ByteArray>()
        
        try {
            val renderer = PDFRenderer(document)
            
            chapters.forEach { chapter ->
                try {
                    // 渲染章节首页为图片
                    val image: BufferedImage = renderer.renderImageWithDPI(chapter.startPage, 96f)
                    
                    // 转换为 JPEG 字节数组
                    val outputStream = java.io.ByteArrayOutputStream()
                    ImageIO.write(image, "JPEG", outputStream)
                    thumbnails[chapter.index] = outputStream.toByteArray()
                    
                    logger.debug("Extracted thumbnail for chapter ${chapter.index}")
                } catch (e: Exception) {
                    logger.warn("Failed to extract thumbnail for chapter ${chapter.index}", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract thumbnails", e)
        }
        
        return thumbnails
    }
    
    /**
     * 提取所有图片（从 PDF XObject 中提取）
     */
    fun extractImages(file: File): Map<String, ByteArray> {
        val images = mutableMapOf<String, ByteArray>()
        
        try {
            Loader.loadPDF(file).use { document ->
                var imageIndex = 0
                
                // 遍历每一页
                for (page in document.pages) {
                    try {
                        val resources = page.resources
                        
                        // 获取所有 XObject 资源
                        val xObjectNames = resources.xObjectNames
                        
                        for (objectName in xObjectNames) {
                            try {
                                val xObject = resources.getXObject(objectName)
                                
                                // 检查是否为图像 XObject
                                if (xObject is org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    val imageXObject = xObject as org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
                                    
                                    // 获取图片后缀
                                    val suffix = imageXObject.suffix
                                    val format = if (suffix.isNotEmpty()) suffix else "png"
                                    
                                    // 提取图片
                                    val bufferedImage = imageXObject.image
                                    
                                    // 转换为字节数组
                                    val outputStream = java.io.ByteArrayOutputStream()
                                    ImageIO.write(bufferedImage, format, outputStream)
                                    
                                    val imageKey = "image_${imageIndex}.${format}"
                                    images[imageKey] = outputStream.toByteArray()
                                    
                                    imageIndex++
                                    logger.debug("Extracted image: $imageKey (${bufferedImage.width}x${bufferedImage.height})")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to extract XObject: $objectName", e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to process page resources", e)
                    }
                }
                
                logger.info("Extracted $imageIndex images from PDF")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract images from PDF", e)
        }
        
        return images
    }
    
    /**
     * 提取指定页面范围的图片
     */
    fun extractImagesFromPages(file: File, startPage: Int, endPage: Int): Map<String, ByteArray> {
        val images = mutableMapOf<String, ByteArray>()
        
        try {
            Loader.loadPDF(file).use { document ->
                var imageIndex = 0
                val totalPages = document.numberOfPages
                
                val actualStartPage = maxOf(0, startPage)
                val actualEndPage = minOf(totalPages - 1, endPage)
                
                // 遍历指定页面范围
                for (pageNum in actualStartPage..actualEndPage) {
                    try {
                        val page = document.getPage(pageNum)
                        val resources = page.resources
                        val xObjectNames = resources.xObjectNames
                        
                        for (objectName in xObjectNames) {
                            try {
                                val xObject = resources.getXObject(objectName)
                                
                                if (xObject is org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    val imageXObject = xObject as org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
                                    val suffix = imageXObject.suffix
                                    val format = if (suffix.isNotEmpty()) suffix else "png"
                                    val bufferedImage = imageXObject.image
                                    
                                    val outputStream = java.io.ByteArrayOutputStream()
                                    ImageIO.write(bufferedImage, format, outputStream)
                                    
                                    val imageKey = "page${pageNum}_image${imageIndex}.${format}"
                                    images[imageKey] = outputStream.toByteArray()
                                    
                                    imageIndex++
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to extract image from page $pageNum", e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to process page $pageNum", e)
                    }
                }
                
                logger.info("Extracted $imageIndex images from pages $actualStartPage-$actualEndPage")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract images from PDF pages", e)
        }
        
        return images
    }
    
    /**
     * 计算文本字数
     */
    fun countWords(text: String): Int {
        // 中文字符数
        val chineseCount = text.count { it.code in 0x4E00..0x9FFF }
        
        // 英文单词数
        val englishWords = text.split(Regex("\\s+"))
            .count { it.matches(Regex("[a-zA-Z]+")) }
        
        return chineseCount + englishWords
    }
}
