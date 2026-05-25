package com.bookd.data.repository

import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.BookSources
import com.bookd.data.entity.Books
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ImageDimensionMigrationRepositoryTest {

    @Test
    fun `given missing dimensions when repository migrates then candidates and updates use database boundary`() = runBlocking {
        Database.connect(
            url = "jdbc:h2:mem:image_dimension_migration_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(BookSources, Books, BookDocuments, DocumentContents, DocumentResources)
        }
        val bookRepository = BookRepository()
        val documentRepository = BookDocumentRepository()
        val repository = ImageDimensionMigrationRepository()
        val book = bookRepository.create(
            title = "Migration Book",
            author = null,
            format = "epub",
            filePath = "/tmp/migration-book.epub",
            fileSize = 1024L
        )
        bookRepository.updateCoverPath(book.id, "/book_images/covers/cover.jpg")
        documentRepository.saveResources(
            listOf(
                DocumentResourceDraft(
                    bookId = book.id,
                    path = "images/pic.jpg",
                    storedPath = "${book.id}/pic.jpg",
                    mediaType = "image/jpeg",
                    size = 128L,
                    width = null,
                    height = null
                )
            )
        )

        val resourceCandidates = repository.findResourcesMissingDimensions()
        val coverCandidates = repository.findCoversMissingDimensions()

        assertEquals(listOf("${book.id}/pic.jpg"), resourceCandidates.map { it.storedPath })
        assertEquals(listOf("/book_images/covers/cover.jpg"), coverCandidates.map { it.coverPath })

        repository.updateResourceDimensions(
            listOf(ResourceDimensionUpdate(resourceCandidates.single().id, width = 640, height = 320))
        )
        repository.updateCoverDimensions(
            listOf(CoverDimensionUpdate(book.id, width = 300, height = 500))
        )

        val resource = documentRepository.findResourcesByBookIdAndPaths(book.id, setOf("images/pic.jpg"))["images/pic.jpg"]
        val updatedBook = bookRepository.findById(book.id)
        assertEquals(640, resource?.width)
        assertEquals(320, resource?.height)
        assertEquals(300, updatedBook?.coverWidth)
        assertEquals(500, updatedBook?.coverHeight)
    }
}
