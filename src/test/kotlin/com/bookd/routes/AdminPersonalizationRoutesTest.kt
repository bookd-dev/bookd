package com.bookd.routes

import com.bookd.domain.model.AiEndpointResponse
import com.bookd.domain.model.AiProviderResponse
import com.bookd.domain.model.PersonalizationSettingsResponse
import com.bookd.domain.model.User
import com.bookd.domain.service.AiConfigurationService
import com.bookd.domain.service.PersonalizationSettingsService
import com.bookd.domain.service.UserService
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AdminPersonalizationRoutesTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given no token when personalization requested then settings services are not called`() = testApplication {
        val userService = mockk<UserService>(relaxed = true)
        val settingsService = mockk<PersonalizationSettingsService>(relaxed = true)
        val aiService = mockk<AiConfigurationService>(relaxed = true)
        startKoin {
            modules(module {
                single { userService }
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.get("/api/admin/personalization")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { settingsService.getSettings() }
        coVerify(exactly = 0) { aiService.listProviders() }
    }

    @Test
    fun `given non admin token when ai providers requested then service is not called`() = testApplication {
        val userService = mockk<UserService>()
        val settingsService = mockk<PersonalizationSettingsService>(relaxed = true)
        val aiService = mockk<AiConfigurationService>(relaxed = true)
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
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.get("/api/admin/ai-providers") {
            header(HttpHeaders.Authorization, "Bearer user-token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { aiService.listProviders() }
    }

    @Test
    fun `given admin token when personalization requested then response includes settings and masks secrets`() = testApplication {
        val userService = mockAdminUserService()
        val settingsService = mockk<PersonalizationSettingsService>()
        val aiService = mockk<AiConfigurationService>()
        coEvery { settingsService.getSettings() } returns PersonalizationSettingsResponse(timeZone = "UTC")
        coEvery { aiService.listProviders() } returns listOf(
            AiProviderResponse(
                id = 1,
                name = "GPT",
                providerKind = "openai_compatible",
                enabled = true,
                priority = 1,
                createdAt = "2026-05-29T00:00",
                updatedAt = "2026-05-29T00:00",
                endpoints = listOf(
                    AiEndpointResponse(
                        id = 2,
                        providerId = 1,
                        baseUrl = "https://api.example.com/v1",
                        apiKeySet = true,
                        maxConcurrency = 2,
                        enabled = true,
                        priority = 1,
                        createdAt = "2026-05-29T00:00",
                        updatedAt = "2026-05-29T00:00",
                        models = emptyList()
                    )
                )
            )
        )
        startKoin {
            modules(module {
                single { userService }
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.get("/api/admin/personalization") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("UTC"))
        assertTrue(body.contains("apiKeySet"))
        assertTrue(body.contains("models"))
        assertFalse(body.contains("supportsTts"))
        assertFalse(body.contains("supportsLlm"))
        assertFalse(body.contains("secret"))
        coVerify(exactly = 1) { settingsService.getSettings() }
        coVerify(exactly = 1) { aiService.listProviders() }
    }

    @Test
    fun `given admin token when provider has no endpoints then response includes empty endpoints array`() = testApplication {
        val userService = mockAdminUserService()
        val settingsService = mockk<PersonalizationSettingsService>(relaxed = true)
        val aiService = mockk<AiConfigurationService>()
        coEvery { aiService.listProviders() } returns listOf(
            AiProviderResponse(
                id = 1,
                name = "GPT",
                providerKind = "openai_compatible",
                enabled = true,
                priority = 1,
                createdAt = "2026-05-29T00:00",
                updatedAt = "2026-05-29T00:00",
                endpoints = emptyList()
            )
        )
        startKoin {
            modules(module {
                single { userService }
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.get("/api/admin/ai-providers") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("endpoints"))
        coVerify(exactly = 1) { aiService.listProviders() }
    }

    @Test
    fun `given admin token when provider submitted without capabilities then route creates provider`() = testApplication {
        val userService = mockAdminUserService()
        val settingsService = mockk<PersonalizationSettingsService>(relaxed = true)
        val aiService = mockk<AiConfigurationService>()
        coEvery { aiService.createProvider(any()) } returns AiProviderResponse(
            id = 1,
            name = "GPT",
            providerKind = "openai_compatible",
            enabled = true,
            priority = 1,
            createdAt = "2026-05-29T00:00",
            updatedAt = "2026-05-29T00:00",
            endpoints = emptyList()
        )
        startKoin {
            modules(module {
                single { userService }
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.post("/api/admin/ai-providers") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "GPT",
                  "providerKind": "openai_compatible",
                  "enabled": true,
                  "priority": 1
                }
                """.trimIndent()
            )
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertFalse(body.contains("supportsTts"))
        assertFalse(body.contains("supportsLlm"))
        coVerify(exactly = 1) { aiService.createProvider(any()) }
    }

    @Test
    fun `given admin token when missing provider deleted then route returns not found`() = testApplication {
        val userService = mockAdminUserService()
        val settingsService = mockk<PersonalizationSettingsService>(relaxed = true)
        val aiService = mockk<AiConfigurationService>()
        coEvery { aiService.deleteProvider(404) } returns false
        startKoin {
            modules(module {
                single { userService }
                single { settingsService }
                single { aiService }
            })
        }

        application {
            configureSerialization()
            routing { adminPersonalizationRoutes() }
        }

        val response = client.delete("/api/admin/ai-providers/404") {
            header(HttpHeaders.Authorization, "Bearer admin-token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        coVerify(exactly = 1) { aiService.deleteProvider(404) }
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
