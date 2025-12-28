package com.bookd.routes

import com.bookd.domain.model.CreateBookSourceRequest
import com.bookd.domain.service.BookSourceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.bookSourceRoutes() {
    route("/api/sources") {
        get {
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val sources = bookSourceService.getAllSources()
            call.respond(sources)
        }
        
        post {
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val request = call.receive<CreateBookSourceRequest>()
            val source = bookSourceService.createSource(request.name, request.path)
            call.respond(HttpStatusCode.Created, source)
        }
        
        delete("/{id}") {
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
                return@delete
            }
            
            val deleted = bookSourceService.deleteSource(id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source not found"))
            }
        }
        
        post("/{id}/toggle") {
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
                return@post
            }
            
            val toggled = bookSourceService.toggleSource(id)
            if (toggled) {
                call.respond(mapOf("message" to "Source toggled"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source not found"))
            }
        }
    }
}
