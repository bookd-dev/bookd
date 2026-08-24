package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.pdf.PdfParseLimits
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PdfMetadataExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given PDF document information when metadata extracted then existing fields and tags are populated`() {
        val file = createPdf("metadata.pdf") { document ->
            document.documentInformation.apply {
                title = "PDF Title"
                author = "PDF Author"
                subject = "Technology, Kotlin"
                keywords = "backend;ebook"
                setCustomMetadataValue("Publisher", "Bookd Press")
                setCustomMetadataValue("ISBN", "978-1-4028-9462-6")
            }
        }

        val metadata = PdfMetadataExtractor(coverStorage = LegacyCoverStorage(tempDir.toFile())).extractMetadata(file)

        assertNotNull(metadata)
        assertEquals("PDF Title", metadata?.title)
        assertEquals("PDF Author", metadata?.author)
        assertEquals("Bookd Press", metadata?.publisher)
        assertEquals("9781402894626", metadata?.isbn)
        assertTrue(metadata?.tags?.containsAll(listOf("Technology", "Kotlin", "backend", "ebook")) == true)
    }

    @Test
    fun `given blank first page when cover extracted then bounded PNG is persisted`() {
        val file = createPdf("blank.pdf")
        val storage = LegacyCoverStorage(tempDir.resolve("covers").toFile())

        val path = PdfMetadataExtractor(coverStorage = storage).extractCover(file, 42)

        assertNotNull(path)
        assertTrue(tempDir.resolve("covers").resolve(path!!.removePrefix("/covers/" )).toFile().isFile)
    }

    @Test
    fun `given malformed or cover over configured pixel limit when cover extracted then no cover is published`() {
        val malformed = tempDir.resolve("malformed.pdf").toFile().also { it.writeText("%PDF-invalid") }
        val valid = createPdf("oversized.pdf")
        val storage = LegacyCoverStorage(tempDir.resolve("covers").toFile())
        val extractor = PdfMetadataExtractor(PdfParseLimits(maxCoverPixels = 0), storage)

        assertNull(extractor.extractCover(malformed, 1))
        assertNull(extractor.extractCover(valid, 2))
    }

    private fun createPdf(name: String, configure: (PDDocument) -> Unit = {}) =
        tempDir.resolve(name).toFile().also { target ->
            PDDocument().use { document ->
                document.addPage(PDPage())
                configure(document)
                document.save(target)
            }
        }
}
