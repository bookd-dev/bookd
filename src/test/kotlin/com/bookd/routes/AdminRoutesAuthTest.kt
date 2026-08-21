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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
        coVerify(exactly = 0) { migrationService.migrateResourceDimensions() }
        coVerify(exactly = 0) { migrationService.migrateCoverDimensions() }
    }

    @Test
    fun `given non admin token when admin migration requested then migration is not executed`() = testApplication {
        val userService = mockk<UserService>()
        val migrationService = mockk<ImageDimensionMigrationService>(relaxed = true)
        coEvery { userService.validateTokenAsync("user-token") } returns User(
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
        coVerify(exactly = 1) { userService.validateTokenAsync("user-token") }
        coVerify(exactly = 0) { migrationService.migrateResourceDimensions() }
        coVerify(exactly = 0) { migrationService.migrateCoverDimensions() }
    }

    @Test
    fun `given admin token when admin migration requested then migration is executed`() = testApplication {
        val userService = mockk<UserService>()
        val migrationService = mockk<ImageDimensionMigrationService>()
        coEvery { userService.validateTokenAsync("admin-token") } returns User(
            id = 1,
            username = "admin",
            password = "hash",
            email = null,
            role = "admin"
        )
        coEvery { migrationService.migrateResourceDimensions() } returns MigrationResult(total = 2, successCount = 1, failedCount = 1)
        coEvery { migrationService.migrateCoverDimensions() } returns MigrationResult(total = 3, successCount = 2, failedCount = 1)
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
        coVerify(exactly = 1) { migrationService.migrateResourceDimensions() }
        coVerify(exactly = 1) { migrationService.migrateCoverDimensions() }
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
        coEvery { userService.validateTokenAsync("bad-token") } returns null
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
        coVerify(exactly = 1) { userService.validateTokenAsync("bad-token") }
        coVerify(exactly = 0) { userService.findAllAsync() }
    }
}
