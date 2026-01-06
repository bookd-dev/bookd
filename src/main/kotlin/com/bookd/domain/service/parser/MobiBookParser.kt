package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * MOBI/AZW3 格式解析器
 */
class MobiBookParser(
    private val mobiParser: MobiParser
) : BookParser {
    
    private var cachedStructure: MobiParser.MobiStructure? = null
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = mobiParser.parseStructure(file) ?: return null
        cachedStructure = structure
        
        val chapters = structure.chapters.map { chapter ->
            BookParser.ChapterInfo(
                index = chapter.index,
                title = cleanTitle(chapter.title),
                level = chapter.level
            )
        }
        
        return BookParser.BookStructure(chapters = chapters)
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        val structure = cachedStructure ?: mobiParser.parseStructure(file) ?: return emptyList()
        
        val mobiChapter = structure.chapters.find { it.index == chapter.index } ?: return emptyList()
        return mobiParser.extractChapterContent(structure.htmlContent, mobiChapter) ?: emptyList()
    }
}
