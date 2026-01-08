package com.bookd.domain.service.parser.epub

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.ListItem
import com.bookd.domain.model.TextSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory

/**
 * EPUB 内容解析器
 * 负责将 HTML 内容解析为结构化的 ContentElement 列表
 */
class EpubContentParser(
    private val inlineParser: InlineParser = InlineParser()
) {
    private val logger = LoggerFactory.getLogger(EpubContentParser::class.java)
    
    /**
     * 将 HTML 解析为结构化内容元素
     */
    fun parseHtml(html: String, chapterHref: String): List<ContentElement> {
        val elements = mutableListOf<ContentElement>()
        
        try {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            
            body.children().forEach { element ->
                parseElement(element, elements, chapterHref)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse HTML to elements", e)
        }
        
        return elements
    }
    
    /**
     * 递归解析 HTML 元素
     */
    private fun parseElement(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        when (val tagName = element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tagName.substring(1).toIntOrNull() ?: 1
                elements.add(ContentElement.Heading(level, element.text()))
            }
            "p" -> {
                parseParagraph(element, elements, chapterHref)
            }
            "img" -> {
                parseImage(element, elements, chapterHref)
            }
            "aside" -> {
                parseAside(element, elements, chapterHref)
            }
            "blockquote" -> {
                val spans = inlineParser.parse(element, chapterHref)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Quote(spans))
                }
            }
            "pre", "code" -> {
                elements.add(ContentElement.Code(element.text()))
            }
            "ul", "ol" -> {
                parseList(element, elements, chapterHref)
            }
            "hr" -> {
                elements.add(ContentElement.Divider)
            }
            "div", "section", "article" -> {
                parseContainer(element, elements, chapterHref)
            }
        }
    }
    
    /**
     * 解析段落元素
     */
    private fun parseParagraph(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        // 检查是否为脚注定义段落
        val footnoteId = FootnoteParser.extractFootnoteDefinitionId(element)
        if (footnoteId != null) {
            val spans = inlineParser.parse(element, chapterHref)
            if (spans.isNotEmpty()) {
                elements.add(ContentElement.Footnote(footnoteId, spans))
            }
            return
        }
        
        // 检查段落是否包含图片（排除脚注图片）
        val images = element.select("img").filter { !FootnoteParser.isFootnoteImage(it) }
        if (images.isNotEmpty()) {
            images.forEach { img ->
                val src = img.attr("src")
                val alt = img.attr("alt")
                if (src.isNotEmpty()) {
                    val normalizedSrc = EpubPathUtils.normalizeImagePath(src, chapterHref)
                    elements.add(ContentElement.Image(normalizedSrc, alt))
                }
            }
        }
        
        // 解析文本内容
        val spans = inlineParser.parse(element, chapterHref)
        if (spans.isNotEmpty()) {
            elements.add(ContentElement.Paragraph(spans))
        }
    }
    
    /**
     * 解析图片元素
     */
    private fun parseImage(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        // 跳过脚注图片
        if (FootnoteParser.isFootnoteImage(element)) {
            return
        }
        val src = element.attr("src")
        val alt = element.attr("alt")
        if (src.isNotEmpty()) {
            val normalizedSrc = EpubPathUtils.normalizeImagePath(src, chapterHref)
            elements.add(ContentElement.Image(normalizedSrc, alt))
        }
    }
    
    /**
     * 解析 aside 元素（通常用于脚注定义）
     */
    private fun parseAside(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        if (FootnoteParser.isFootnoteContainer(element)) {
            parseFootnoteContainer(element, elements, chapterHref)
        } else {
            // 非脚注的 aside，递归处理
            element.children().forEach { child ->
                parseElement(child, elements, chapterHref)
            }
        }
    }
    
    /**
     * 解析脚注容器，处理嵌套的 ol/li 结构
     */
    private fun parseFootnoteContainer(container: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        // 查找所有带 id 的 li 元素（通常脚注内容在 li 中）
        val footnoteLis = container.select("li[id]")
        
        if (footnoteLis.isNotEmpty()) {
            footnoteLis.forEach { li ->
                val id = li.id()
                if (id.isNotEmpty()) {
                    val spans = inlineParser.parse(li, chapterHref)
                    if (spans.isNotEmpty()) {
                        elements.add(ContentElement.Footnote(id, spans))
                    }
                }
            }
        } else {
            // 没有嵌套 li，直接使用容器的 id
            val id = container.id()
            if (id.isNotEmpty()) {
                val spans = inlineParser.parse(container, chapterHref)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Footnote(id, spans))
                }
            }
        }
    }
    
    /**
     * 解析列表元素
     */
    private fun parseList(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        val ordered = element.tagName() == "ol"
        val items = element.children()
            .filter { it.tagName() == "li" }
            .map { ListItem(inlineParser.parse(it, chapterHref)) }
        
        if (items.isNotEmpty()) {
            elements.add(ContentElement.ListBlock(ordered, items))
        }
    }
    
    /**
     * 解析容器元素（div/section/article）
     */
    private fun parseContainer(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        // 检查是否为脚注定义容器
        val footnoteId = FootnoteParser.extractFootnoteDefinitionId(element)
        if (footnoteId != null) {
            val spans = inlineParser.parse(element, chapterHref)
            if (spans.isNotEmpty()) {
                elements.add(ContentElement.Footnote(footnoteId, spans))
            }
            return
        }
        
        // 检查是否有直接文本内容（混合文本和 br 标签）
        val hasDirectText = element.childNodes().any { it is TextNode && it.text().trim().isNotEmpty() }
        val hasBrTags = element.select("br").isNotEmpty()
        
        if (hasDirectText && hasBrTags) {
            parseTextWithBreaks(element, elements, chapterHref)
        } else {
            // 递归处理容器元素
            element.children().forEach { child ->
                parseElement(child, elements, chapterHref)
            }
        }
    }
    
    /**
     * 解析包含 br 标签的混合文本
     */
    private fun parseTextWithBreaks(element: Element, elements: MutableList<ContentElement>, chapterHref: String) {
        val currentLineText = StringBuilder()
        
        element.childNodes().forEach { node ->
            when (node) {
                is TextNode -> {
                    currentLineText.append(node.text())
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "br" -> {
                            val text = currentLineText.toString().trim()
                            if (text.isNotEmpty()) {
                                elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                            }
                            currentLineText.clear()
                        }
                        "img" -> {
                            if (FootnoteParser.isFootnoteImage(node)) {
                                return@forEach
                            }
                            val text = currentLineText.toString().trim()
                            if (text.isNotEmpty()) {
                                elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                                currentLineText.clear()
                            }
                            val src = node.attr("src")
                            val alt = node.attr("alt")
                            if (src.isNotEmpty()) {
                                val normalizedSrc = EpubPathUtils.normalizeImagePath(src, chapterHref)
                                elements.add(ContentElement.Image(normalizedSrc, alt))
                            }
                        }
                        else -> {
                            currentLineText.append(node.text())
                        }
                    }
                }
            }
        }
        
        // 处理剩余文本
        val text = currentLineText.toString().trim()
        if (text.isNotEmpty()) {
            elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
        }
    }
}
