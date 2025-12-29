package com.bookd.routes

import com.bookd.domain.service.BookService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.data.repository.BookRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get

@Serializable
data class BooksResponse(
    val books: List<com.bookd.domain.model.Book>,
    val total: Int
)

fun Route.bookRoutes() {
    route("/api/books") {
        get {
            val bookService = get<BookService>(BookService::class.java)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
            val sourceId = call.request.queryParameters["sourceId"]?.toIntOrNull()
            
            val books = if (sourceId != null) {
                bookService.getBooksBySourceId(sourceId)
            } else {
                bookService.getAllBooks(limit, offset)
            }
            
            val total = if (sourceId != null) {
                bookService.getCountBySourceId(sourceId).toInt()
            } else {
                bookService.getTotalCount().toInt()
            }
            
            call.respond(BooksResponse(books, total))
        }
        
        get("/count") {
            val bookService = get<BookService>(BookService::class.java)
            val sourceId = call.request.queryParameters["sourceId"]?.toIntOrNull()
            
            val count = if (sourceId != null) {
                bookService.getCountBySourceId(sourceId)
            } else {
                bookService.getTotalCount()
            }
            
            call.respond(mapOf("count" to count))
        }
        
        get("/{id}") {
            val bookService = get<BookService>(BookService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            val book = bookService.getBookById(id)
            if (book == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
            } else {
                call.respond(book)
            }
        }
        
        post("/{id}/generate-cover") {
            val coverGenerator = get<CoverGeneratorService>(CoverGeneratorService::class.java)
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@post
            }
            
            val book = bookRepository.findById(id)
            if (book == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
                return@post
            }
            
            val coverPath = coverGenerator.generateCover(id, book.title, book.author)
            if (coverPath != null) {
                // Update book with generated cover path
                bookRepository.updateMetadata(id, coverPath = coverPath)
                call.respond(mapOf("success" to true, "coverPath" to coverPath))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to generate cover"))
            }
        }
    }
}
