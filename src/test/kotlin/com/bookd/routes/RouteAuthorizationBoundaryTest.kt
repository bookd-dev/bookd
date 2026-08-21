package com.bookd.routes

import com.bookd.domain.model.BookSource
import com.bookd.domain.model.Tag
import com.bookd.domain.model.User
import com.bookd.domain.service.BackgroundParseService
import com.bookd.domain.service.BookContentService
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.domain.service.TagService
import com.bookd.domain.service.TxtParseRuleService
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
import kotlin.test.assertTrue

class RouteAuthorizationBoundaryTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given no token when sources requested then source service is not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val sourceService = mockk<BookSourceService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { sourceService }
            })
        }

        application {
            configureSerialization()
            routing { bookSourceRoutes() }
        }

        val response = client.get("/api/sources")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { sourceService.getAllSources() }
    }

    @Test
    fun `given non admin token when txt rules requested then rule service is not called`() = testApplication {
        val userService = mockUserService("user-token", role = "user")
        val ruleService = mockk<TxtParseRuleService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { ruleService }
            })
        }

        application {
            configureSerialization()
            routing { txtParseRuleRoutes() }
        }

        val response = client.get("/api/txt-parse-rules") {
            header(HttpHeaders.Authorization, "Bearer user-token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 1) { userService.validateTokenAsync("user-token") }
        coVerify(exactly = 0) { ruleService.getAllRules() }
    }

    @Test
    fun `given admin token when background status requested then status is returned`() = testApplication {
        val userService = mockUserService("admin-token", role = "admin")
        val backgroundParseService = mockk<BackgroundParseService>()
        coEvery { backgroundParseService.getStatus() } returns BackgroundParseService.BackgroundParseStatus(
            running = true,
            enabled = true,
            intervalSeconds = 60,
            batchSize = 10,
            unparsedBooksCount = 3
        )
        startKoin {
            modules(module {
                single { userService }
                single { backgroundParseService }
            })
        }

        application {
            configureSerialization()
            routing { backgroundParseRoutes() }
        }

        val response = client.get("/api/background-parse/status") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Regex(""""running"\s*:\s*true""").containsMatchIn(body))
        coVerify(exactly = 1) { backgroundParseService.getStatus() }
    }

    @Test
    fun `given no token when tag is created then tag service is not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val tagService = mockk<TagService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { tagService }
            })
        }

        application {
            configureSerialization()
            routing { tagRoutes() }
        }

        val response = client.post("/api/tags") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"fiction"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { tagService.createTagAsync(any()) }
    }

    @Test
    fun `given no token when tags are listed then public read remains available`() = testApplication {
        val tagService = mockk<TagService>()
        coEvery { tagService.getTagStatsAsync() } returns mapOf(Tag(id = 1, name = "fiction") to 2)
        startKoin {
            modules(module { single { tagService } })
        }

        application {
            configureSerialization()
            routing { tagRoutes() }
        }

        val response = client.get("/api/tags")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("fiction"))
    }

    @Test
    fun `given user token when app sources requested then sources are returned`() = testApplication {
        val userService = mockUserService("user-token", role = "user")
        val sourceService = mockk<BookSourceService>()
        coEvery { sourceService.getAllSources() } returns listOf(
            BookSource(id = 1, name = "Library", path = "/books", enabled = true)
        )
        startKoin {
            modules(module {
                single { userService }
                single { sourceService }
            })
        }

        application {
            configureSerialization()
            routing { appRoutes() }
        }

        val response = client.get("/api/app/sources") {
            header(HttpHeaders.Authorization, "Bearer user-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Library"))
        coVerify(exactly = 1) { sourceService.getAllSources() }
    }

    @Test
    fun `given no token when app books requested then book service is not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val bookService = mockk<BookService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { bookService }
            })
        }

        application {
            configureSerialization()
            routing { appRoutes() }
        }

        val response = client.get("/api/app/books") {
            parameter("sourceId", "1")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { bookService.getBooksBySourceIdPaged(any(), any(), any()) }
    }

    @Test
    fun `given no token when book reparse requested then content service is not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val contentService = mockk<BookContentService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { contentService }
            })
        }

        application {
            configureSerialization()
            routing { bookContentRoutes() }
        }

        val response = client.post("/api/books/7/reparse")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { contentService.queueForReparse(any()) }
    }

    private fun mockUserService(token: String, role: String): UserService {
        val userService = mockk<UserService>()
        coEvery { userService.validateTokenAsync(token) } returns User(
            id = if (role == "admin") 1 else 2,
            username = role,
            password = "hash",
            email = null,
            role = role
        )
        return userService
    }
}
