package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
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
            // 使用 Tika 提取 HTML 内容
            val htmlContent = extractHtmlContent(file)
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
     * 使用 Tika 提取 HTML 内容
     */
    private fun extractHtmlContent(file: File): String {
        return try {
            val metadata = Metadata()
            val handler = BodyContentHandler(-1) // 无限制
            
            FileInputStream(file).use { stream ->
                parser.parse(stream, handler, metadata)
            }
            
            handler.toString()
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
            
            // 查找所有标题标签 (h1, h2, h3)
            val headings = doc.select("h1, h2, h3, h4")
            
            if (headings.isEmpty()) {
                // 如果没有标题，尝试基于特定模式分割
                chapters.addAll(detectChaptersByPattern(htmlContent))
            } else {
                // 基于 HTML 标题分章
                headings.forEachIndexed { index, heading ->
                    val title = heading.text().trim()
                    val level = heading.tagName().substring(1).toIntOrNull() ?: 1
                    
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
                                index = index,
                                title = title,
                                startPos = startPos,
                                endPos = endPos,
                                level = level - 1 // 转换为 0-based level
                            )
                        )
                    }
                }
            }
            
            // 如果还是没有章节，创建一个包含全部内容的章节
            if (chapters.isEmpty()) {
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
        }
        
        return chapters
    }
    
    /**
     * 基于文本模式检测章节
     */
    private fun detectChaptersByPattern(htmlContent: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        val plainText = Jsoup.parse(htmlContent).text()
        
        // 章节模式（与 TxtParser 类似）
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
        
        // 按位置排序
        matches.sortBy { it.first }
        
        // 创建章节
        matches.forEachIndexed { index, (pos, title) ->
            val endPos = if (index < matches.size - 1) {
                matches[index + 1].first
            } else {
                htmlContent.length
            }
            
            chapters.add(
                ChapterInfo(
                    index = index,
                    title = title,
                    startPos = pos,
                    endPos = endPos,
                    level = 0
                )
            )
        }
        
        return chapters
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
