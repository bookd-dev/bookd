package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.parser.pdf.PdfParseLimits
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PdfBookParserTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given PDF without outline when structure parsed then deterministic twenty page ranges are returned`() = runBlocking {
        val file = createPdf("fallback.pdf", 45) { "Page ${it + 1}" }
        val chapters = PdfBookParser().parseStructure(file)!!.chapters

        assertEquals(listOf(0 to 20, 20 to 40, 40 to 45), chapters.map { it.startPos to it.endPos })
        assertEquals(
            listOf("pdf:pages:000001-000020", "pdf:pages:000021-000040", "pdf:pages:000041-000045"),
            chapters.map { it.href }
        )
    }

    @Test
    fun `given outline with duplicate destinations when structure parsed then non-overlapping ranges are returned`() = runBlocking {
        val file = createPdf("outline.pdf", 6, outlines = listOf("First" to 1, "Duplicate" to 1, "Third" to 4)) {
            "Text ${it + 1}"
        }

        val chapters = PdfBookParser().parseStructure(file)!!.chapters

        assertEquals(listOf(0 to 1, 1 to 4, 4 to 6), chapters.map { it.startPos to it.endPos })
        assertEquals(listOf(false, true, true), chapters.map { it.inToc })
        assertEquals(listOf("第 1-1 页", "First", "Third"), chapters.map { it.title })
    }

    @Test
    fun `given text and blank PDF pages when content parsed twice then text and anchors are stable`() = runBlocking {
        val file = createPdf("partial.pdf", 3) { if (it == 1) null else "Stable page ${it + 1}" }
        val parser = PdfBookParser()
        val chapter = parser.parseStructure(file)!!.chapters.single()

        val first = parser.parseChapterContent(file, chapter)
        val second = parser.parseChapterContent(file, chapter)

        assertEquals(first.map { it.anchorId }, second.map { it.anchorId })
        assertEquals(3, first.count { it is ContentElement.Divider })
        assertTrue(first.filterIsInstance<ContentElement.Paragraph>().any { it.spans.single().text == "Stable page 1" })
        assertTrue(first.filterIsInstance<ContentElement.Paragraph>().any { it.spans.single().text == "Stable page 3" })
    }

    @Test
    fun `given image only PDF when content parsed then missing text layer reason is returned`() = runBlocking {
        val file = createPdf("image-only.pdf", 2) { null }
        val parser = PdfBookParser()
        val chapter = parser.parseStructure(file)!!.chapters.single()

        val error = assertThrows(EbookParseException::class.java) {
            runBlocking { parser.parseChapterContent(file, chapter) }
        }

        assertEquals(EbookParseFailureReason.PDF_IMAGE_ONLY, error.reason)
    }

    @Test
    fun `given page and text limits when exceeded then stable limit reason is returned`() = runBlocking {
        val pageLimited = createPdf("too-many.pdf", 2) { "text" }
        val pageError = assertThrows(EbookParseException::class.java) {
            runBlocking { PdfBookParser(PdfParseLimits(maxPages = 1)).parseStructure(pageLimited) }
        }
        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, pageError.reason)

        val textLimited = createPdf("too-much-text.pdf", 1) { "large text" }
        val parser = PdfBookParser(PdfParseLimits(maxPageTextBytes = 2))
        val chapter = parser.parseStructure(textLimited)!!.chapters.single()
        val textError = assertThrows(EbookParseException::class.java) {
            runBlocking { parser.parseChapterContent(textLimited, chapter) }
        }
        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, textError.reason)

        val fileError = assertThrows(EbookParseException::class.java) {
            runBlocking { PdfBookParser(PdfParseLimits(maxFileBytes = 1)).parseStructure(textLimited) }
        }
        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, fileError.reason)
    }

    @Test
    fun `given PDF parsing deadline when page extraction starts too late then stable limit reason is returned`() = runBlocking {
        val file = createPdf("deadline.pdf", 1) { "deadline text" }
        var invocation = 0
        val parser = PdfBookParser(
            limits = PdfParseLimits(parseDeadlineMillis = 1),
            nanoTime = { if (invocation++ == 0) 0 else 2_000_000 }
        )
        val chapter = parser.parseStructure(file)!!.chapters.single()

        val error = assertThrows(EbookParseException::class.java) {
            runBlocking { parser.parseChapterContent(file, chapter) }
        }

        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, error.reason)
    }

    @Test
    fun `given malformed PDF when parsed then parser rejects document`() {
        val file = tempDir.resolve("malformed.pdf").also { it.toFile().writeText("%PDF-not-a-document") }.toFile()
        assertThrows(Exception::class.java) {
            runBlocking { PdfBookParser().parseStructure(file) }
        }
    }

    @Test
    fun `given password required or extraction prohibited PDF when parsed then protected reason is returned`() {
        val passwordRequired = createPdf("password.pdf", 1) { "secret" }.also { file ->
            protect(file.toPath(), userPassword = "required", canExtract = true)
        }
        val extractionProhibited = createPdf("prohibited.pdf", 1) { "secret" }.also { file ->
            protect(file.toPath(), userPassword = "", canExtract = false)
        }

        listOf(passwordRequired, extractionProhibited).forEach { file ->
            val error = assertThrows(EbookParseException::class.java) {
                runBlocking { PdfBookParser().parseStructure(file) }
            }
            assertEquals(EbookParseFailureReason.PDF_PROTECTED, error.reason)
        }
    }

    @Test
    fun `given permitted encrypted PDF with empty user password when parsed then text is accepted`() = runBlocking {
        val file = createPdf("permitted.pdf", 1) { "permitted text" }.also { target ->
            protect(target.toPath(), userPassword = "", canExtract = true)
        }

        val parser = PdfBookParser()
        val chapter = parser.parseStructure(file)!!.chapters.single()
        val content = parser.parseChapterContent(file, chapter)

        assertTrue(content.filterIsInstance<ContentElement.Paragraph>().any { it.spans.single().text == "permitted text" })
    }

    private fun createPdf(
        name: String,
        pages: Int,
        outlines: List<Pair<String, Int>> = emptyList(),
        text: (Int) -> String?
    ) = tempDir.resolve(name).toFile().also { target ->
        PDDocument().use { document ->
            repeat(pages) { pageIndex ->
                val page = PDPage()
                document.addPage(page)
                text(pageIndex)?.let { pageText ->
                    PDPageContentStream(document, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                        stream.newLineAtOffset(72f, 720f)
                        stream.showText(pageText)
                        stream.endText()
                    }
                }
            }
            if (outlines.isNotEmpty()) {
                val outline = PDDocumentOutline()
                document.documentCatalog.documentOutline = outline
                outlines.forEach { (title, pageIndex) ->
                    val destination = PDPageFitDestination().apply { page = document.getPage(pageIndex) }
                    outline.addLast(PDOutlineItem().apply {
                        this.title = title
                        this.destination = destination
                    })
                }
            }
            document.save(target)
        }
    }

    private fun protect(path: Path, userPassword: String, canExtract: Boolean) {
        org.apache.pdfbox.Loader.loadPDF(path.toFile()).use { document ->
            val permission = AccessPermission().apply { setCanExtractContent(canExtract) }
            document.protect(StandardProtectionPolicy("owner-password", userPassword, permission))
            document.save(path.toFile())
        }
    }
}
