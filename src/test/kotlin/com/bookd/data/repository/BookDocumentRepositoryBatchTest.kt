package com.bookd.data.repository

import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.BookSources
import com.bookd.data.entity.Books
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class BookDocumentRepositoryBatchTest {

    @Test
    fun `given parsed documents when saving in batch then documents contents stats and resources are persisted`() {
        Database.connect(
            url = "jdbc:h2:mem:book_document_batch_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(BookSources, Books, BookDocuments, DocumentContents, DocumentResources)
        }

        val book = BookRepository().create(
            title = "Batch Book",
            author = null,
            format = "epub",
            filePath = "/tmp/batch-book.epub",
            fileSize = 1024L
        )
        val repository = BookDocumentRepository()

        val documentsByIndex = repository.replaceDocumentsForBook(
            bookId = book.id,
            documents = listOf(
                BookDocumentDraft(index = 0, href = "chapter1.xhtml", inToc = true, title = "Chapter 1", level = 0),
                BookDocumentDraft(index = 1, href = "chapter2.xhtml", inToc = false, title = "Chapter 2", level = 0)
            )
        )
        val firstDocument = documentsByIndex[0]!!

        repository.replaceDocumentContentsAndStats(
            listOf(
                DocumentContentDraft(
                    documentId = firstDocument.id,
                    elements = listOf(ContentElement.Paragraph(listOf(TextSpan("Hello")))),
                    wordCount = 1,
                    imageCount = 1
                )
            )
        )
        repository.saveResources(
            listOf(
                DocumentResourceDraft(
                    bookId = book.id,
                    path = "images/pic.jpg",
                    storedPath = "${book.id}/pic.jpg",
                    mediaType = "image/jpeg",
                    size = 128L,
                    width = 640,
                    height = 320
                )
            )
        )

        val content = repository.getDocumentContent(firstDocument.id)
        val stats = runBlocking { repository.findStatsByBookIds(listOf(book.id))[book.id] }
        val resources = runBlocking {
            repository.findResourcesByBookIdAndPaths(book.id, setOf("images/pic.jpg"))
        }

        assertNotNull(content)
        assertEquals(2, repository.findByBookId(book.id).size)
        assertEquals(1, stats?.chapterCount)
        assertEquals(1, stats?.totalWordCount)
        assertEquals(1, stats?.totalImageCount)
        assertEquals(640, resources["images/pic.jpg"]?.width)
        assertEquals(320, resources["images/pic.jpg"]?.height)
    }
}
