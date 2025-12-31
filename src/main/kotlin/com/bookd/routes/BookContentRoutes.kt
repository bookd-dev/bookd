package com.bookd.routes

import com.bookd.domain.service.BookContentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.bookContentRoutes() {
    route("/api/books/{id}") {
        
        // 获取书籍清单（目录结构）
        get("/manifest") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            val manifest = contentService.getBookManifest(bookId)
            if (manifest == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book manifest not found. Content may not be parsed yet."))
            } else {
                call.respond(manifest)
            }
        }
        
        // 获取章节内容
        get("/chapters/{index}") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            val chapterIndex = call.parameters["index"]?.toIntOrNull()
            
            if (bookId == null || chapterIndex == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid parameters"))
                return@get
            }
            
            val chapterContent = contentService.getChapterContent(bookId, chapterIndex)
            if (chapterContent == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chapter not found"))
            } else {
                call.respond(chapterContent)
            }
        }
        
        // 获取资源文件（图片等）
        get("/resources") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            val path = call.request.queryParameters["path"]
            
            if (bookId == null || path == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid parameters"))
                return@get
            }
            
            val resource = contentService.getResource(bookId, path)
            if (resource == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Resource not found"))
            } else {
                val (file, mediaType) = resource
                call.response.header(HttpHeaders.ContentType, mediaType)
                call.respondFile(file)
            }
        }
    }
}
