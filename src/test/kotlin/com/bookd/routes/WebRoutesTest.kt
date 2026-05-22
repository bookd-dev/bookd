package com.bookd.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRoutesTest {

    @Test
    fun `given nested admin route when requested then react index is returned`() = testApplication {
        application {
            routing { webRoutes() }
        }

        val response = client.get("/admin/books")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("""<div id="root">"""))
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true)
    }

    @Test
    fun `given api route when only web routes are registered then spa fallback does not handle it`() = testApplication {
        application {
            routing { webRoutes() }
        }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
