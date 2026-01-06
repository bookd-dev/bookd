package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * TXT 格式解析器
 */
class TxtBookParser(
    private val txtParser: TxtParser
) : BookParser {
    
    private var cachedStructure: TxtParser.TxtStructure? = null
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = txtParser.parseStructure(file) ?: return null
        cachedStructure = structure
        
        val chapters = structure.chapters.map { chapter ->
            BookParser.ChapterInfo(
                index = chapter.index,
                title = cleanTitle(chapter.title),
                level = chapter.level,
                startPos = chapter.startPos,
                endPos = chapter.endPos
            )
        }
        
        return BookParser.BookStructure(chapters = chapters)
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        val structure = cachedStructure ?: txtParser.parseStructure(file) ?: return emptyList()
        
        val txtChapter = structure.chapters.find { it.index == chapter.index } ?: return emptyList()
        return txtParser.extractChapterContent(structure.fullText, txtChapter)
    }
}
