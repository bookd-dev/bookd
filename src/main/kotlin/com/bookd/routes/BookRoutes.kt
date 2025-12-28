package com.bookd.routes

import com.bookd.domain.service.BookService
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
            call.respond(BooksResponse(books, books.size))
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
    }
}
