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
        
        return BookParser.BookStructure(chapters = chapters)
    }
    
    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        val href = chapter.href ?: throw IllegalArgumentException("EPUB chapter href is missing")
        return epubParser.parseChapterContent(file, href)
            ?: throw IllegalStateException("Failed to parse EPUB chapter: $href")
    }

    override suspend fun parseChapterContents(
        file: File,
        chapters: List<BookParser.ChapterInfo>
    ): Map<Int, List<ContentElement>> {
        val hrefs = chapters.map { chapter ->
            chapter.href ?: throw IllegalArgumentException("EPUB chapter href is missing at index ${chapter.index}")
        }
        val contentsByHref = epubParser.parseChapterContents(file, hrefs)
            ?: throw IllegalStateException("Failed to parse EPUB chapters")
        return chapters.associate { chapter ->
            val href = requireNotNull(chapter.href)
            chapter.index to requireNotNull(contentsByHref[href]) {
                "EPUB chapter content is missing: $href"
            }
        }
    }

    override suspend fun forEachResource(file: File, consumer: (path: String, bytes: ByteArray) -> Unit) {
        epubParser.forEachResource(file, consumer)
    }
}
