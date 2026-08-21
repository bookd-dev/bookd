package com.bookd.data.repository

import com.bookd.data.entity.BookSources
import com.bookd.data.entity.Books
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BookRepositorySearchTest {

    private lateinit var bookRepository: BookRepository
    private lateinit var sourceRepository: BookSourceRepository

    @BeforeEach
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:book_search_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction { SchemaUtils.create(BookSources, Books) }
        bookRepository = BookRepository()
        sourceRepository = BookSourceRepository()
    }

    @Test
    fun `given metadata query when searching then title author isbn and publisher are matched`() = runBlocking {
        val source = sourceRepository.createAsync("Main", "/books/main")
        createBook(title = "Dune", author = "Frank Herbert", sourceId = source.id)
        createBook(title = "Foundation", author = "Isaac Asimov", isbn = "9780553293357", sourceId = source.id)
        createBook(title = "The Left Hand of Darkness", author = "Ursula Le Guin", publisher = "Ace Books", sourceId = source.id)

        assertEquals(listOf("Dune"), bookRepository.searchAsync("DUNE", limit = 20, offset = 0).map { it.title })
        assertEquals(listOf("Dune"), bookRepository.searchAsync("herbert", limit = 20, offset = 0).map { it.title })
        assertEquals(listOf("Foundation"), bookRepository.searchAsync("055329", limit = 20, offset = 0).map { it.title })
        assertEquals(
            listOf("The Left Hand of Darkness"),
            bookRepository.searchAsync("ace", limit = 20, offset = 0).map { it.title }
        )
    }

    @Test
    fun `given source filter when searching then results and count are limited to that source`() = runBlocking {
        val firstSource = sourceRepository.createAsync("First", "/books/first")
        val secondSource = sourceRepository.createAsync("Second", "/books/second")
        createBook(title = "Dune", author = null, sourceId = firstSource.id)
        createBook(title = "Dune Messiah", author = null, sourceId = secondSource.id)
        createBook(title = "Children of Dune", author = null, sourceId = secondSource.id)

        val results = bookRepository.searchAsync("dune", limit = 20, offset = 0, sourceId = secondSource.id)
        val count = bookRepository.countSearchResultsAsync("dune", sourceId = secondSource.id)

        assertEquals(listOf("Children of Dune", "Dune Messiah"), results.map { it.title })
        assertEquals(2, count)
    }

    @Test
    fun `given paged search when loading second page then ordering and counts are deterministic`() = runBlocking {
        val source = sourceRepository.createAsync("Main", "/books/main")
        createBook(title = "Dune Rogue", author = null, sourceId = source.id)
        createBook(title = "Dune", author = null, sourceId = source.id)
        createBook(title = "Dune Messiah", author = null, sourceId = source.id)

        val secondPage = bookRepository.searchAsync("dune", limit = 1, offset = 1)
        val count = bookRepository.countSearchResultsAsync("dune")

        assertEquals(listOf("Dune Messiah"), secondPage.map { it.title })
        assertEquals(3, count)
    }

    private suspend fun createBook(
        title: String,
        author: String?,
        sourceId: Int,
        isbn: String? = null,
        publisher: String? = null
    ) {
        val book = bookRepository.create(
            title = title,
            author = author,
            format = "epub",
            filePath = "/books/${UUID.randomUUID()}.epub",
            fileSize = 1024,
            sourceId = sourceId
        )
        bookRepository.updateMetadataAsync(
            id = book.id,
            isbn = isbn,
            publisher = publisher
        )
    }
}
