package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.BookSource
import com.bookd.domain.service.parser.mobi.MobiTestFixtures
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class BookScanServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given scanned files when importing then existing books are loaded by batch file path query`() {
        val bookRepository = mockk<BookRepository>()
        val bookSourceRepository = mockk<BookSourceRepository>()
        val metadataService = mockk<BookMetadataService>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val service = BookScanService(bookRepository, bookSourceRepository, metadataService, contentService)

        val existingPath = tempDir.resolve("existing.epub").createFile().also { it.writeText("existing") }.toFile().absolutePath
        val newPath = tempDir.resolve("new.epub").createFile().also { it.writeText("new") }.toFile().absolutePath
        val existingBook = createBook(1, "Existing", existingPath, author = "Known")
        val newBook = createBook(2, "New", newPath)

        every { bookSourceRepository.findById(7) } returns BookSource(id = 7, name = "Source", path = tempDir.toString(), enabled = true)
        every { bookRepository.findByFilePaths(any()) } returns mapOf(existingPath to existingBook)
        every {
            bookRepository.create(
                title = "new",
                author = null,
                format = "epub",
                filePath = newPath,
                fileSize = any(),
                sourceId = 7
            )
        } returns newBook
        every { metadataService.extractMetadataAsync(any(), any()) } just runs

        val result = service.scanBookSource(7, fullScan = false)

        assertEquals(2, result.found)
        assertEquals(1, result.imported)
        verify(exactly = 1) { bookRepository.findByFilePaths(match { it.toSet() == setOf(existingPath, newPath) }) }
        verify(exactly = 0) { bookRepository.findByFilePath(any()) }
    }

    @Test
    fun `given valid PDF and MOBI plus spoofed files when scanned then only valid signatures are imported`() {
        val bookRepository = mockk<BookRepository>()
        val bookSourceRepository = mockk<BookSourceRepository>()
        val metadataService = mockk<BookMetadataService>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val service = BookScanService(bookRepository, bookSourceRepository, metadataService, contentService)
        val pdf = tempDir.resolve("valid.pdf").also { it.writeBytes("%PDF-1.7\n".toByteArray()) }.toFile()
        val mobi = MobiTestFixtures.createMobi(tempDir.resolve("valid.mobi")).toFile()
        tempDir.resolve("spoofed.pdf").writeText("not a pdf")
        tempDir.resolve("spoofed.mobi").writeText("not a mobi")

        every { bookSourceRepository.findById(8) } returns BookSource(id = 8, name = "Source", path = tempDir.toString(), enabled = true)
        every { bookRepository.findByFilePaths(any()) } returns emptyMap()
        every { bookRepository.create(any(), null, any(), any(), any(), 8) } answers {
            val format = arg<String>(2)
            createBook(if (format == "pdf") 10 else 11, firstArg(), arg(3), format = format)
        }
        every { metadataService.extractMetadataAsync(any(), any()) } just runs

        val result = service.scanBookSource(8)

        assertEquals(2, result.found)
        assertEquals(2, result.imported)
        verify(exactly = 1) { bookRepository.findByFilePaths(match { it.toSet() == setOf(pdf.absolutePath, mobi.absolutePath) }) }
        verify(exactly = 1) { bookRepository.create("valid", null, "pdf", pdf.absolutePath, pdf.length(), 8) }
        verify(exactly = 1) { bookRepository.create("valid", null, "mobi", mobi.absolutePath, mobi.length(), 8) }
    }

    private fun createBook(
        id: Int,
        title: String,
        filePath: String,
        author: String? = null,
        format: String = "epub"
    ): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            format = format,
            filePath = filePath,
            fileSize = 1024L
        )
    }
}
