package com.bookd.domain.service.parser.epub

import org.jsoup.nodes.Element

/**
 * 脚注解析器
 * 负责识别脚注链接和解析脚注定义
 */
object FootnoteParser {
    
    /**
     * 判断链接是否为脚注链接
     */
    fun isFootnoteLink(element: Element): Boolean {
        val href = element.attr("href")
        val epubType = element.attr("epub:type")
        val className = element.className()
        
        // 检查 epub:type="noteref" 或常见脚注类名
        if (epubType.contains("noteref") || epubType.contains("footnote")) {
            return true
        }
        
        // 检查类名
        if (className.contains("footnote") || className.contains("note")) {
            return true
        }
        
        // 检查 href 是否指向页内锚点（脚注通常如此）
        if (href.startsWith("#n") || href.startsWith("#fn") || href.startsWith("#note")) {
            return true
        }
        
        // 检查是否只包含脚注图标图片
        val imgs = element.select("img")
        if (imgs.isNotEmpty() && element.text().trim().isEmpty()) {
            val imgSrc = imgs.first()?.attr("src") ?: ""
            if (imgSrc.contains("note") || imgSrc.contains("footnote")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 从脚注链接中提取脚注 ID
     */
    fun extractFootnoteId(element: Element): String? {
        val href = element.attr("href")
        if (href.startsWith("#")) {
            return href.substring(1)
        }
        // 对于跨文件脚注引用，提取锚点部分
        if (href.contains("#")) {
            return href.substringAfter("#")
        }
        return element.attr("id").takeIf { it.isNotEmpty() }
    }
    
    /**
     * 从脚注链接中提取脚注图片路径（已正规化）
     */
    fun extractFootnoteImage(element: Element, chapterHref: String): String? {
        val img = element.selectFirst("img")
        val src = img?.attr("src")?.takeIf { it.isNotEmpty() } ?: return null
        return EpubPathUtils.normalizeImagePath(src, chapterHref)
    }
    
    /**
     * 判断图片是否为脚注标记图片
     */
    fun isFootnoteImage(element: Element): Boolean {
        val src = element.attr("src").lowercase()
        val alt = element.attr("alt").lowercase()
        val className = element.className().lowercase()
        
        return src.contains("note") || alt.contains("note") ||
                className.contains("footnote") || className.contains("note")
    }
    
    /**
     * 判断元素是否为脚注定义容器
     */
    fun isFootnoteContainer(element: Element): Boolean {
        val epubType = element.attr("epub:type")
        val className = element.className().lowercase()
        
        return epubType.contains("footnote") || epubType.contains("rearnote") ||
                className.contains("footnote")
    }
    
    /**
     * 从脚注定义元素中提取脚注 ID
     */
    fun extractFootnoteDefinitionId(element: Element): String? {
        val epubType = element.attr("epub:type")
        val className = element.className().lowercase()
        val id = element.id()
        
        // EPUB3 脚注定义: <aside epub:type="footnote" id="n20">
        if (epubType.contains("footnote") || epubType.contains("rearnote")) {
            return id.takeIf { it.isNotEmpty() }
        }
        
        // 类名标识的脚注: <div class="footnote" id="n20">
        if (className.contains("footnote") || className.contains("duokan-footnote")) {
            return id.takeIf { it.isNotEmpty() }
        }
        
        // 通过 ID 模式匹配: id="n20", id="fn20", id="note20"
        if (id.matches(Regex("^(n|fn|note|footnote)\\d+$", RegexOption.IGNORE_CASE))) {
            return id
        }
        
        return null
    }
}
