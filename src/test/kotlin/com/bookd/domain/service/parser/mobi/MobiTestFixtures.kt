package com.bookd.domain.service.parser.mobi

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object MobiTestFixtures {
    fun createMobi(
        path: Path,
        encoding: Int = 65001,
        encryption: Int = 0,
        malformedSecondOffset: Boolean = false
    ): Path {
        val record0 = ByteArray(700)
        putU16(record0, 0, 2)
        putU32(record0, 4, 128)
        putU16(record0, 8, 1)
        putU16(record0, 10, 4096)
        putU16(record0, 12, encryption)
        "MOBI".toByteArray().copyInto(record0, 16)
        putU32(record0, 20, 232)
        putU32(record0, 28, encoding.toLong())
        putU32(record0, 36, 6)
        val title = "Fallback Title".toByteArray()
        putU32(record0, 84, 500)
        putU32(record0, 88, title.size.toLong())
        title.copyInto(record0, 500)
        putU32(record0, 108, 1)
        putU32(record0, 128, 0x40)

        val exthRecords = listOf(
            503 to "EXTH Title".toByteArray(),
            100 to "Author One".toByteArray(),
            101 to "Publisher".toByteArray(),
            103 to "<p>Description</p>".toByteArray(),
            104 to "9781402894626".toByteArray(),
            105 to "fiction".toByteArray(),
            201 to byteArrayOf(0, 0, 0, 0)
        )
        val exthLength = 12 + exthRecords.sumOf { 8 + it.second.size }
        val exthStart = 248
        "EXTH".toByteArray().copyInto(record0, exthStart)
        putU32(record0, exthStart + 4, exthLength.toLong())
        putU32(record0, exthStart + 8, exthRecords.size.toLong())
        var cursor = exthStart + 12
        exthRecords.forEach { (type, value) ->
            putU32(record0, cursor, type.toLong())
            putU32(record0, cursor + 4, (8 + value.size).toLong())
            value.copyInto(record0, cursor + 8)
            cursor += 8 + value.size
        }

        val cover = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val record0Offset = 94
        val record1Offset = if (malformedSecondOffset) record0Offset else record0Offset + record0.size
        val bytes = ByteArray(record0Offset + record0.size + cover.size)
        "BOOKMOBI".toByteArray().copyInto(bytes, 60)
        putU16(bytes, 76, 2)
        putU32(bytes, 78, record0Offset.toLong())
        putU32(bytes, 86, record1Offset.toLong())
        record0.copyInto(bytes, record0Offset)
        cover.copyInto(bytes, record0Offset + record0.size)
        Files.write(path, bytes)
        return path
    }

    fun createEpub(path: Path): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            output.writeEntry("mimetype", "application/epub+zip")
            output.writeEntry("META-INF/container.xml", """
                <?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="EPUB/content.opf"/></rootfiles>
                </container>
            """.trimIndent())
            output.writeEntry("EPUB/content.opf", """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Normalized</dc:title></metadata>
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="image" href="image.png" media-type="image/png"/>
                  </manifest>
                  <spine><itemref idref="chapter"/></spine>
                </package>
            """.trimIndent())
            output.writeEntry("EPUB/chapter.xhtml", "<html><body><h1 id=\"heading\">Chapter</h1><p><a href=\"#heading\">Linked text</a></p><img src=\"image.png\"/></body></html>")
            output.writeBytes("EPUB/image.png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        }
        return path
    }

    private fun ZipOutputStream.writeEntry(name: String, value: String) = writeBytes(name, value.toByteArray())
    private fun ZipOutputStream.writeBytes(name: String, value: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(value)
        closeEntry()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (24 - index * 8)).toByte() }
    }
}
