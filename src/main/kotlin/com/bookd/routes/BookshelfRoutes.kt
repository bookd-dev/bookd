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
            
            result.fold(
                onSuccess = { bookshelf ->
                    call.respondSuccess(HttpStatusCode.Created, bookshelf)
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
        }
        
        // 调整书架顺序
        put("/reorder") {
            val userId = call.getAuthenticatedUserId() ?: return@put
            val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
            
            val request = call.receive<ReorderBookshelvesRequest>()
            val result = bookshelfService.reorderBookshelves(userId, request)
            
            result.fold(
                onSuccess = {
                    call.respondNoContent()
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = { bookshelf ->
                    call.respondSuccess(bookshelf)
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = {
                    call.respondNoContent()
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = { response ->
                    // 拼接完整封面 URL
                    val baseUrl = call.buildBaseUrl()
                    val booksWithFullCoverUrl = response.books.map { bookWithProgress ->
                        val book = bookWithProgress.book
                        val fullCoverPath = book.coverPath?.let { path ->
                            if (path.startsWith("http")) path else "$baseUrl$path"
                        }
                        bookWithProgress.copy(
                            book = book.copy(coverPath = fullCoverPath)
                        )
                    }
                    call.respondSuccess(response.copy(books = booksWithFullCoverUrl))
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = {
                    call.respondNoContent()
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = {
                    call.respondNoContent()
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
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
            
            result.fold(
                onSuccess = {
                    call.respondNoContent()
                },
                onFailure = { e ->
                    if (e is BookshelfException) {
                        call.respondError(e.errorCode)
                    } else {
                        call.respondError(ErrorCode.GEN_INTERNAL_ERROR, e.message)
                    }
                }
            )
        }
    }
}

/**
 * 获取当前认证用户的 ID
 * 如果未认证则返回 null 并自动响应错误
 */
private suspend fun ApplicationCall.getAuthenticatedUserId(): Int? {
    val userService = get<UserService>(UserService::class.java)
    val token = request.header("Authorization")?.removePrefix("Bearer ")
    
    if (token == null) {
        respondError(ErrorCode.AUTH_NO_TOKEN)
        return null
    }
    
    val user = userService.validateToken(token)
    if (user == null) {
        respondError(ErrorCode.AUTH_INVALID_TOKEN)
        return null
    }
    
    return user.id
}
