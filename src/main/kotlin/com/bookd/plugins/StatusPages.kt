package com.bookd.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Error handling request to ${call.request.path()}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to (cause.message ?: "Unknown error"),
                    "type" to cause::class.simpleName
                )
            )
        }
    }
}
