package com.bookd.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.webRoutes() {
    // Serve login page at root and /login
    get("/") {
        call.respondText(
            this::class.java.classLoader.getResource("static/login.html")!!.readText(),
            ContentType.Text.Html
        )
    }
    
    get("/login") {
        call.respondText(
            this::class.java.classLoader.getResource("static/login.html")!!.readText(),
            ContentType.Text.Html
        )
    }
    
    // Serve setup page
    get("/setup") {
        call.respondText(
            this::class.java.classLoader.getResource("static/setup.html")!!.readText(),
            ContentType.Text.Html
        )
    }
    
    // Serve admin page
    get("/admin") {
        call.respondText(
            this::class.java.classLoader.getResource("static/admin.html")!!.readText(),
            ContentType.Text.Html
        )
    }
    
    // Serve reader page
    get("/reader") {
        call.respondText(
            this::class.java.classLoader.getResource("static/reader.html")!!.readText(),
            ContentType.Text.Html
        )
    }
    
    // Serve other static resources
    staticResources("/", "static")
}
