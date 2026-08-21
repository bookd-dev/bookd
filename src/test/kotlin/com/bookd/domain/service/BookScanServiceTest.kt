package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.BookSource
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

    private fun createBook(id: Int, title: String, filePath: String, author: String? = null): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            format = "epub",
            filePath = filePath,
            fileSize = 1024L
        )
    }
}
