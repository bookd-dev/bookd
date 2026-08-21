package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import com.bookd.domain.service.TxtParseRuleService
import org.slf4j.LoggerFactory
import java.io.File

class TxtParser(
    private val txtParseRuleService: TxtParseRuleService
) {
    
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
     * 获取启用的章节解析规则
     */
    private suspend fun getChapterPatterns(): List<Regex> {
        return try {
            val rules = txtParseRuleService.getEnabledRules()
            if (rules.isEmpty()) {
                logger.warn("No enabled TXT parse rules found, using fallback patterns")
                getFallbackPatterns()
            } else {
                rules.mapNotNull { rule ->
                    try {
                        rule.rule.toRegex()
                    } catch (e: Exception) {
                        logger.error("Invalid regex pattern for rule '${rule.name}': ${rule.rule}", e)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load TXT parse rules, using fallback patterns", e)
            getFallbackPatterns()
        }
    }
    
    /**
     * 备用模式（如果数据库规则加载失败）
     */
    private fun getFallbackPatterns(): List<Regex> = listOf(
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
    suspend fun parseStructure(file: File): TxtStructure? {
        try {
            val fullText = TxtFileDecoder.read(file)
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
    private suspend fun detectChapters(text: String): List<ChapterInfo> {
        val chapterPatterns = getChapterPatterns()
        val chapters = mutableListOf<ChapterInfo>()
        val lines = splitLinesWithOffsets(text)
        
        var currentChapterStart = 0
        var chapterIndex = 0
        var lastChapterLine = 0
        lines.forEachIndexed { lineIndex, line ->
            val trimmedLine = line.text.trim()
            
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
                                    title = lines[lastChapterLine].text.trim(),
                                    startPos = currentChapterStart,
                                    endPos = line.startOffset
                                )
                            )
                        }
                        
                        // 开始新章节
                        currentChapterStart = line.startOffset
                        lastChapterLine = lineIndex
                        chapterIndex++
                        
                        logger.debug("Found chapter $chapterIndex: $trimmedLine")
                        return@forEachIndexed
                    }
                }
            }
            
        }
        
        // 添加最后一章或整本书作为一章
        if (chapterIndex > 0) {
            chapters.add(
                ChapterInfo(
                    index = chapterIndex - 1,
                    title = lines[lastChapterLine].text.trim(),
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
     * 保留原始换行符所占的字符位置，避免 CRLF/CR 文本的章节区间逐行漂移。
     */
    private fun splitLinesWithOffsets(text: String): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\r' || char == '\n') {
                lines.add(TextLine(text.substring(lineStart, index), lineStart))
                index += if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') 2 else 1
                lineStart = index
            } else {
                index++
            }
        }
        lines.add(TextLine(text.substring(lineStart), lineStart))
        return lines
    }

    private data class TextLine(val text: String, val startOffset: Int)
    
    /**
     * 提取章节内容
     */
    fun extractChapterContent(fullText: String, chapterInfo: ChapterInfo): List<ContentElement> {
        val chapterText = fullText.substring(chapterInfo.startPos, chapterInfo.endPos)
        val anchorGenerator = ContentAnchorGenerator(
            sourceKind = "txt",
            chapterIdentity = chapterInfo.index.toString()
        )
        
        // 统一换行符为 \n，然后按行分割
        val normalizedText = chapterText.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalizedText.split("\n")
        
        val elements = mutableListOf<ContentElement>()
        var isFirstNonEmpty = true
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            
            if (trimmedLine.isEmpty()) {
                // 空行跳过
                return@forEach
            }
            
            // 第一个非空行如果是标题，添加为 Heading
            if (isFirstNonEmpty && chapterInfo.title != null && trimmedLine.startsWith(chapterInfo.title.trim())) {
                elements.add(
                    ContentElement.Heading(
                        level = 1,
                        text = trimmedLine,
                        anchorId = anchorGenerator.generatedAnchor("heading", trimmedLine)
                    )
                )
                isFirstNonEmpty = false
            } else {
                // 每行作为一个独立段落
                elements.add(
                    ContentElement.Paragraph(
                        spans = listOf(TextSpan(trimmedLine)),
                        anchorId = anchorGenerator.generatedAnchor("paragraph", trimmedLine)
                    )
                )
                isFirstNonEmpty = false
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
