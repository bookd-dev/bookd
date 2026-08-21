package com.bookd.domain.service.parser.epub

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class EpubArchiveException(message: String) : IOException(message)

/**
 * EPUB 是 ZIP 容器。所有读取都必须有上限，避免恶意条目或压缩炸弹耗尽内存。
 */
internal object EpubArchiveSafety {
    const val MAX_XML_BYTES = 2 * 1024 * 1024
    const val MAX_CHAPTER_BYTES = 8 * 1024 * 1024
    const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    const val MAX_TOTAL_IMAGE_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRY_COUNT = 10_000
    private const val MAX_DECLARED_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024

    fun validate(zipFile: ZipFile) {
        var count = 0
        var declaredSize = 0L
        zipFile.entries().asSequence().forEach { entry ->
            count++
            if (count > MAX_ENTRY_COUNT) {
                throw EpubArchiveException("EPUB contains too many entries")
            }
            if (entry.size > 0) {
                declaredSize = safeAdd(declaredSize, entry.size)
                if (declaredSize > MAX_DECLARED_UNCOMPRESSED_BYTES) {
                    throw EpubArchiveException("EPUB uncompressed size exceeds limit")
                }
            }
        }
    }

    fun readText(zipFile: ZipFile, entry: ZipEntry, maxBytes: Int): String =
        readBytes(zipFile, entry, maxBytes).toString(StandardCharsets.UTF_8)

    fun readBytes(zipFile: ZipFile, entry: ZipEntry, maxBytes: Int): ByteArray {
        if (entry.size > maxBytes) {
            throw EpubArchiveException("EPUB entry exceeds limit: ${entry.name}")
        }

        return zipFile.getInputStream(entry).use { input ->
            val initialSize = entry.size.takeIf { it in 1..maxBytes.toLong() }?.toInt() ?: 8 * 1024
            val output = ByteArrayOutputStream(initialSize)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw EpubArchiveException("EPUB entry exceeds limit while reading: ${entry.name}")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > Long.MAX_VALUE - left) {
            throw EpubArchiveException("EPUB size metadata overflow")
        }
        return left + right
    }
}
