package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.metadata.EbookParserClient
import java.io.File

/**
 * EPUB 格式解析器
 * 
 * 使用 Python 微服务（ebooklib）进行 EPUB 解析
 */
class EpubBookParser(
    private val ebookParserClient: EbookParserClient
) : BookParser {
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        // 调用 Python 微服务解析结构
        val response = ebookParserClient.parseStructure(file.absolutePath, 0) ?: run {
            throw Exception("Failed to parse EPUB structure via microservice")
        }
        
        if (!response.success) {
            throw Exception("EPUB structure parsing failed: ${response.error}")
        }
        
        // 转换为 BookStructure
        val chapters = response.chapters.map { dto ->
            BookParser.ChapterInfo(
                index = dto.index,
                title = cleanTitle(dto.title),
                level = dto.level,
                href = dto.href,
                inToc = dto.in_toc
            )
        }
        
        return BookParser.BookStructure(
            chapters = chapters,
            resources = emptyMap() // Python 服务不返回资源
        )
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo, bookId: Int): List<ContentElement> {
        // 调用 Python 微服务解析章节内容
        val response = ebookParserClient.parseChapterContent(
            file.absolutePath,
            bookId,
            chapter.href ?: ""
        ) ?: run {
            throw Exception("Failed to parse chapter content via microservice")
        }
        
        if (!response.success) {
            throw Exception("Chapter content parsing failed: ${response.error}")
        }
        
        return response.elements
    }
}
