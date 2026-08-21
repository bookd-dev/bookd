package com.bookd.routes

import com.bookd.domain.model.CreateBookSourceRequest
import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BookSourceService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.bookSourceRoutes() {
    route("/api/sources") {
        get {
            call.requireAdminUser() ?: return@get
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val sources = bookSourceService.getAllSources()
            call.respondSuccess(sources)
        }

        post {
            call.requireAdminUser() ?: return@post
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val request = call.receive<CreateBookSourceRequest>()
            val source = bookSourceService.createSource(request.name, request.path)
            call.respondSuccess(HttpStatusCode.Created, source)
        }

        delete("/{id}") {
            call.requireAdminUser() ?: return@delete
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.SOURCE_INVALID_ID)
                return@delete
            }

            val deleted = bookSourceService.deleteSource(id)
            if (deleted) {
                call.respondNoContent()
            } else {
                call.respondError(ErrorCode.SOURCE_NOT_FOUND)
            }
        }

        post("/{id}/toggle") {
            call.requireAdminUser() ?: return@post
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.SOURCE_INVALID_ID)
                return@post
            }

            val toggled = bookSourceService.toggleSource(id)
            if (toggled) {
                call.respondSuccessMessage(MessageBundle.Success.SOURCE_TOGGLED)
            } else {
                call.respondError(ErrorCode.SOURCE_NOT_FOUND)
            }
        }
    }
}
