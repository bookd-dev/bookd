package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import com.bookd.domain.service.parser.pdf.PdfDocumentAccess
import com.bookd.domain.service.parser.pdf.PdfParseLimits
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PdfBookParser(
    private val limits: PdfParseLimits = PdfParseLimits(),
    private val nanoTime: () -> Long = System::nanoTime
) : BookParser {
    override suspend fun parseStructure(file: File): BookParser.BookStructure =
        PdfDocumentAccess.use(file, limits) { document ->
            BookParser.BookStructure(createChapters(document))
        }

    override suspend fun parseChapterContent(
        file: File,
        chapter: BookParser.ChapterInfo
    ): List<ContentElement> = parseChapterContents(file, listOf(chapter)).getValue(chapter.index)

    override suspend fun parseChapterContents(
        file: File,
        chapters: List<BookParser.ChapterInfo>
    ): Map<Int, List<ContentElement>> = PdfDocumentAccess.use(file, limits) { document ->
        val startedAt = nanoTime()
        var totalTextBytes = 0L
        var hasUsableText = false
        val result = linkedMapOf<Int, List<ContentElement>>()

        chapters.forEach { chapter ->
            validateChapterRange(chapter, document.numberOfPages)
            val href = requireNotNull(chapter.href) { "PDF chapter href is missing" }
            val anchors = ContentAnchorGenerator("pdf", href)
            val elements = mutableListOf<ContentElement>()
            chapter.title?.takeIf { it.isNotBlank() }?.let { title ->
                elements += ContentElement.Heading(
                    level = (chapter.level + 1).coerceIn(1, 6),
                    text = title,
                    anchorId = anchors.anchorFor("chapter-title", "heading", title)
                )
            }

            for (pageIndex in chapter.startPos until chapter.endPos) {
                ensureDeadline(startedAt)
                val pageNumber = pageIndex + 1
                elements += ContentElement.Divider(
                    anchorId = anchors.anchorFor("page-$pageNumber", "divider", "page-$pageNumber")
                )
                val pageText = extractPage(document, pageNumber)
                val pageBytes = pageText.toByteArray(Charsets.UTF_8).size
                if (pageBytes > limits.maxPageTextBytes) {
                    throw EbookParseException(
                        EbookParseFailureReason.PARSER_LIMIT_EXCEEDED,
                        "PDF page text exceeds configured limit"
                    )
                }
                totalTextBytes += pageBytes
                if (totalTextBytes > limits.maxTotalTextBytes) {
                    throw EbookParseException(
                        EbookParseFailureReason.PARSER_LIMIT_EXCEEDED,
                        "PDF extracted text exceeds configured total limit"
                    )
                }

                normalizeParagraphs(pageText).forEach { paragraph ->
                    hasUsableText = true
                    elements += ContentElement.Paragraph(
                        spans = listOf(TextSpan(paragraph)),
                        anchorId = anchors.generatedAnchor("paragraph", "page-$pageNumber:$paragraph")
                    )
                }
            }
            result[chapter.index] = elements
        }

        if (!hasUsableText) {
            throw EbookParseException(
                EbookParseFailureReason.PDF_IMAGE_ONLY,
                "PDF has no usable text layer"
            )
        }
        result
    }

    private fun createChapters(document: PDDocument): List<BookParser.ChapterInfo> {
        val outlineEntries = flattenOutline(document)
            .filter { it.pageIndex in 0 until document.numberOfPages }
            .sortedWith(compareBy<OutlineEntry> { it.pageIndex }.thenBy { it.order })
            .distinctBy { it.pageIndex }

        if (outlineEntries.isEmpty()) {
            return (0 until document.numberOfPages step limits.fallbackPagesPerChapter).mapIndexed { index, start ->
                val end = minOf(start + limits.fallbackPagesPerChapter, document.numberOfPages)
                chapter(index, "第 ${start + 1}-${end} 页", 0, true, start, end)
            }
        }

        val chapters = mutableListOf<BookParser.ChapterInfo>()
        var chapterIndex = 0
        if (outlineEntries.first().pageIndex > 0) {
            val end = outlineEntries.first().pageIndex
            chapters += chapter(chapterIndex++, "第 1-${end} 页", 0, false, 0, end)
        }
        outlineEntries.forEachIndexed { entryIndex, entry ->
            val end = outlineEntries.getOrNull(entryIndex + 1)?.pageIndex ?: document.numberOfPages
            if (entry.pageIndex < end) {
                chapters += chapter(
                    chapterIndex++,
                    cleanTitle(entry.title) ?: "第 ${entry.pageIndex + 1}-${end} 页",
                    entry.level,
                    true,
                    entry.pageIndex,
                    end
                )
            }
        }
        return chapters.ifEmpty {
            listOf(chapter(0, "第 1-${document.numberOfPages} 页", 0, true, 0, document.numberOfPages))
        }
    }

    private fun chapter(
        index: Int,
        title: String,
        level: Int,
        inToc: Boolean,
        start: Int,
        end: Int
    ) = BookParser.ChapterInfo(
        index = index,
        title = title,
        level = level,
        href = "pdf:pages:${(start + 1).toString().padStart(6, '0')}-${end.toString().padStart(6, '0')}",
        inToc = inToc,
        startPos = start,
        endPos = end
    )

    private fun flattenOutline(document: PDDocument): List<OutlineEntry> {
        val root = document.documentCatalog.documentOutline ?: return emptyList()
        val result = mutableListOf<OutlineEntry>()
        var order = 0

        fun visit(node: PDOutlineNode, level: Int) {
            node.children().forEach { item ->
                val pageIndex = runCatching { item.findDestinationPage(document) }
                    .getOrNull()
                    ?.let(document.pages::indexOf)
                    ?: -1
                if (pageIndex >= 0) {
                    result += OutlineEntry(pageIndex, item.title, level, order++)
                }
                visit(item, level + 1)
            }
        }
        visit(root, 0)
        return result
    }

    private fun extractPage(document: PDDocument, pageNumber: Int): String = PDFTextStripper().apply {
        sortByPosition = true
        startPage = pageNumber
        endPage = pageNumber
    }.getText(document)

    private fun normalizeParagraphs(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return emptyList()
        val blocks = normalized.split(Regex("\\n\\s*\\n+"))
        return blocks.mapNotNull { block ->
            block.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf(String::isNotBlank)
        }
    }

    private fun validateChapterRange(chapter: BookParser.ChapterInfo, pageCount: Int) {
        require(chapter.startPos >= 0 && chapter.endPos > chapter.startPos && chapter.endPos <= pageCount) {
            "Invalid PDF chapter page range at index ${chapter.index}"
        }
    }

    private fun ensureDeadline(startedAt: Long) {
        val elapsedMillis = (nanoTime() - startedAt) / 1_000_000
        if (elapsedMillis > limits.parseDeadlineMillis) {
            throw EbookParseException(
                EbookParseFailureReason.PARSER_LIMIT_EXCEEDED,
                "PDF parsing exceeded configured deadline"
            )
        }
    }

    private data class OutlineEntry(
        val pageIndex: Int,
        val title: String?,
        val level: Int,
        val order: Int
    )
}
