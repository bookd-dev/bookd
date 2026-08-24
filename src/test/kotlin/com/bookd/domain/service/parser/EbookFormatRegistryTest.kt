package com.bookd.domain.service.parser

import com.bookd.domain.service.parser.mobi.MobiTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeBytes

class EbookFormatRegistryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given valid PDF and MOBI signatures when detected then formats are returned`() {
        val pdf = tempDir.resolve("valid.pdf").also { it.writeBytes("%PDF-1.7\n".toByteArray()) }.toFile()
        val mobi = MobiTestFixtures.createMobi(tempDir.resolve("valid.mobi")).toFile()

        assertEquals(EbookFormat.PDF, EbookFormatRegistry.detect(pdf))
        assertEquals(EbookFormat.MOBI, EbookFormatRegistry.detect(mobi))
    }

    @Test
    fun `given truncated or malformed containers when detected then formats are rejected`() {
        val truncatedPdf = tempDir.resolve("truncated.pdf").also { it.writeBytes("%PD".toByteArray()) }.toFile()
        val truncatedMobi = tempDir.resolve("truncated.mobi").also { it.writeBytes(ByteArray(67)) }.toFile()
        val malformedMobi = tempDir.resolve("malformed.mobi").also { it.writeBytes(ByteArray(78)) }.toFile()

        assertNull(EbookFormatRegistry.detect(truncatedPdf))
        assertNull(EbookFormatRegistry.detect(truncatedMobi))
        assertNull(EbookFormatRegistry.detect(malformedMobi))
    }

    @Test
    fun `given spoofed extensions when signatures disagree then files are rejected`() {
        val fakePdf = tempDir.resolve("spoofed.pdf").also { it.writeBytes("plain text".toByteArray()) }.toFile()
        val fakeMobi = tempDir.resolve("spoofed.mobi").also { it.writeBytes("%PDF-1.7".toByteArray()) }.toFile()

        assertFalse(EbookFormatRegistry.matchesSignature(fakePdf, EbookFormat.PDF))
        assertFalse(EbookFormatRegistry.matchesSignature(fakeMobi, EbookFormat.MOBI))
        assertNull(EbookFormatRegistry.detect(fakePdf))
        assertNull(EbookFormatRegistry.detect(fakeMobi))
        assertTrue(EbookFormatRegistry.fromExtension("EPUB") == EbookFormat.EPUB)
    }
}
