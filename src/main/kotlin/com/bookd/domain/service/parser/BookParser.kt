package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * 统一的书籍解析器接口
 */
interface BookParser {
    /**
     * 书籍结构信息
     */
    data class BookStructure(
        val chapters: List<ChapterInfo>,
        val resources: Map<String, ByteArray> = emptyMap()
    )
    
    /**
     * 章节基本信息
     */
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val level: Int = 0,
        val href: String? = null,
        val inToc: Boolean = true, // 是否在目录中显示
        val startPos: Int = 0,
        val endPos: Int = 0
    )
    
    /**
     * 解析书籍结构（章节列表）
     */
    suspend fun parseStructure(file: File): BookStructure?
    
    /**
     * 解析单个章节内容
     * @param file 书籍文件
     * @param chapter 章节信息
     * @param bookId 书籍ID（用于图片持久化等场景）
     */
    suspend fun parseChapterContent(file: File, chapter: ChapterInfo, bookId: Int = 0): List<ContentElement>
    
    /**
     * 统计字数
     * 中文按字符数,英文按单词数
     */
    fun countWords(elements: List<ContentElement>): Int {
        var count = 0
        elements.forEach { element ->
            when (element) {
                is ContentElement.Paragraph -> {
                    element.spans.forEach { span ->
                        count += countWordsInText(span.text)
                    }
                }
                is ContentElement.Heading -> {
                    count += countWordsInText(element.text)
                }
                is ContentElement.Quote -> {
                    element.spans.forEach { span ->
                        count += countWordsInText(span.text)
                    }
                }
                is ContentElement.Code -> {
                    count += countWordsInText(element.text)
                }
                is ContentElement.ListBlock -> {
                    element.items.forEach { item ->
                        item.spans.forEach { span ->
                            count += countWordsInText(span.text)
                        }
                    }
                }
                else -> {}
            }
        }
        return count
    }
    
    /**
     * 统计单段文本的字数
     * - 中文/日文/韩文字符: 按字符数
     * - 英文/数字: 按单词数
     * - 其他: 按字符数
     */
    private fun countWordsInText(text: String): Int {
        var count = 0
        
        // 统计中文字符 (CJK Unified Ideographs)
        val cjkCount = text.count { 
            it.code in 0x4E00..0x9FFF ||  // CJK Unified Ideographs
            it.code in 0x3400..0x4DBF ||  // CJK Extension A
            it.code in 0x20000..0x2A6DF || // CJK Extension B
            it.code in 0xF900..0xFAFF ||  // CJK Compatibility Ideographs
            it.code in 0x3040..0x309F ||  // Hiragana
            it.code in 0x30A0..0x30FF ||  // Katakana
            it.code in 0xAC00..0xD7AF     // Hangul
        }
        count += cjkCount
        
        // 统计英文单词 (去除 CJK 字符后再统计)
        val nonCjkText = text.filter { 
            it.code !in 0x4E00..0x9FFF &&
            it.code !in 0x3400..0x4DBF &&
            it.code !in 0x20000..0x2A6DF &&
            it.code !in 0xF900..0xFAFF &&
            it.code !in 0x3040..0x309F &&
            it.code !in 0x30A0..0x30FF &&
            it.code !in 0xAC00..0xD7AF
        }
        
        if (nonCjkText.isNotBlank()) {
            // 按空白字符分割,统计有效单词
            val words = nonCjkText.split(Regex("\\s+"))
                .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
            count += words.size
        }
        
        return count
    }
    
    /**
     * 统计图片数量
     */
    fun countImages(elements: List<ContentElement>): Int {
        return elements.count { it is ContentElement.Image }
    }
    
    /**
     * 清理标题中的非法字符
     */
    fun cleanTitle(title: String?): String? {
        return title?.replace("\u0000", "")?.trim()
    }
}
