package com.bookd.routes

import com.bookd.domain.model.User
import com.bookd.domain.service.BookScanService
import com.bookd.domain.service.FileSystemService
import com.bookd.domain.service.ScanResult
import com.bookd.domain.service.UserService
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationalRoutesTest {

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given filesystem list route when directory exists then response remains successful`() = testApplication {
        tempDir.resolve("book.txt").createFile()
        val fileSystemService = FileSystemService()
        val userService = mockAdminUserService()
        startKoin {
            modules(module {
                single { fileSystemService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { fileSystemRoutes() }
        }

        val response = client.get("/api/filesystem/list") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
            parameter("path", tempDir.toString())
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("book.txt"))
    }

    @Test
    fun `given scan all route when scan succeeds then response remains compatible`() = testApplication {
        val scanService = mockk<BookScanService>()
        val userService = mockAdminUserService()
        every { scanService.scanAllSources(false) } returns ScanResult(
            found = 3,
            imported = 2,
            message = "Success"
        )
        startKoin {
            modules(module {
                single { scanService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { scanRoutes() }
        }

        val response = client.post("/api/scan/all") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Regex(""""found"\s*:\s*3""").containsMatchIn(body))
        assertTrue(Regex(""""imported"\s*:\s*2""").containsMatchIn(body))
        verify(exactly = 1) { scanService.scanAllSources(false) }
    }

    @Test
    fun `given no token when filesystem list requested then service is not called`() = testApplication {
        val fileSystemService = mockk<FileSystemService>(relaxed = true)
        val userService = mockk<UserService>(relaxed = true)
        startKoin {
            modules(module {
                single { fileSystemService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { fileSystemRoutes() }
        }

        val response = client.get("/api/filesystem/list") {
            parameter("path", tempDir.toString())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { fileSystemService.listDirectoryAsync(any()) }
    }

    @Test
    fun `given no token when scan all requested then service is not called`() = testApplication {
        val scanService = mockk<BookScanService>(relaxed = true)
        val userService = mockk<UserService>(relaxed = true)
        startKoin {
            modules(module {
                single { scanService }
                single { userService }
            })
        }

        application {
            configureSerialization()
            routing { scanRoutes() }
        }

        val response = client.post("/api/scan/all")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        verify(exactly = 0) { scanService.scanAllSources(any()) }
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
