package com.bookd.routes

import com.bookd.domain.model.User
import com.bookd.domain.service.ImageDimensionMigrationService
import com.bookd.domain.service.MigrationResult
import com.bookd.domain.service.ReadingService
import com.bookd.domain.service.UserService
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

class AdminRoutesAuthTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given no token when admin migration requested then migration is not executed`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val migrationService = mockk<ImageDimensionMigrationService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { migrationService }
            })
        }

        application {
            configureSerialization()
            routing { adminRoutes() }
        }

        val response = client.post("/api/admin/migrate-image-dimensions")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        verify(exactly = 0) { migrationService.migrateResourceDimensions() }
        verify(exactly = 0) { migrationService.migrateCoverDimensions() }
    }

    @Test
    fun `given non admin token when admin migration requested then migration is not executed`() = testApplication {
        val userService = mockk<UserService>()
        val migrationService = mockk<ImageDimensionMigrationService>(relaxed = true)
        every { userService.validateToken("user-token") } returns User(
            id = 2,
            username = "reader",
            password = "hash",
            email = null,
            role = "user"
        )
        startKoin {
            modules(module {
                single { userService }
                single { migrationService }
            })
        }

        application {
            configureSerialization()
            routing { adminRoutes() }
        }

        val response = client.post("/api/admin/migrate-image-dimensions") {
            header(HttpHeaders.Authorization, "Bearer user-token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        verify(exactly = 1) { userService.validateToken("user-token") }
        verify(exactly = 0) { migrationService.migrateResourceDimensions() }
        verify(exactly = 0) { migrationService.migrateCoverDimensions() }
    }

    @Test
    fun `given admin token when admin migration requested then migration is executed`() = testApplication {
        val userService = mockk<UserService>()
        val migrationService = mockk<ImageDimensionMigrationService>()
        every { userService.validateToken("admin-token") } returns User(
            id = 1,
            username = "admin",
            password = "hash",
            email = null,
            role = "admin"
        )
        every { migrationService.migrateResourceDimensions() } returns MigrationResult(total = 2, successCount = 1, failedCount = 1)
        every { migrationService.migrateCoverDimensions() } returns MigrationResult(total = 3, successCount = 2, failedCount = 1)
        startKoin {
            modules(module {
                single { userService }
                single { migrationService }
            })
        }

        application {
            configureSerialization()
            routing { adminRoutes() }
        }

        val response = client.post("/api/admin/migrate-image-dimensions") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { migrationService.migrateResourceDimensions() }
        verify(exactly = 1) { migrationService.migrateCoverDimensions() }
    }

    @Test
    fun `given no token when reading settings requested then shared auth rejects request`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val readingService = mockk<ReadingService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { readingService }
            })
        }

        application {
            configureSerialization()
            routing { readingRoutes() }
        }

        val response = client.get("/api/user/reader-settings")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { readingService.getReaderSettings(any()) }
    }

    @Test
    fun `given invalid token when users requested then shared admin auth rejects request`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        every { userService.validateToken("bad-token") } returns null
        startKoin {
            modules(module { single { userService } })
        }

        application {
            configureSerialization()
            routing { userManagementRoutes(userService) }
        }

        val response = client.get("/api/users") {
            header(HttpHeaders.Authorization, "Bearer bad-token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        verify(exactly = 1) { userService.validateToken("bad-token") }
        verify(exactly = 0) { userService.findAll() }
    }
}
