package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.ListItem
import com.bookd.domain.model.TextSpan
import com.bookd.domain.model.TextStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.File

/**
 * HTML 文章解析器
 * 支持单 HTML 文件作为完整文章，可选择性提取 h1/h2 作为章节分隔
 */
class HtmlParser {
    
    private val logger = LoggerFactory.getLogger(HtmlParser::class.java)
    
    data class HtmlStructure(
        val title: String?,
        val chapters: List<ChapterInfo>,
        val metadata: HtmlMetadata?
    )
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val level: Int,
        val startOffset: Int,
        val endOffset: Int
    )
    
    data class HtmlMetadata(
        val title: String?,
        val author: String?,
        val description: String?,
        val keywords: List<String> = emptyList()
    )
    
    /**
     * 解析 HTML 文件结构
     * 策略：
     * 1. 提取 meta 信息
     * 2. 基于 h1/h2 标签自动分章，如果没有则作为单章节
     */
    fun parseStructure(file: File): HtmlStructure? {
        try {
            val html = file.readText()
            val doc = Jsoup.parse(html)
            
            // 1. 提取元数据
            val metadata = extractMetadata(doc)
            
            // 2. 获取主体内容区域
            val body = findMainContent(doc)
            
            // 3. 分析章节结构
            val chapters = analyzeChapters(body)
            
            return HtmlStructure(
                title = metadata.title,
                chapters = chapters,
                metadata = metadata
            )
        } catch (e: Exception) {
            logger.error("Failed to parse HTML structure: ${file.name}", e)
            return null
        }
    }
    
    /**
     * 解析章节内容
     */
    fun parseChapterContent(file: File, startOffset: Int, endOffset: Int): List<ContentElement> {
        try {
            val html = file.readText()
            val doc = Jsoup.parse(html)
            val body = findMainContent(doc)
            
            // 获取指定范围的元素
            val allElements = body.children()
            val chapterElements = if (endOffset > startOffset) {
                allElements.subList(startOffset, minOf(endOffset, allElements.size))
            } else {
                allElements
            }
            
            return parseElements(chapterElements)
        } catch (e: Exception) {
            logger.error("Failed to parse chapter content", e)
            return emptyList()
        }
    }
    
    /**
     * 提取 HTML 元数据
     */
    private fun extractMetadata(doc: org.jsoup.nodes.Document): HtmlMetadata {
        val title = doc.select("meta[property=og:title]").attr("content").ifEmpty {
            doc.select("meta[name=title]").attr("content").ifEmpty {
                doc.title()
            }
        }
        
        val author = doc.select("meta[name=author]").attr("content").ifEmpty {
            doc.select("meta[property=article:author]").attr("content")
        }
        
        val description = doc.select("meta[name=description]").attr("content").ifEmpty {
            doc.select("meta[property=og:description]").attr("content")
        }
        
        val keywords = doc.select("meta[name=keywords]").attr("content")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        return HtmlMetadata(
            title = title.takeIf { it.isNotEmpty() },
            author = author.takeIf { it.isNotEmpty() },
            description = description.takeIf { it.isNotEmpty() },
            keywords = keywords
        )
    }
    
    /**
     * 查找主体内容区域
     * 优先级: article > main > .content > .article > body
     */
    private fun findMainContent(doc: org.jsoup.nodes.Document): Element {
        return doc.selectFirst("article") 
            ?: doc.selectFirst("main")
            ?: doc.selectFirst(".content")
            ?: doc.selectFirst(".article")
            ?: doc.selectFirst("#content")
            ?: doc.body()
    }
    
    /**
     * 分析章节结构
     * 基于 h1/h2 标签分章，如果没有则整个文档作为单章节
     */
    private fun analyzeChapters(body: Element): List<ChapterInfo> {
        val children = body.children()
        val chapters = mutableListOf<ChapterInfo>()
        
        // 查找所有 h1/h2 标签位置
        val headingIndices = children.withIndex()
            .filter { (_, element) ->
                element.tagName() in listOf("h1", "h2")
            }
            .map { (index, element) ->
                Triple(index, element.tagName(), element.text())
            }
        
        // 如果没有标题，整个文档作为单章节
        if (headingIndices.isEmpty()) {
            chapters.add(
                ChapterInfo(
                    index = 0,
                    title = "正文",
                    level = 0,
                    startOffset = 0,
                    endOffset = children.size
                )
            )
            return chapters
        }
        
        // 基于标题分章
        headingIndices.forEachIndexed { index, (offset, tagName, title) ->
            val level = if (tagName == "h1") 0 else 1
            val nextOffset = headingIndices.getOrNull(index + 1)?.first ?: children.size
            
            chapters.add(
                ChapterInfo(
                    index = index,
                    title = title.takeIf { it.isNotEmpty() } ?: "第 ${index + 1} 章",
                    level = level,
                    startOffset = offset,
                    endOffset = nextOffset
                )
            )
        }
        
        return chapters
    }
    
    /**
     * 解析元素列表为 ContentElement
     */
    private fun parseElements(elements: List<Element>): List<ContentElement> {
        val contentElements = mutableListOf<ContentElement>()
        
        elements.forEach { element ->
            parseElement(element, contentElements)
        }
        
        return contentElements
    }
    
    /**
     * 递归解析单个元素
     */
    private fun parseElement(element: Element, result: MutableList<ContentElement>) {
        when (element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toIntOrNull() ?: 1
                val text = element.text()
                if (text.isNotEmpty()) {
                    result.add(ContentElement.Heading(level, text))
                }
            }
            "p" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    result.add(ContentElement.Paragraph(spans))
                }
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                val width = element.attr("width").toIntOrNull()
                val height = element.attr("height").toIntOrNull()
                
                if (src.isNotEmpty()) {
                    result.add(ContentElement.Image(src, alt, width, height))
                }
            }
            "blockquote" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    result.add(ContentElement.Quote(spans))
                }
            }
            "pre" -> {
                val code = element.selectFirst("code")
                val text = (code ?: element).text()
                val language = code?.className()
                    ?.split(" ")
                    ?.firstOrNull { it.startsWith("language-") }
                    ?.removePrefix("language-")
                
                if (text.isNotEmpty()) {
                    result.add(ContentElement.Code(text, language))
                }
            }
            "code" -> {
                // 单独的 code 标签（非 pre 内）
                if (element.parent()?.tagName() != "pre") {
                    val text = element.text()
                    if (text.isNotEmpty()) {
                        result.add(ContentElement.Code(text))
                    }
                }
            }
            "ul", "ol" -> {
                val ordered = element.tagName() == "ol"
                val items = element.select("> li")
                    .map { ListItem(parseInlineElements(it)) }
                    .filter { it.spans.isNotEmpty() }
                
                if (items.isNotEmpty()) {
                    result.add(ContentElement.ListBlock(ordered, items))
                }
            }
            "hr" -> {
                result.add(ContentElement.Divider)
            }
            "div", "section", "article", "header", "footer", "nav", "aside" -> {
                // 递归处理容器元素
                element.children().forEach { child ->
                    parseElement(child, result)
                }
            }
            "table" -> {
                // 暂时跳过表格，可以后续扩展
                logger.debug("Skipping table element")
            }
        }
    }
    
    /**
     * 解析行内元素为 TextSpan
     */
    private fun parseInlineElements(element: Element): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        
        element.childNodes().forEach { node ->
            collectTextSpans(node, spans, emptyList(), null)
        }
        
        return spans
    }
    
    /**
     * 递归收集文本片段
     */
    private fun collectTextSpans(
        node: Node,
        spans: MutableList<TextSpan>,
        inheritedStyles: List<TextStyle>,
        inheritedLink: String?
    ) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    spans.add(TextSpan(text, inheritedStyles, inheritedLink))
                }
            }
            is Element -> {
                var styles = inheritedStyles
                var link = inheritedLink
                
                when (node.tagName().lowercase()) {
                    "strong", "b" -> styles = styles + TextStyle.BOLD
                    "em", "i" -> styles = styles + TextStyle.ITALIC
                    "u" -> styles = styles + TextStyle.UNDERLINE
                    "s", "strike", "del" -> styles = styles + TextStyle.STRIKETHROUGH
                    "code" -> styles = styles + TextStyle.CODE
                    "a" -> {
                        styles = styles + TextStyle.UNDERLINE
                        link = node.attr("href").takeIf { it.isNotEmpty() } ?: link
                    }
                    "br" -> {
                        // 换行符处理
                        spans.add(TextSpan("\n", inheritedStyles, inheritedLink))
                        return
                    }
                }
                
                // 递归处理子节点
                node.childNodes().forEach { child ->
                    collectTextSpans(child, spans, styles, link)
                }
            }
        }
    }
}
