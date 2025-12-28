package com.bookd.plugins

import com.bookd.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        bookRoutes()
        bookSourceRoutes()
        scanRoutes()
        fileSystemRoutes()
        webRoutes()
    }
}
