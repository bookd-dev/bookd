package com.bookd.domain.service.parser.mobi

import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

class MobiHeaderReader(private val limits: MobiParseLimits = MobiParseLimits()) {
    fun read(file: File): MobiHeader {
        if (!file.isFile || file.length() < PDB_HEADER_BYTES) invalid("MOBI header is truncated")
        if (file.length() > limits.maxFileBytes) limit("MOBI source exceeds configured size limit")

        RandomAccessFile(file, "r").use { input ->
            val pdb = ByteArray(PDB_HEADER_BYTES)
            input.readFully(pdb)
            if (!pdb.copyOfRange(60, 68).contentEquals(BOOK_MOBI)) invalid("MOBI signature is invalid")
            val recordCount = u16(pdb, 76)
            if (recordCount <= 0 || recordCount > limits.maxRecords) limit("MOBI record count is invalid")
            val tableBytes = recordCount.toLong() * RECORD_ENTRY_BYTES
            if (PDB_HEADER_BYTES + tableBytes > file.length()) invalid("MOBI record table is truncated")

            val table = ByteArray(tableBytes.toInt())
            input.readFully(table)
            val offsets = IntArray(recordCount) { index -> u32(table, index * RECORD_ENTRY_BYTES).toBoundedInt() }
            validateOffsets(offsets, file.length())
            val record0 = readRecord(input, offsets, 0, file.length(), limits.maxHeaderRecordBytes)
            if (record0.size < MIN_RECORD_ZERO_BYTES || !record0.copyOfRange(16, 20).contentEquals(MOBI)) {
                invalid("MOBI record zero is malformed")
            }

            val compression = u16(record0, 0)
            val textLength = u32(record0, 4)
            val textRecordCount = u16(record0, 8)
            if (textRecordCount > recordCount - 1) invalid("MOBI text record count exceeds the record table")
            val recordSize = u16(record0, 10)
            val encryption = u16(record0, 12)
            val mobiHeaderLength = u32(record0, 20).toBoundedInt()
            if (mobiHeaderLength < MIN_MOBI_HEADER_BYTES || 16L + mobiHeaderLength > record0.size) {
                invalid("MOBI header length is invalid")
            }
            val encodingCode = u32(record0, 28).toInt()
            val charset = when (encodingCode) {
                65001 -> Charsets.UTF_8
                1252 -> Charset.forName("windows-1252")
                else -> invalid("MOBI text encoding is unsupported")
            }
            val version = u32(record0, 36).toInt()
            val title = readFullName(record0, charset)
            val firstImageIndex = u32(record0, 108).takeUnless { it == 0xffffffffL }?.toBoundedInt()
            val exth = readExth(record0, mobiHeaderLength, charset)
            val coverRecordIndex = exth.firstValue(201)?.let { coverOffset ->
                firstImageIndex?.let { first ->
                    runCatching { Math.addExact(first, coverOffset.toBoundedInt()) }
                        .getOrElse { invalid("MOBI cover record index overflows") }
                }
            }
            val cover = coverRecordIndex?.takeIf { it in offsets.indices }
                ?.let { readRecord(input, offsets, it, file.length(), limits.maxImageBytes) }
                ?.let(::detectImage)

            return MobiHeader(
                compression = compression,
                textLength = textLength,
                textRecordCount = textRecordCount,
                recordSize = recordSize,
                encryption = encryption,
                encoding = encodingCode,
                version = version,
                recordCount = recordCount,
                title = exth.firstText(503) ?: title,
                authors = exth.texts(100),
                publisher = exth.firstText(101),
                description = exth.firstText(103),
                isbn = exth.firstText(104),
                subjects = exth.texts(105),
                publishDate = exth.firstText(106),
                cover = cover
            )
        }
    }

    private fun readFullName(record0: ByteArray, charset: Charset): String? {
        if (record0.size < 92) return null
        val offset = u32(record0, 84).toBoundedInt()
        val length = u32(record0, 88).toBoundedInt()
        if (length == 0) return null
        if (offset < 0 || length < 0 || offset.toLong() + length > record0.size) invalid("MOBI full-name range is invalid")
        return record0.copyOfRange(offset, offset + length).toString(charset).clean()
    }

