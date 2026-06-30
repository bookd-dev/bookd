package com.bookd.routes

import com.bookd.domain.model.Book
import com.bookd.domain.model.User
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.domain.service.UserService
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppBookSearchRoutesTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given user token and search query when app search requested then paged response is returned`() = testApplication {
        val userService = mockUserService()
        val bookService = mockk<BookService>()
        val sourceService = mockk<BookSourceService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { bookService }
                single { sourceService }
            })
        }
        coEvery { bookService.searchBooks("dune", 2, 1, 7) } returns listOf(
            Book(
                id = 10,
                title = "Dune Messiah",
                author = "Frank Herbert",
                format = "epub",
                filePath = "/books/dune-messiah.epub",
                fileSize = 1024,
                coverPath = "/covers/dune-messiah.jpg",
                sourceId = 7
            )
        )
        coEvery { bookService.getSearchCount("dune", 7) } returns 4

        application {
            configureSerialization()
            routing { appRoutes() }
        }

        val response = client.get("/api/app/books/search") {
            header(HttpHeaders.Authorization, "Bearer user-token")
            parameter("q", " dune ")
            parameter("sourceId", "7")
            parameter("limit", "2")
            parameter("offset", "1")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Dune Messiah"))
        assertTrue(body.contains("http://localhost/covers/dune-messiah.jpg"))
        assertTrue(Regex(""""total"\s*:\s*4""").containsMatchIn(body))
        assertTrue(Regex(""""limit"\s*:\s*2""").containsMatchIn(body))
        assertTrue(Regex(""""offset"\s*:\s*1""").containsMatchIn(body))
        assertTrue(Regex(""""hasMore"\s*:\s*true""").containsMatchIn(body))
        coVerify(exactly = 1) { bookService.searchBooks("dune", 2, 1, 7) }
        coVerify(exactly = 1) { bookService.getSearchCount("dune", 7) }
    }

    @Test
    fun `given blank query when app search requested then request is rejected`() = testApplication {
        val userService = mockUserService()
        val bookService = mockk<BookService>(relaxed = true)
        val sourceService = mockk<BookSourceService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { bookService }
                single { sourceService }
            })
        }

        application {
            configureSerialization()
            routing { appRoutes() }
        }

        val response = client.get("/api/app/books/search") {
            header(HttpHeaders.Authorization, "Bearer user-token")
            parameter("q", "   ")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { bookService.searchBooks(any(), any(), any(), any()) }
        coVerify(exactly = 0) { bookService.getSearchCount(any(), any()) }
    }

    @Test
    fun `given no token when app search requested then book service is not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val bookService = mockk<BookService>(relaxed = true)
        val sourceService = mockk<BookSourceService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { bookService }
                single { sourceService }
            })
        }

        application {
            configureSerialization()
            routing { appRoutes() }
        }

        val response = client.get("/api/app/books/search") {
            parameter("q", "dune")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { bookService.searchBooks(any(), any(), any(), any()) }
        coVerify(exactly = 0) { bookService.getSearchCount(any(), any()) }
    }

    private fun mockUserService(): UserService {
        val userService = mockk<UserService>()
        coEvery { userService.validateTokenAsync("user-token") } returns User(
            id = 2,
            username = "reader",
            password = "hash",
            email = null,
            role = "user"
        )
        return userService
    }
}
