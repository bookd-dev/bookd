package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.parser.mobi.MobiNormalizer
import java.io.File

class MobiBookParser(private val normalizer: MobiNormalizer) : BookParser {
    private fun delegate(file: File): Pair<File, EpubBookParser> =
        normalizer.normalize(file) to EpubBookParser(EpubParser())

    override suspend fun parseStructure(file: File): BookParser.BookStructure? {
        val (normalized, parser) = delegate(file)
        return parser.parseStructure(normalized)
    }

    override suspend fun parseChapterContent(file: File, chapter: BookParser.ChapterInfo): List<ContentElement> {
        val (normalized, parser) = delegate(file)
        return parser.parseChapterContent(normalized, chapter)
    }

    override suspend fun parseChapterContents(
        file: File,
        chapters: List<BookParser.ChapterInfo>
    ): Map<Int, List<ContentElement>> {
        val (normalized, parser) = delegate(file)
        return parser.parseChapterContents(normalized, chapters)
    }

    override suspend fun forEachResource(file: File, consumer: (path: String, bytes: ByteArray) -> Unit) {
        val (normalized, parser) = delegate(file)
        parser.forEachResource(normalized, consumer)
    }
}
