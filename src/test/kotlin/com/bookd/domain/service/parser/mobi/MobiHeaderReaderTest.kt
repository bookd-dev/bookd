package com.bookd.domain.service.parser.mobi

import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MobiHeaderReaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given MOBI6 EXTH header when read then metadata encryption and cover mapping are returned`() {
        val file = MobiTestFixtures.createMobi(tempDir.resolve("book.mobi")).toFile()

        val header = MobiHeaderReader().read(file)

        assertEquals(6, header.version)
        assertEquals(65001, header.encoding)
        assertEquals(0, header.encryption)
        assertEquals("EXTH Title", header.title)
        assertEquals(listOf("Author One"), header.authors)
        assertEquals("Publisher", header.publisher)
        assertEquals("9781402894626", header.isbn)
        assertEquals(listOf("fiction"), header.subjects)
        assertEquals("jpg", header.cover?.extension)
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()), header.cover?.bytes)
    }

    @Test
    fun `given malformed record offsets when read then invalid signature reason is returned`() {
        val file = MobiTestFixtures.createMobi(tempDir.resolve("malformed.mobi"), malformedSecondOffset = true).toFile()

        val error = assertThrows(EbookParseException::class.java) { MobiHeaderReader().read(file) }

        assertEquals(EbookParseFailureReason.INVALID_SIGNATURE, error.reason)
    }

    @Test
    fun `given unsupported encoding when read then invalid signature reason is returned`() {
        val file = MobiTestFixtures.createMobi(tempDir.resolve("encoding.mobi"), encoding = 9999).toFile()

        val error = assertThrows(EbookParseException::class.java) { MobiHeaderReader().read(file) }

        assertEquals(EbookParseFailureReason.INVALID_SIGNATURE, error.reason)
    }

    @Test
    fun `given record count over configured limit when read then stable limit reason is returned`() {
        val file = MobiTestFixtures.createMobi(tempDir.resolve("records.mobi")).toFile()

        val error = assertThrows(EbookParseException::class.java) {
            MobiHeaderReader(MobiParseLimits(maxRecords = 1)).read(file)
        }

        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, error.reason)
    }
}
