package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * EPUB 格式解析器
 */
class EpubBookParser(
    private val epubParser: EpubParser
) : BookParser {
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = epubParser.parseStructure(file) ?: return null
        
        val chapters = structure.chapters.map { doc ->
            BookParser.ChapterInfo(
                index = doc.index,
                title = cleanTitle(doc.title),
                level = doc.level,
                href = doc.href,
                inToc = doc.inToc
            )
        }
        
        return BookParser.BookStructure(
            chapters = chapters,
            resources = structure.resources
        )
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        return epubParser.parseChapterContent(file, chapter.href ?: "") ?: emptyList()
    }
}
