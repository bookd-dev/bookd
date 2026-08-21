package com.bookd.routes

import com.bookd.domain.model.Book
import com.bookd.domain.model.User
import com.bookd.domain.service.BookService
import com.bookd.domain.service.CoverGenerateResult
import com.bookd.domain.service.UserService
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
        val bookService = mockk<BookService>()
        val userService = mockAdminUserService()
        startKoin {
            modules(module {
                single { bookService }
                single { userService }
            })
        }

        coEvery {
            bookService.updateMetadata(
                id = 7,
                title = "New Title",
                author = null,
                coverPath = null,
                isbn = null,
                publisher = null,
                description = null
            )
        } returns Book(
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
            header(HttpHeaders.Authorization, "Bearer admin-token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":" New Title "}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            bookService.updateMetadata(
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
        val bookService = mockk<BookService>(relaxed = true)
        val userService = mockAdminUserService()
        startKoin {
            modules(module {
                single { bookService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.put("/api/books/7/metadata") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) {
            bookService.updateMetadata(
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

    @Test
    fun `given no token when updating metadata then book service is not called`() = testApplication {
        val bookService = mockk<BookService>(relaxed = true)
        val userService = mockk<UserService>(relaxed = true)
        startKoin {
            modules(module {
                single { bookService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.put("/api/books/7/metadata") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"New Title"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) {
            bookService.updateMetadata(
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

    @Test
    fun `given books with local and absolute covers when listing then local cover is public url`() = testApplication {
        val bookService = mockk<BookService>()
        startKoin {
            modules(module { single { bookService } })
        }

        coEvery { bookService.getAllBooks(limit = 100, offset = 0) } returns listOf(
            Book(
                id = 1,
                title = "Local Cover",
                author = null,
                format = "epub",
                filePath = "/books/local.epub",
                fileSize = 1024,
                coverPath = "/covers/local.png"
            ),
            Book(
                id = 2,
                title = "Remote Cover",
                author = null,
                format = "epub",
                filePath = "/books/remote.epub",
                fileSize = 1024,
                coverPath = "https://cdn.example/remote.png"
            )
        )
        coEvery { bookService.getTotalCount() } returns 2

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.get("/api/books")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        kotlin.test.assertTrue(body.contains("http://localhost/covers/local.png"))
        kotlin.test.assertTrue(body.contains("https://cdn.example/remote.png"))
    }

    @Test
    fun `given generated cover request when service succeeds then route returns cover path`() = testApplication {
        val bookService = mockk<BookService>()
        val userService = mockAdminUserService()
        startKoin {
            modules(module {
                single { bookService }
                single { userService }
            })
        }

        coEvery { bookService.generateCover(7) } returns CoverGenerateResult.Generated(
            coverPath = "/book_images/covers/generated.png",
            width = 400,
            height = 600
        )

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.post("/api/books/7/generate-cover") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        kotlin.test.assertTrue(body.contains("/book_images/covers/generated.png"))
        coVerify(exactly = 1) { bookService.generateCover(7) }
    }

    private fun mockAdminUserService(): UserService {
        val userService = mockk<UserService>()
        coEvery { userService.validateTokenAsync("admin-token") } returns User(
            id = 1,
            username = "admin",
            password = "hash",
            email = null,
            role = "admin"
        )
        return userService
    }
}
