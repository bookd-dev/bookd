package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.domain.service.metadata.EbookParserClient
import com.bookd.domain.service.metadata.TxtParseRule
import java.io.File

/**
 * TXT 格式解析器
 * 
 * 使用 Python 微服务进行 TXT 解析，支持自定义规则
 */
class TxtBookParser(
    private val ebookParserClient: EbookParserClient,
    private val txtParseRuleService: TxtParseRuleService
) : BookParser {
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        // 获取启用的解析规则
        val rules = txtParseRuleService.getEnabledRules().map { rule ->
            TxtParseRule(
                name = rule.name,
                rule = rule.rule,
                example = rule.example,
                enabled = rule.enabled,
                priority = rule.priority
            )
        }
        
        // 调用 Python 微服务解析结构
        val response = ebookParserClient.parseTxtStructure(
            file.absolutePath,
            0, // bookId 暂时用 0，实际使用时会传入正确的 ID
            rules
        ) ?: run {
            throw Exception("Failed to parse TXT structure via microservice")
        }
        
        if (!response.success) {
            throw Exception("TXT structure parsing failed: ${response.error}")
        }
        
        // 转换为 BookStructure
        val chapters = response.chapters.map { dto ->
            BookParser.ChapterInfo(
                index = dto.index,
                title = cleanTitle(dto.title ?: ""),
                level = dto.level,
                startPos = dto.start_pos,
                endPos = dto.end_pos
            )
        }
        
        return BookParser.BookStructure(chapters = chapters)
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo, bookId: Int): List<ContentElement> {
        // 调用 Python 微服务解析章节内容
        val response = ebookParserClient.parseTxtChapterContent(
            file.absolutePath,
            bookId,
            chapter.startPos ?: 0,
            chapter.endPos ?: 0,
            chapter.title
        ) ?: run {
            throw Exception("Failed to parse TXT chapter content via microservice")
        }
        
        if (!response.success) {
            throw Exception("TXT chapter content parsing failed: ${response.error}")
        }
        
        return response.elements
    }
}
