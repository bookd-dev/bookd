package com.bookd.routes

import com.bookd.com.bookd.extension.buildBaseUrl
import com.bookd.domain.service.BookContentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
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
        
        // 重新解析书籍内容
        post("/reparse") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@post
            }
            
            val queued = contentService.queueForReparse(bookId)
            if (queued) {
                call.respond(mapOf("message" to "Book queued for reparse"))
            } else {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Book is already being parsed or not found"))
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
            
            // 构建完整的 URL
            val baseUrl = call.buildBaseUrl()
            
            val chapterContent = contentService.getChapterContent(bookId, chapterIndex, baseUrl)
            if (chapterContent == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chapter not found"))
            } else {
                call.respond(chapterContent)
            }
        }
    }
}
