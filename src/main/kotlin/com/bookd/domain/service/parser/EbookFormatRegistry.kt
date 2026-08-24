package com.bookd.domain.service.parser

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

enum class EbookFormat(val extension: String) {
    EPUB("epub"),
    TXT("txt"),
    PDF("pdf"),
    MOBI("mobi")
}

/**
 * 书籍格式的唯一注册点。复杂容器格式必须同时通过扩展名和文件签名校验。
 */
object EbookFormatRegistry {
    private const val PDF_HEADER_LIMIT = 1024
    private const val MOBI_HEADER_SIZE = 78
    private const val MOBI_RECORD_ENTRY_SIZE = 8
    private const val MAX_MOBI_RECORDS = 100_000
    private val pdfMagic = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val mobiMagic = "BOOKMOBI".toByteArray(Charsets.US_ASCII)

    fun fromExtension(extension: String): EbookFormat? = EbookFormat.entries
        .firstOrNull { it.extension == extension.lowercase() }

    fun detect(file: File): EbookFormat? {
        val format = fromExtension(file.extension) ?: return null
        if (!file.isFile) return null
        return format.takeIf { matchesSignature(file, format) }
    }

    fun matchesSignature(file: File, format: EbookFormat): Boolean = when (format) {
        EbookFormat.PDF -> hasPdfSignature(file)
        EbookFormat.MOBI -> hasMobiSignature(file)
        EbookFormat.EPUB, EbookFormat.TXT -> true
    }

    private fun hasPdfSignature(file: File): Boolean {
        if (file.length() < pdfMagic.size) return false
        val bytes = readPrefix(file, PDF_HEADER_LIMIT)
        return bytes.indexOf(pdfMagic) in 0..1019
    }

    private fun hasMobiSignature(file: File): Boolean {
        if (file.length() < MOBI_HEADER_SIZE) return false
        val bytes = readPrefix(file, MOBI_HEADER_SIZE)
        if (!bytes.copyOfRange(60, 68).contentEquals(mobiMagic)) return false
        val recordCount = ((bytes[76].toInt() and 0xff) shl 8) or (bytes[77].toInt() and 0xff)
        if (recordCount !in 1..MAX_MOBI_RECORDS) return false
        val tableEnd = MOBI_HEADER_SIZE.toLong() + recordCount.toLong() * MOBI_RECORD_ENTRY_SIZE
        if (tableEnd > file.length()) return false

        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                input.seek(MOBI_HEADER_SIZE.toLong())
                var previous = -1L
                repeat(recordCount) {
                    val offset = input.readInt().toLong() and 0xffffffffL
                    input.skipBytes(4)
                    if (offset < tableEnd || offset >= file.length() || offset <= previous) return@use false
                    previous = offset
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun readPrefix(file: File, limit: Int): ByteArray {
        val size = minOf(file.length(), limit.toLong()).toInt()
        if (size <= 0) return ByteArray(0)
        val bytes = ByteArray(size)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count < 0) break
                offset += count
            }
            return if (offset == bytes.size) bytes else bytes.copyOf(offset)
        }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        for (index in 0..size - needle.size) {
            var matches = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }
}
