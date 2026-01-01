package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * HTML 文章解析适配器
 * 将 HTML 文章适配到统一的 BookParser 接口
 */
class HtmlBookParser(
    private val htmlParser: HtmlParser
) : BookParser {
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = htmlParser.parseStructure(file) ?: return null
        
        val chapters = structure.chapters.map { chapter ->
            BookParser.ChapterInfo(
                index = chapter.index,
                title = cleanTitle(chapter.title),
                level = chapter.level,
                href = null,
                startPos = chapter.startOffset,
                endPos = chapter.endOffset
            )
        }
        
        return BookParser.BookStructure(
            chapters = chapters,
            resources = emptyMap()
        )
    }
    
    override suspend fun parseChapterContent(
        file: File,
        chapter: BookParser.ChapterInfo
    ): List<ContentElement> {
        return htmlParser.parseChapterContent(file, chapter.startPos, chapter.endPos)
    }
}
