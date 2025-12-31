package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import org.slf4j.LoggerFactory
import java.io.File

class TxtParser {
    
    private val logger = LoggerFactory.getLogger(TxtParser::class.java)
    
    data class TxtStructure(
        val chapters: List<ChapterInfo>,
        val fullText: String
    )
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val startPos: Int,
        val endPos: Int,
        val level: Int = 0
    )
    
    /**
     * 章节标题正则模式
     */
    private val chapterPatterns = listOf(
        // 英文: Chapter 1, Chapter One, Ch.1, Ch 1
        """^(Chapter|CHAPTER|Ch\.|Ch)\s*([0-9]+|One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten).*$""".toRegex(),
        
        // 中文数字: 第一章, 第1章, 第一节, 第1节, 第一回, 第1回
        """^第([零一二三四五六七八九十百千万0-9]+)[章节回].*$""".toRegex(),
        
        // 简化: 一、二、三、
        """^([零一二三四五六七八九十百千万]+)[、．.].*$""".toRegex(),
        
        // 阿拉伯数字: 1., 1、, 01.
        """^([0-9]{1,3})[、．.].*$""".toRegex(),
        
        // 卷/部/篇
        """^第([零一二三四五六七八九十百千万0-9]+)[卷部篇].*$""".toRegex()
    )
    
    /**
     * 解析 TXT 文件结构
     */
    fun parseStructure(file: File): TxtStructure? {
        try {
            val fullText = file.readText()
            val chapters = detectChapters(fullText)
            
            logger.info("Detected ${chapters.size} chapters in TXT file")
            return TxtStructure(chapters, fullText)
        } catch (e: Exception) {
            logger.error("Failed to parse TXT structure: ${file.name}", e)
            return null
        }
    }
    
    /**
     * 检测章节分割
     */
    private fun detectChapters(text: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        val lines = text.lines()
        
        var currentChapterStart = 0
        var chapterIndex = 0
        var lastChapterLine = 0
        var currentPosition = 0 // 追踪当前字符位置
        
        lines.forEachIndexed { lineIndex, line ->
            val trimmedLine = line.trim()
            val lineLength = line.length + 1 // +1 for newline
            
            // 跳过空行
            if (trimmedLine.isNotEmpty()) {
                // 检查是否匹配章节标题
                chapterPatterns.forEach { pattern ->
                    if (pattern.matches(trimmedLine)) {
                        // 如果不是第一章，保存前一章
                        if (chapterIndex > 0) {
                            chapters.add(
                                ChapterInfo(
                                    index = chapterIndex - 1,
                                    title = lines[lastChapterLine].trim(),
                                    startPos = currentChapterStart,
                                    endPos = currentPosition
                                )
                            )
                        }
                        
                        // 开始新章节
                        currentChapterStart = currentPosition
                        lastChapterLine = lineIndex
                        chapterIndex++
                        
                        logger.debug("Found chapter $chapterIndex: $trimmedLine")
                        return@forEachIndexed
                    }
                }
            }
            
            currentPosition += lineLength
        }
        
        // 添加最后一章或整本书作为一章
        if (chapterIndex > 0) {
            chapters.add(
                ChapterInfo(
                    index = chapterIndex - 1,
                    title = lines[lastChapterLine].trim(),
                    startPos = currentChapterStart,
                    endPos = text.length
                )
            )
        } else {
            // 没有检测到章节，整本书作为一章
            chapters.add(
                ChapterInfo(
                    index = 0,
                    title = "全文",
                    startPos = 0,
                    endPos = text.length
                )
            )
        }
        
        return chapters
    }
    
    /**
     * 提取章节内容
     */
    fun extractChapterContent(fullText: String, chapterInfo: ChapterInfo): List<ContentElement> {
        val chapterText = fullText.substring(chapterInfo.startPos, chapterInfo.endPos)
        val paragraphs = chapterText.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        val elements = mutableListOf<ContentElement>()
        
        paragraphs.forEachIndexed { index, paragraph ->
            // 第一个段落如果是标题，添加为 Heading
            if (index == 0 && chapterInfo.title != null && paragraph.startsWith(chapterInfo.title)) {
                elements.add(ContentElement.Heading(1, paragraph))
            } else {
                // 其他段落作为普通段落
                val spans = paragraph.split("\n")
                    .map { line -> TextSpan(line) }
                
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Paragraph(spans))
                }
            }
        }
        
        return elements
    }
    
    /**
     * 计算章节字数
     */
    fun countWords(text: String): Int {
        // 简单计数：中文按字符数，英文按单词数
        val chineseCount = text.count { it.code in 0x4E00..0x9FFF }
        val englishWords = text.split(Regex("\\s+"))
            .count { it.matches(Regex("[a-zA-Z]+")) }
        
        return chineseCount + englishWords
    }
}
