package com.bookd.domain.service.parser.epub

import com.bookd.domain.model.TextSpan
import com.bookd.domain.model.TextStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 行内元素解析器
 * 负责将 HTML 行内元素解析为 TextSpan 列表
 */
class InlineParser {
    
    // 脚注计数器，用于生成默认脚注标记 [1], [2], [3]...
    private var footnoteCounter = 0
    
    /**
     * 重置脚注计数器（每章开始时调用）
     */
    fun resetFootnoteCounter() {
        footnoteCounter = 0
    }
    
    /**
     * 解析行内元素为 TextSpan 列表
     */
    fun parse(element: Element, chapterHref: String = ""): List<TextSpan> {
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
                    parseInlineElement(node, spans, chapterHref)
                }
            }
        }
        
        return spans
    }
    
    /**
     * 解析单个行内元素
     */
    private fun parseInlineElement(node: Element, spans: MutableList<TextSpan>, chapterHref: String) {
        val tagName = node.tagName().lowercase()
        
        // 检查是否为脚注链接
        if (tagName == "a" && FootnoteParser.isFootnoteLink(node)) {
            val footnoteId = FootnoteParser.extractFootnoteId(node)
            if (footnoteId != null) {
                val footnoteImage = FootnoteParser.extractFootnoteImage(node, chapterHref)
                // 保留原始脚注标记文本（如 [1]、*、† 等），若为空则生成序号标记
                val originalText = node.text().trim().takeIf { it.isNotEmpty() }
                    ?: "[${++footnoteCounter}]"
                spans.add(TextSpan(
                    text = originalText,
                    styles = listOf(TextStyle.BOLD),
                    footnoteId = footnoteId,
                    footnoteImage = footnoteImage
                ))
            }
            return
        }
        
        // 跳过脚注内的图片标记
        if (tagName == "img" && FootnoteParser.isFootnoteImage(node)) {
            return
        }
        
        val text = node.text().trim()
        if (text.isNotEmpty()) {
            val styles = mutableListOf<TextStyle>()
            var link: String? = null
            
            when (tagName) {
                "strong", "b" -> styles.add(TextStyle.BOLD)
                "em", "i" -> styles.add(TextStyle.ITALIC)
                "u" -> styles.add(TextStyle.UNDERLINE)
                "s", "strike", "del" -> styles.add(TextStyle.STRIKETHROUGH)
                "code" -> styles.add(TextStyle.CODE)
                "a" -> {
                    styles.add(TextStyle.UNDERLINE)
                    link = node.attr("href")
                }
                "sup", "sub" -> {
                    // 递归解析上标/下标内容
                    spans.addAll(parse(node, chapterHref))
                    return
                }
            }
            
            spans.add(TextSpan(text, styles, link))
        } else if (tagName == "sup" || tagName == "sub") {
            // 上标/下标可能只包含图片，递归处理
            spans.addAll(parse(node, chapterHref))
        }
    }
}