    private fun readExth(record0: ByteArray, mobiHeaderLength: Int, charset: Charset): ExthValues {
        if (record0.size < 132 || u32(record0, 128).toInt() and 0x40 == 0) return ExthValues(emptyMap(), charset)
        val start = 16 + mobiHeaderLength
        if (start < 0 || start + 12 > record0.size || !record0.copyOfRange(start, start + 4).contentEquals(EXTH)) {
            invalid("MOBI EXTH header is malformed")
        }
        val length = u32(record0, start + 4).toBoundedInt()
        val count = u32(record0, start + 8).toBoundedInt()
        if (length < 12 || start.toLong() + length > record0.size || count > limits.maxRecords) invalid("MOBI EXTH bounds are invalid")
        var cursor = start + 12
        val values = linkedMapOf<Int, MutableList<ByteArray>>()
        repeat(count) {
            if (cursor + 8 > start + length) invalid("MOBI EXTH record is truncated")
            val type = u32(record0, cursor).toInt()
            val recordLength = u32(record0, cursor + 4).toBoundedInt()
            if (recordLength < 8 || cursor.toLong() + recordLength > start + length) invalid("MOBI EXTH record length is invalid")
            values.getOrPut(type, ::mutableListOf) += record0.copyOfRange(cursor + 8, cursor + recordLength)
            cursor += recordLength
        }
        return ExthValues(values, charset)
    }

    private fun validateOffsets(offsets: IntArray, fileLength: Long) {
        offsets.forEachIndexed { index, offset ->
            if (offset < PDB_HEADER_BYTES || offset.toLong() >= fileLength) invalid("MOBI record offset is outside the file")
            if (index > 0 && offset <= offsets[index - 1]) invalid("MOBI record offsets are not increasing")
        }
    }

    private fun readRecord(
        input: RandomAccessFile,
        offsets: IntArray,
        index: Int,
        fileLength: Long,
        maxBytes: Int
    ): ByteArray {
        val start = offsets[index].toLong()
        val end = offsets.getOrNull(index + 1)?.toLong() ?: fileLength
        val length = end - start
        if (length <= 0 || length > maxBytes) limit("MOBI record exceeds configured size limit")
        return ByteArray(length.toInt()).also { bytes -> input.seek(start); input.readFully(bytes) }
    }

    private fun detectImage(bytes: ByteArray): MobiCover? = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> MobiCover("jpg", bytes)
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG) -> MobiCover("png", bytes)
        bytes.size >= 6 && bytes.copyOfRange(0, 3).contentEquals(GIF) -> MobiCover("gif", bytes)
        else -> null
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) invalid("MOBI integer is outside record bounds")
        return (bytes[offset].toInt() and 0xff shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) invalid("MOBI integer is outside record bounds")
        return (0 until 4).fold(0L) { value, index -> (value shl 8) or (bytes[offset + index].toLong() and 0xff) }
    }

    private fun Long.toBoundedInt(): Int = takeIf { it in 0..Int.MAX_VALUE }?.toInt()
        ?: invalid("MOBI integer exceeds supported bounds")

    private fun String.clean(): String? = trim().trimEnd('\u0000').takeIf(String::isNotBlank)
    private fun invalid(message: String): Nothing = throw EbookParseException(EbookParseFailureReason.INVALID_SIGNATURE, message)
    private fun limit(message: String): Nothing = throw EbookParseException(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, message)

    private data class ExthValues(val values: Map<Int, List<ByteArray>>, val charset: Charset) {
        fun texts(type: Int): List<String> = values[type].orEmpty().mapNotNull { it.toString(charset).trim().takeIf(String::isNotBlank) }
        fun firstText(type: Int): String? = texts(type).firstOrNull()
        fun firstValue(type: Int): Long? = values[type]?.firstOrNull()?.takeIf { it.size == 4 }
            ?.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xff) }
    }

    private companion object {
        const val PDB_HEADER_BYTES = 78
        const val RECORD_ENTRY_BYTES = 8
        const val MIN_RECORD_ZERO_BYTES = 132
        const val MIN_MOBI_HEADER_BYTES = 116
        val BOOK_MOBI = "BOOKMOBI".toByteArray(Charsets.US_ASCII)
        val MOBI = "MOBI".toByteArray(Charsets.US_ASCII)
        val EXTH = "EXTH".toByteArray(Charsets.US_ASCII)
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val GIF = "GIF".toByteArray(Charsets.US_ASCII)
    }
}

data class MobiHeader(
    val compression: Int,
    val textLength: Long,
    val textRecordCount: Int,
    val recordSize: Int,
    val encryption: Int,
    val encoding: Int,
    val version: Int,
    val recordCount: Int,
    val title: String?,
    val authors: List<String>,
    val publisher: String?,
    val description: String?,
    val isbn: String?,
    val subjects: List<String>,
    val publishDate: String?,
    val cover: MobiCover?
)

data class MobiCover(val extension: String, val bytes: ByteArray)
