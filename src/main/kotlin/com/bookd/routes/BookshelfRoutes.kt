package com.bookd.routes

import com.bookd.com.bookd.extension.buildBaseUrl
import com.bookd.domain.model.*
import com.bookd.domain.service.BookshelfException
import com.bookd.domain.service.BookshelfService
import com.bookd.domain.service.UserService
import com.bookd.extension.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BookshelfRoutes")

/**
 * 书架管理路由
 * 
 * 提供书架的 CRUD 接口和书架-书籍关联管理
 */
fun Route.bookshelfRoutes() {
    route("/api/bookshelves") {
        // 获取用户所有书架
        get {
            val userId = call.getAuthenticatedUserId() ?: return@get
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelves = bookshelfService.getUserBookshelves(userId)
            call.respondSuccess(bookshelves)
        }
        
        // 创建新书架
        post {
            val userId = call.getAuthenticatedUserId() ?: return@post
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val request = call.receive<CreateBookshelfRequest>()
            val result = bookshelfService.createBookshelf(userId, request)
            
            call.handleResult(result) { bookshelf ->
                respondSuccess(HttpStatusCode.Created, bookshelf)
            }
        }
        
        // 调整书架顺序
        put("/reorder") {
            val userId = call.getAuthenticatedUserId() ?: return@put
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val request = call.receive<ReorderBookshelvesRequest>()
            val result = bookshelfService.reorderBookshelves(userId, request)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
        
        // 更新书架信息
        put("/{id}") {
            val userId = call.getAuthenticatedUserId() ?: return@put
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelfId = call.parameters["id"]?.toIntOrNull()
            if (bookshelfId == null) {
                call.respondError(ErrorCode.SHELF_INVALID_ID)
                return@put
            }
            
            val request = call.receive<UpdateBookshelfRequest>()
            val result = bookshelfService.updateBookshelf(userId, bookshelfId, request)
            
            call.handleResult(result) { bookshelf ->
                respondSuccess(bookshelf)
            }
        }
        
        // 删除书架
        delete("/{id}") {
            val userId = call.getAuthenticatedUserId() ?: return@delete
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelfId = call.parameters["id"]?.toIntOrNull()
            if (bookshelfId == null) {
                call.respondError(ErrorCode.SHELF_INVALID_ID)
                return@delete
            }
            
            val result = bookshelfService.deleteBookshelf(userId, bookshelfId)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
        
        // 获取书架中的书籍列表
        get("/{id}/books") {
            val userId = call.getAuthenticatedUserId() ?: return@get
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelfId = call.parameters["id"]?.toIntOrNull()
            if (bookshelfId == null) {
                call.respondError(ErrorCode.SHELF_INVALID_ID)
                return@get
            }
            
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
            
            val result = bookshelfService.getBooksInBookshelf(userId, bookshelfId, limit, offset)
            
            call.handleResult(result) { response ->
                // 拼接完整封面 URL
                val baseUrl = buildBaseUrl()
                val booksWithFullCoverUrl = response.books.map { bookWithProgress ->
                    val book = bookWithProgress.book
                    val fullCoverPath = book.coverPath?.let { path ->
                        if (path.startsWith("http")) path else "$baseUrl$path"
                    }
                    bookWithProgress.copy(
                        book = book.copy(coverPath = fullCoverPath)
                    )
                }
                respondSuccess(response.copy(books = booksWithFullCoverUrl))
            }
        }
        
        // 添加书籍到书架
        post("/{id}/books") {
            val userId = call.getAuthenticatedUserId() ?: return@post
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelfId = call.parameters["id"]?.toIntOrNull()
            if (bookshelfId == null) {
                call.respondError(ErrorCode.SHELF_INVALID_ID)
                return@post
            }
            
            val request = call.receive<AddBookToBookshelfRequest>()
            val result = bookshelfService.addBookToBookshelf(userId, bookshelfId, request.bookId)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
        
        // 从书架移除书籍
        delete("/{id}/books/{bookId}") {
            val userId = call.getAuthenticatedUserId() ?: return@delete
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookshelfId = call.parameters["id"]?.toIntOrNull()
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            
            if (bookshelfId == null) {
                call.respondError(ErrorCode.SHELF_INVALID_ID)
                return@delete
            }
            
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@delete
            }
            
            val result = bookshelfService.removeBookFromBookshelf(userId, bookshelfId, bookId)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
    }
    
    // 书籍相关的书架接口
    route("/api/books/{bookId}/bookshelves") {
        // 获取书籍所在的书架列表
        get {
            val userId = call.getAuthenticatedUserId() ?: return@get
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }
            
            val bookshelves = bookshelfService.getBookshelvesForBook(userId, bookId)
            call.respondSuccess(bookshelves)
        }
        
        // 批量添加书籍到多个书架
        post {
            val userId = call.getAuthenticatedUserId() ?: return@post
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@post
            }
            
            val request = call.receive<AddToBookshelvesRequest>()
            val result = bookshelfService.addBookToBookshelves(userId, bookId, request.bookshelfIds)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
        
        // 批量从多个书架移除书籍
        delete {
            val userId = call.getAuthenticatedUserId() ?: return@delete
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@delete
            }
            
            val request = call.receive<BatchRemoveFromBookshelvesRequest>()
            val result = bookshelfService.batchRemoveBookFromBookshelves(userId, bookId, request.bookshelfIds)
            
            call.handleResult(result) {
                respondNoContent()
            }
        }
    }
}
