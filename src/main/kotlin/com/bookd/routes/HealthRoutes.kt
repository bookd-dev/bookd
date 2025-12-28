package com.bookd.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    route("/health") {
        get {
            call.respond(mapOf(
                "status" to "UP",
                "service" to "bookd-server",
                "version" to "0.0.1"
            ))
        }
    }
}
