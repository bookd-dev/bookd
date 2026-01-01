package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.ToXMLContentHandler
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class MobiParser {
    
    private val logger = LoggerFactory.getLogger(MobiParser::class.java)
    private val parser = AutoDetectParser()
    
    data class MobiStructure(
        val chapters: List<ChapterInfo>,
        val htmlContent: String
    )
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val startPos: Int,
        val endPos: Int,
        val level: Int = 0
    )
    
    /**
     * 解析 MOBI/AZW3 文件结构
     */
    fun parseStructure(file: File): MobiStructure? {
        return try {
            // 方案1: 直接从MOBI记录提取HTML (更可靠)
            val htmlFromRecords = extractHtmlFromRecords(file)
            val htmlContent = if (htmlFromRecords.isNotEmpty() && htmlFromRecords.length > 1000) {
                logger.info("Using HTML extracted from MOBI records (${htmlFromRecords.length} chars)")
                htmlFromRecords
            } else {
                // 方案2: 回退到Tika提取
                logger.info("Falling back to Tika extraction")
                extractHtmlContent(file)
            }
            
            if (htmlContent.isEmpty()) {
                logger.warn("No HTML content extracted from MOBI/AZW3 file")
                return null
            }
            
            // 从 HTML 中检测章节
            val chapters = detectChaptersFromHtml(htmlContent)
            logger.info("Detected ${chapters.size} chapters from MOBI/AZW3")
            
            MobiStructure(chapters, htmlContent)
        } catch (e: Exception) {
            logger.error("Failed to parse MOBI/AZW3 structure: ${file.name}", e)
            null
        }
    }
    
    /**
     * 直接从 MOBI 记录提取 HTML 内容
     */
    private fun extractHtmlFromRecords(file: File): String {
        return try {
            val htmlParts = mutableListOf<String>()
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // Read record count
                raf.seek(76)
                val recordCountBytes = ByteArray(2)
                raf.read(recordCountBytes)
                val recordCount = java.nio.ByteBuffer.wrap(recordCountBytes)
                    .order(java.nio.ByteOrder.BIG_ENDIAN).short.toInt()
                
                if (recordCount < 1 || recordCount > 10000) {
                    logger.warn("Invalid record count: $recordCount")
                    return ""
                }
                
                // Read record offsets
                raf.seek(78)
                val offsets = mutableListOf<Int>()
                for (i in 0 until recordCount) {
                    val offsetBytes = ByteArray(4)
                    raf.read(offsetBytes)
                    val offset = java.nio.ByteBuffer.wrap(offsetBytes)
                        .order(java.nio.ByteOrder.BIG_ENDIAN).int
                    offsets.add(offset)
                    raf.skipBytes(4)  // Skip attributes
                }
                
                // Extract text from records (usually records 1 to N contain text)
                // Skip record 0 (MOBI header)
                val maxRecords = minOf(recordCount, 100)  // Limit to first 100 records
                for (i in 1 until maxRecords) {
                    try {
                        raf.seek(offsets[i].toLong())
                        
                        // Calculate record size
                        val size = if (i + 1 < offsets.size) {
                            offsets[i + 1] - offsets[i]
                        } else {
                            minOf(10000, (raf.length() - offsets[i]).toInt())
                        }
                        
                        if (size <= 0 || size > 100000) continue
                        
                        val data = ByteArray(size)
                        val bytesRead = raf.read(data)
                        
                        if (bytesRead > 0) {
                            // Try to decode as UTF-8
                            val text = String(data, 0, bytesRead, Charsets.UTF_8)
                            htmlParts.add(text)
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to read record $i: ${e.message}")
                    }
                }
            }
            
            val fullHtml = htmlParts.joinToString("\n")
            logger.debug("Extracted ${fullHtml.length} characters from MOBI records")
            fullHtml
            
        } catch (e: Exception) {
            logger.error("Failed to extract HTML from MOBI records", e)
            ""
        }
    }
    
    /**
     * 使用 Tika 提取 HTML 内容
     */
    private fun extractHtmlContent(file: File): String {
        return try {
            val metadata = Metadata()
            val outputStream = ByteArrayOutputStream()
            val handler = ToXMLContentHandler(outputStream, "UTF-8")
            
            FileInputStream(file).use { stream ->
                parser.parse(stream, handler, metadata)
            }
            
            val xmlContent = outputStream.toString("UTF-8")
            logger.debug("Extracted content length: ${xmlContent.length}")
            
            // 如果内容太短，可能解析失败
            if (xmlContent.length < 100) {
                logger.warn("Extracted content is too short, might be empty")
            }
            
            xmlContent
        } catch (e: Exception) {
            logger.error("Failed to extract HTML from MOBI/AZW3", e)
            ""
        }
    }
    
    /**
     * 从 HTML 内容中检测章节
     */
    private fun detectChaptersFromHtml(htmlContent: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        try {
            val doc = Jsoup.parse(htmlContent)
            
            // 查找所有标题标签 (h1, h2, h3, h4)
            val headings = doc.select("h1, h2, h3, h4, h5, h6")
            
            if (headings.isNotEmpty()) {
                // 基于 HTML 标题分章
                logger.info("Found ${headings.size} headings in HTML")
                headings.forEachIndexed { index, heading ->
                    val title = heading.text().trim()
                    if (title.isEmpty()) return@forEachIndexed
                    
                    val level = heading.tagName().substring(1).toIntOrNull()?.minus(1) ?: 0
                    
                    // 计算章节在原始 HTML 中的位置
                    val startPos = htmlContent.indexOf(heading.outerHtml())
                    val endPos = if (index < headings.size - 1) {
                        htmlContent.indexOf(headings[index + 1].outerHtml())
                    } else {
                        htmlContent.length
                    }
                    
                    if (startPos >= 0 && endPos > startPos) {
                        chapters.add(
                            ChapterInfo(
                                index = chapters.size,
                                title = title,
                                startPos = startPos,
                                endPos = endPos,
                                level = level
                            )
                        )
                    }
                }
            }
            
            // 如果没有找到标题，尝试基于特定模式分割
            if (chapters.isEmpty()) {
                logger.info("No headings found, trying pattern-based detection")
                val plainText = doc.text()
                chapters.addAll(detectChaptersByPattern(plainText, htmlContent))
            }
            
            // 如果还是没有章节，创建一个包含全部内容的章节
            if (chapters.isEmpty()) {
                logger.warn("No chapters detected, creating single chapter")
                chapters.add(
                    ChapterInfo(
                        index = 0,
                        title = "全文",
                        startPos = 0,
                        endPos = htmlContent.length,
                        level = 0
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to detect chapters from HTML", e)
            // 降级：创建单一章节
            chapters.add(
                ChapterInfo(
                    index = 0,
                    title = "全文",
                    startPos = 0,
                    endPos = htmlContent.length,
                    level = 0
                )
            )
        }
        
        return chapters
    }
    
    /**
     * 基于文本模式检测章节
     */
    private fun detectChaptersByPattern(plainText: String, htmlContent: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        // 方案1: 直接在HTML中搜索章节标记 (处理MOBI格式化的章节)
        val htmlChapterPattern = """<[^>]*>(第[零一二三四五六七八九十百千万0-9]+[章节回][^<]{0,50})</[^>]*>""".toRegex()
        val htmlMatches = htmlChapterPattern.findAll(htmlContent).toList()
        
        if (htmlMatches.isNotEmpty()) {
            logger.info("Found ${htmlMatches.size} chapters in HTML tags")
            htmlMatches.forEachIndexed { index, match ->
                val title = match.groupValues[1].trim()
                val startPos = match.range.first
                val endPos = if (index < htmlMatches.size - 1) {
                    htmlMatches[index + 1].range.first
                } else {
                    htmlContent.length
                }
                
                chapters.add(
                    ChapterInfo(
                        index = index,
                        title = title,
                        startPos = startPos,
                        endPos = endPos,
                        level = 0
                    )
                )
            }
            return chapters
        }
        
        // 方案2: 在纯文本中搜索章节模式 (原有逻辑)
        val chapterPatterns = listOf(
            """(?m)^(Chapter|CHAPTER|Ch\.)\s*([0-9]+|One|Two|Three|Four|Five).*$""".toRegex(),
            """(?m)^第([零一二三四五六七八九十百千万0-9]+)[章节回].*$""".toRegex(),
            """(?m)^([零一二三四五六七八九十百千万]+)[、．.].*$""".toRegex(),
            """(?m)^([0-9]{1,3})[、．.].*$""".toRegex()
        )
        
        val matches = mutableListOf<Pair<Int, String>>() // (position, title)
        
        chapterPatterns.forEach { pattern ->
            pattern.findAll(plainText).forEach { match ->
                matches.add(match.range.first to match.value.trim())
            }
        }
        
        if (matches.isEmpty()) {
            logger.debug("No chapter patterns found in plain text")
            return chapters
        }
        
        // 按位置排序并去重
        matches.sortBy { it.first }
        val uniqueMatches = matches.distinctBy { it.first }
        
        logger.info("Found ${uniqueMatches.size} chapter patterns in plain text")
        
        // 创建章节
        uniqueMatches.forEachIndexed { index, (pos, title) ->
            // 在 HTML 中查找对应位置
            val htmlPos = findTextPositionInHtml(htmlContent, title, pos)
            val endPos = if (index < uniqueMatches.size - 1) {
                val nextTitle = uniqueMatches[index + 1].second
                findTextPositionInHtml(htmlContent, nextTitle, uniqueMatches[index + 1].first)
            } else {
                htmlContent.length
            }
            
            if (htmlPos >= 0 && endPos > htmlPos) {
                chapters.add(
                    ChapterInfo(
                        index = index,
                        title = title,
                        startPos = htmlPos,
                        endPos = endPos,
                        level = 0
                    )
                )
            }
        }
        
        return chapters
    }
    
    /**
     * 在 HTML 中查找文本位置
     */
    private fun findTextPositionInHtml(htmlContent: String, text: String, approximatePos: Int): Int {
        // 首先尝试精确匹配
        var pos = htmlContent.indexOf(text)
        if (pos >= 0) return pos
        
        // 尝试模糊匹配（处理 HTML 标签）
        val cleanText = text.replace(Regex("\\s+"), " ")
        pos = htmlContent.indexOf(cleanText)
        if (pos >= 0) return pos
        
        // 使用近似位置
        return approximatePos.coerceIn(0, htmlContent.length)
    }
    
    /**
     * 提取章节内容
     */
    fun extractChapterContent(htmlContent: String, chapterInfo: ChapterInfo): List<ContentElement>? {
        return try {
            val chapterHtml = htmlContent.substring(chapterInfo.startPos, chapterInfo.endPos)
            parseHtmlToElements(chapterHtml, chapterInfo.title)
        } catch (e: Exception) {
            logger.error("Failed to extract MOBI chapter content", e)
            null
        }
    }
    
    /**
     * 将 HTML 解析为结构化内容元素
     */
    private fun parseHtmlToElements(html: String, chapterTitle: String?): List<ContentElement> {
        val elements = mutableListOf<ContentElement>()
        
        try {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            
            // 添加章节标题
            if (!chapterTitle.isNullOrBlank()) {
                elements.add(ContentElement.Heading(1, chapterTitle))
            }
            
            body.children().forEach { element ->
                parseElement(element, elements)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse HTML to elements", e)
        }
        
        return elements
    }
    
    /**
     * 递归解析 HTML 元素
     */
    private fun parseElement(element: Element, elements: MutableList<ContentElement>) {
        when (element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toIntOrNull() ?: 1
                val text = element.text().trim()
                if (text.isNotEmpty()) {
                    elements.add(ContentElement.Heading(level, text))
                }
            }
            "p" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Paragraph(spans))
                }
            }
            "blockquote" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Quote(spans))
                }
            }
            "pre", "code" -> {
                val text = element.text()
                if (text.isNotEmpty()) {
                    elements.add(ContentElement.Code(text))
                }
            }
            "hr" -> {
                elements.add(ContentElement.Divider)
            }
            "div", "section", "article", "body" -> {
                // 递归处理容器元素
                element.children().forEach { child ->
                    parseElement(child, elements)
                }
            }
            else -> {
                // 其他元素，尝试提取文本作为段落
                val text = element.ownText().trim()
                if (text.isNotEmpty()) {
                    elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                }
            }
        }
    }
    
    /**
     * 解析行内元素为 TextSpan
     */
    private fun parseInlineElements(element: Element): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        
        element.childNodes().forEach { node ->
            when (node) {
                is TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) {
                        spans.add(TextSpan(text))
                    }
                }
                is Element -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) {
                        val styles = mutableListOf<com.bookd.domain.model.TextStyle>()
                        
                        when (node.tagName().lowercase()) {
                            "strong", "b" -> styles.add(com.bookd.domain.model.TextStyle.BOLD)
                            "em", "i" -> styles.add(com.bookd.domain.model.TextStyle.ITALIC)
                            "u" -> styles.add(com.bookd.domain.model.TextStyle.UNDERLINE)
                            "s", "strike", "del" -> styles.add(com.bookd.domain.model.TextStyle.STRIKETHROUGH)
                            "code" -> styles.add(com.bookd.domain.model.TextStyle.CODE)
                        }
                        
                        spans.add(TextSpan(text, styles))
                    }
                }
            }
        }
        
        return spans
    }
    
    /**
     * 计算字数
     */
    fun countWords(text: String): Int {
        val chineseCount = text.count { it.code in 0x4E00..0x9FFF }
        val englishWords = text.split(Regex("\\s+"))
            .count { it.matches(Regex("[a-zA-Z]+")) }
        
        return chineseCount + englishWords
    }
}
