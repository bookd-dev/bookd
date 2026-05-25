package com.bookd.routes

import com.bookd.domain.service.BookScanService
import com.bookd.domain.service.FileSystemService
import com.bookd.domain.service.ScanResult
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
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
        startKoin {
            modules(module { single { fileSystemService } })
        }

        application {
            configureSerialization()
            routing { fileSystemRoutes() }
        }

        val response = client.get("/api/filesystem/list") {
            parameter("path", tempDir.toString())
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("book.txt"))
    }

    @Test
    fun `given scan all route when scan succeeds then response remains compatible`() = testApplication {
        val scanService = mockk<BookScanService>()
        every { scanService.scanAllSources(false) } returns ScanResult(
            found = 3,
            imported = 2,
            message = "Success"
        )
        startKoin {
            modules(module { single { scanService } })
        }

        application {
            configureSerialization()
            routing { scanRoutes() }
        }

        val response = client.post("/api/scan/all")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Regex(""""found"\s*:\s*3""").containsMatchIn(body))
        assertTrue(Regex(""""imported"\s*:\s*2""").containsMatchIn(body))
        verify(exactly = 1) { scanService.scanAllSources(false) }
    }
}
