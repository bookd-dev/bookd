package com.bookd.domain.service.metadata

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.InputStream

class LegacyCoverStorageTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `given existing legacy cover when saving replacement then target contains complete replacement`() {
        val storage = LegacyCoverStorage(tempDir)
        val targetFile = File(tempDir, "book_7.jpg")
        targetFile.writeBytes(byteArrayOf(1, 2, 3))
        val replacement = byteArrayOf(9, 8, 7, 6)

        val path = storage.saveCover(7, "JPG", replacement.inputStream())

        assertEquals("/covers/book_7.jpg", path)
        assertArrayEquals(replacement, targetFile.readBytes())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `given replacement write fails when saving cover then existing cover remains and temp file is cleaned`() {
        val storage = LegacyCoverStorage(tempDir)
        val targetFile = File(tempDir, "book_8.png")
        val existing = byteArrayOf(4, 5, 6)
        targetFile.writeBytes(existing)

        assertThrows(IOException::class.java) {
            storage.saveCover(8, "png", FailingInputStream(byteArrayOf(1, 2)))
        }

        assertArrayEquals(existing, targetFile.readBytes())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private class FailingInputStream(
        private val firstChunk: ByteArray
    ) : InputStream() {
        private var firstRead = true

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!firstRead) {
                throw IOException("forced read failure")
            }

            firstRead = false
            val count = minOf(firstChunk.size, length)
            firstChunk.copyInto(buffer, offset, endIndex = count)
            return count
        }

        override fun read(): Int {
            if (!firstRead) {
                throw IOException("forced read failure")
            }

            firstRead = false
            return firstChunk.firstOrNull()?.toInt() ?: -1
        }
    }
}
