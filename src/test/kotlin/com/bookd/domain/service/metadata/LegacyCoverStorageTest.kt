package com.bookd.domain.service.metadata

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO

class LegacyCoverStorageTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `given existing legacy cover when saving replacement then target contains complete replacement`() {
        val storage = LegacyCoverStorage(tempDir)
        val targetFile = File(tempDir, "book_7.png")
        targetFile.writeBytes(byteArrayOf(1, 2, 3))
        val replacement = pngBytes()

        val path = storage.saveCover(7, "PNG", replacement.inputStream())

        assertEquals("/covers/book_7.png", path)
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

    @Test
    fun `given invalid cover bytes when saving replacement then existing cover remains and temp file is cleaned`() {
        val storage = LegacyCoverStorage(tempDir)
        val targetFile = File(tempDir, "book_9.jpg")
        val existing = pngBytes()
        targetFile.writeBytes(existing)

        assertThrows(IOException::class.java) {
            storage.saveCover(9, "jpg", "TEST2".byteInputStream())
        }

        assertArrayEquals(existing, targetFile.readBytes())
        assertTrue(tempDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private fun pngBytes(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, Color.WHITE.rgb)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
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
