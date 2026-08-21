package com.bookd.domain.service.parser.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubArchiveSecurityTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given normal package xml when reading epub then spine is returned`() {
        val epub = createEpub(
            containerXml = normalContainerXml(),
            opfXml = normalOpfXml()
        )

        val packageInfo = ZipFile(epub.toFile()).use { EpubPackageReader().readPackage(it) }

        requireNotNull(packageInfo)
        assertEquals(listOf("chapter.xhtml"), packageInfo.spineItems)
    }

    @Test
    fun `given doctype with external entity when reading epub then xml is rejected`() {
        val secret = tempDir.resolve("secret.txt")
        Files.writeString(secret, "must-not-be-read")
        val maliciousContainer = """<?xml version="1.0"?>
            <!DOCTYPE container [<!ENTITY xxe SYSTEM "${secret.toUri()}">]>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="&xxe;"/></rootfiles>
            </container>
        """.trimIndent()
        val epub = createEpub(maliciousContainer, normalOpfXml())

        assertThrows(Exception::class.java) {
            ZipFile(epub.toFile()).use { EpubPackageReader().readPackage(it) }
        }
    }

    @Test
    fun `given entry larger than read limit when reading bytes then archive is rejected`() {
        val epub = tempDir.resolve("oversized.epub")
        ZipOutputStream(Files.newOutputStream(epub)).use { output ->
            output.putNextEntry(ZipEntry("large.bin"))
            output.write(byteArrayOf(1, 2, 3, 4, 5))
            output.closeEntry()
        }

        assertThrows(EpubArchiveException::class.java) {
            ZipFile(epub.toFile()).use { zip ->
                EpubArchiveSafety.readBytes(zip, requireNotNull(zip.getEntry("large.bin")), 4)
            }
        }
    }

    private fun createEpub(containerXml: String, opfXml: String): Path {
        val epub = tempDir.resolve("test-${System.nanoTime()}.epub")
        ZipOutputStream(Files.newOutputStream(epub)).use { output ->
            output.writeEntry("META-INF/container.xml", containerXml)
            output.writeEntry("EPUB/content.opf", opfXml)
            output.writeEntry("EPUB/chapter.xhtml", "<html><body><p>content</p></body></html>")
        }
        return epub
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun normalContainerXml(): String = """
        <?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="EPUB/content.opf"/></rootfiles>
        </container>
    """.trimIndent()

    private fun normalOpfXml(): String = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf">
          <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="chapter"/></spine>
        </package>
    """.trimIndent()
}
