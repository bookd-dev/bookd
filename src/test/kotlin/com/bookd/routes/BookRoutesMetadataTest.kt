package com.bookd.routes

import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.Book
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

class BookRoutesMetadataTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given metadata request with title when updating book then title is persisted`() = testApplication {
        val repository = mockk<BookRepository>()
        startKoin {
            modules(module { single { repository } })
        }

        every {
            repository.updateMetadata(
                id = 7,
                title = "New Title",
                author = null,
                coverPath = null,
                isbn = null,
                publisher = null,
                description = null
            )
        } returns 1
        every { repository.findById(7) } returns Book(
            id = 7,
            title = "New Title",
            author = null,
            format = "epub",
            filePath = "/books/new.epub",
            fileSize = 1024
        )

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.put("/api/books/7/metadata") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":" New Title "}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) {
            repository.updateMetadata(
                id = 7,
                title = "New Title",
                author = null,
                coverPath = null,
                isbn = null,
                publisher = null,
                description = null
            )
        }
    }

    @Test
    fun `given blank title when updating metadata then request is rejected`() = testApplication {
        val repository = mockk<BookRepository>(relaxed = true)
        startKoin {
            modules(module { single { repository } })
        }

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.put("/api/books/7/metadata") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        verify(exactly = 0) {
            repository.updateMetadata(
                id = any(),
                title = any(),
                author = any(),
                coverPath = any(),
                isbn = any(),
                publisher = any(),
                description = any()
            )
        }
    }
}
