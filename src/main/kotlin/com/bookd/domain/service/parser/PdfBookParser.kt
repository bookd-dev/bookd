package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * PDF 格式解析器
 */
class PdfBookParser(
    private val pdfParser: PdfParser
) : BookParser {
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = pdfParser.parseStructure(file, extractImages = false) ?: return null
        
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
        val pdfChapter = PdfParser.ChapterInfo(
            index = chapter.index,
            title = chapter.title,
            level = chapter.level,
            startPage = 0,
            endPage = 0
        )
        return pdfParser.extractChapterContent(file, pdfChapter) ?: emptyList()
    }
    
    /**
     * 提取 PDF 图片资源
     */
    fun extractImages(file: File): Map<String, ByteArray> {
        return pdfParser.extractImages(file)
    }
}
