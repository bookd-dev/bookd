package com.bookd.domain.service.parser

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** 统一处理常见 TXT 编码，并拒绝把超大文件一次性载入堆内存。 */
internal object TxtFileDecoder {
    private const val MAX_TXT_BYTES = 128 * 1024 * 1024L
    private val gb18030 = Charset.forName("GB18030")

    fun read(file: File): String {
        val length = file.length()
        if (length > MAX_TXT_BYTES) {
            throw IOException("TXT file exceeds ${MAX_TXT_BYTES / 1024 / 1024} MiB limit")
        }

        val bytes = file.readBytes()
        return when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> decode(bytes, 3, StandardCharsets.UTF_8)
            bytes.startsWith(0xFF, 0xFE) -> decode(bytes, 2, StandardCharsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> decode(bytes, 2, StandardCharsets.UTF_16BE)
            else -> decodeOrNull(bytes, StandardCharsets.UTF_8)
                ?: decode(bytes, 0, gb18030)
        }.removePrefix("\uFEFF")
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index].toInt() and 0xFF == prefix[index] }

    private fun decodeOrNull(bytes: ByteArray, charset: Charset): String? = try {
        decode(bytes, 0, charset)
    } catch (_: CharacterCodingException) {
        null
    }

    private fun decode(bytes: ByteArray, offset: Int, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    }
}
