package com.bookd.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.webRoutes() {
    get("/") {
        call.respondText("Bookd Server is running", ContentType.Text.Plain)
    }
    
    get("/admin") {
        call.respondRedirect("/admin/")
    }
    
    staticResources("/admin", "static")
}
