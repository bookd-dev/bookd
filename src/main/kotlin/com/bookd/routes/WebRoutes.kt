package com.bookd.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.webRoutes() {
    fun reactIndex(): String =
        this::class.java.classLoader.getResource("static/web/index.html")!!.readText()

    suspend fun ApplicationCall.respondReactIndex() {
        respondText(reactIndex(), ContentType.Text.Html)
    }

    get("/") {
        call.respondReactIndex()
    }
    
    get("/login") {
        call.respondReactIndex()
    }
    
    get("/setup") {
        call.respondReactIndex()
    }
    
    get("/admin") {
        call.respondReactIndex()
    }

    get("/admin/{...}") {
        call.respondReactIndex()
    }
    
    get("/reader") {
        call.respondReactIndex()
    }

    staticResources("/", "static/web")
}
