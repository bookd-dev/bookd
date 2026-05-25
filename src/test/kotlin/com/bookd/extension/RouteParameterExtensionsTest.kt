package com.bookd.extension

import com.bookd.domain.model.ErrorCode
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteParameterExtensionsTest {

    @Test
    fun `given invalid int path parameter when required then configured error response is returned`() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/items/{id}") {
                    val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@get
                    call.respondSuccess(IdResponse(id))
                }
            }
        }

        val response = client.get("/items/not-a-number")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains(ErrorCode.BOOK_INVALID_ID.code))
    }

    @Test
    fun `given query parameters when reading typed values then defaults and parsed values are returned`() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/query") {
                    call.respondSuccess(
                        QueryResponse(
                            limit = call.intQueryParameter("limit", 20),
                            offset = call.longQueryParameter("offset", 0),
                            sourceId = call.optionalIntQueryParameter("sourceId"),
                            fullScan = call.booleanQueryParameter("fullScan", false)
                        )
                    )
                }
            }
        }

        val response = client.get("/query?offset=3&sourceId=9&fullScan=true")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Regex("\"limit\"\\s*:\\s*20").containsMatchIn(body))
        assertTrue(Regex("\"offset\"\\s*:\\s*3").containsMatchIn(body))
        assertTrue(Regex("\"sourceId\"\\s*:\\s*9").containsMatchIn(body))
        assertTrue(Regex("\"fullScan\"\\s*:\\s*true").containsMatchIn(body))
    }
}

@Serializable
private data class IdResponse(val id: Int)

@Serializable
private data class QueryResponse(
    val limit: Int,
    val offset: Long,
    val sourceId: Int?,
    val fullScan: Boolean
)
