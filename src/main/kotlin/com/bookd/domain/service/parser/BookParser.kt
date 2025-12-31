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
        val startPos: Int = 0,
        val endPos: Int = 0
    )
    
    /**
     * 解析书籍结构（章节列表）
     */
    suspend fun parseStructure(file: File): BookStructure?
    
    /**
     * 解析单个章节内容
     */
    suspend fun parseChapterContent(file: File, chapter: ChapterInfo): List<ContentElement>
    
    /**
     * 统计字数
     */
    fun countWords(elements: List<ContentElement>): Int {
        var count = 0
        elements.forEach { element ->
            when (element) {
                is ContentElement.Paragraph -> {
                    element.spans.forEach { span ->
                        count += span.text.length
                    }
                }
                is ContentElement.Heading -> {
                    count += element.text.length
                }
                is ContentElement.Quote -> {
                    element.spans.forEach { span ->
                        count += span.text.length
                    }
                }
                is ContentElement.Code -> {
                    count += element.text.length
                }
                is ContentElement.ListBlock -> {
                    element.items.forEach { item ->
                        item.spans.forEach { span ->
                            count += span.text.length
                        }
                    }
                }
                else -> {}
            }
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
