package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import java.io.File

/**
 * PDF 格式解析器
 */
class PdfBookParser(
    private val pdfParser: PdfParser
) : BookParser {
    
    // 缓存 PDF 章节结构(包含页码信息)
    private var cachedStructure: PdfParser.PdfStructure? = null
    
    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val structure = pdfParser.parseStructure(file, extractImages = false) ?: return null
        
        // 缓存原始结构
        cachedStructure = structure
        
        val chapters = structure.chapters.map { chapter ->
            BookParser.ChapterInfo(
                index = chapter.index,
                title = cleanTitle(chapter.title),
                level = chapter.level,
                startPos = chapter.startPage,  // 存储开始页码
                endPos = chapter.endPage        // 存储结束页码
            )
        }
        
        return BookParser.BookStructure(chapters = chapters)
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        // 使用缓存的结构或重新解析
        val structure = cachedStructure ?: pdfParser.parseStructure(file, false)
        
        if (structure != null) {
            val pdfChapter = structure.chapters.find { it.index == chapter.index }
            if (pdfChapter != null) {
                return pdfParser.extractChapterContent(file, pdfChapter) ?: emptyList()
            }
        }
        
        // 回退: 使用 startPos/endPos 作为页码
        val pdfChapter = PdfParser.ChapterInfo(
            index = chapter.index,
            title = chapter.title,
            startPage = chapter.startPos,
            endPage = chapter.endPos,
            level = chapter.level
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
