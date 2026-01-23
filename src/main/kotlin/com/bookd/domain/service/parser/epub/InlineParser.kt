package com.bookd.domain.service.parser.epub

import com.bookd.domain.model.TextSpan
import com.bookd.domain.model.TextStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 脚注引用信息
 */
data class FootnoteReference(
    val footnoteId: String,
    val footnoteImage: String?,  // 正规化后的图片路径
    val footnoteIndex: Int  // 脚注序号（1, 2, 3...）
)

/**
 * 行内元素解析器
 * 负责将 HTML 行内元素解析为 TextSpan 列表
 */
class InlineParser {
    
    // 脚注计数器，用于生成默认脚注标记 [1], [2], [3]...
    private var footnoteCounter = 0
    
    // 脚注引用信息映射 (footnoteId -> FootnoteReference)
    private val footnoteReferences = mutableMapOf<String, FootnoteReference>()
    
    /**
     * 重置脚注计数器（每章开始时调用）
     */
    fun resetFootnoteCounter() {
        footnoteCounter = 0
        footnoteReferences.clear()
    }
    
    /**
     * 获取收集到的脚注引用信息
     */
    fun getFootnoteReferences(): Map<String, FootnoteReference> {
        return footnoteReferences.toMap()
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
                
                // 递增脚注计数器并生成序号标记
                val footnoteIndex = ++footnoteCounter
                val indexText = "[$footnoteIndex]"
                
                // 如果原文有文本（不是纯图片），使用原文；否则使用序号
                val displayText = node.text().trim().takeIf { it.isNotEmpty() } ?: indexText
                
                // 记录脚注引用信息（始终使用序号，不管原文显示什么）
                footnoteReferences[footnoteId] = FootnoteReference(
                    footnoteId = footnoteId,
                    footnoteImage = footnoteImage,
                    footnoteIndex = footnoteIndex
                )
                
                spans.add(TextSpan(
                    text = displayText,
                    styles = listOf(TextStyle.BOLD),
                    footnoteId = footnoteId
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
