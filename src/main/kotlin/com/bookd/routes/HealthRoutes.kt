package com.bookd.routes

import com.bookd.extension.respondSuccess
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String
)

fun Route.healthRoutes() {
    route("/api/health") {
        get {
            call.respondSuccess(HealthResponse(
                status = "UP",
                service = "bookd-server",
                version = "0.0.1"
            ))
        }
    }
}
