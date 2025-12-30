package com.bookd.routes

import com.bookd.domain.model.*
import com.bookd.domain.service.ReadingService
import com.bookd.domain.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get

@Serializable
data class BookmarksResponse(
    val bookmarks: List<BookmarkResponse>,
    val total: Int
)

@Serializable
data class ReadingHistoryResponse(
    val items: List<ReadingHistoryItemDTO>
)

@Serializable
data class ReaderSettingsPatchRequest(
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val fontWeight: Int? = null,
    val lineHeight: Double? = null,
    val letterSpacing: Double? = null,
    val paragraphSpacing: Int? = null,
    val textAlign: String? = null,
    val theme: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val pageMode: String? = null,
    val brightness: Int? = null,
    val marginHorizontal: Int? = null,
    val marginVertical: Int? = null
)

fun Route.readingRoutes() {
    val readingService = get<ReadingService>(ReadingService::class.java)
    val userService = get<UserService>(UserService::class.java)
    
    // ============ 阅读进度 API ============
    route("/api/books/{bookId}/progress") {
        get {
            val user = getCurrentUser(call, userService) ?: return@get
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            val progress = readingService.getProgress(user.id, bookId)
            if (progress != null) {
                call.respond(progress)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No reading progress found"))
            }
        }
        
        put {
            val user = getCurrentUser(call, userService) ?: return@put
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@put
            }
            
            val dto = call.receive<ReadingProgressDTO>()
            val progress = readingService.updateProgress(user.id, bookId, dto)
            call.respond(progress)
        }
    }
    
    // ============ 阅读历史 API ============
    get("/api/user/reading-history") {
        val user = getCurrentUser(call, userService) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        
        val history = readingService.getReadingHistory(user.id, limit)
        call.respond(ReadingHistoryResponse(items = history))
    }
    
    // ============ 书签 API ============
    route("/api/books/{bookId}/bookmarks") {
        get {
            val user = getCurrentUser(call, userService) ?: return@get
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            val bookmarks = readingService.getBookmarks(user.id, bookId)
            call.respond(BookmarksResponse(bookmarks = bookmarks, total = bookmarks.size))
        }
        
        post {
            val user = getCurrentUser(call, userService) ?: return@post
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@post
            }
            
            val dto = call.receive<BookmarkDTO>()
            val bookmark = readingService.addBookmark(user.id, bookId, dto)
            call.respond(HttpStatusCode.Created, bookmark)
        }
    }
    
    route("/api/bookmarks/{id}") {
        put {
            val user = getCurrentUser(call, userService) ?: return@put
            val bookmarkId = call.parameters["id"]?.toIntOrNull()
            if (bookmarkId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid bookmark ID"))
                return@put
            }
            
            val dto = call.receive<BookmarkDTO>()
            val bookmark = readingService.updateBookmark(user.id, bookmarkId, dto)
            if (bookmark != null) {
                call.respond(bookmark)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Bookmark not found"))
            }
        }
        
        delete {
            val user = getCurrentUser(call, userService) ?: return@delete
            val bookmarkId = call.parameters["id"]?.toIntOrNull()
            if (bookmarkId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid bookmark ID"))
                return@delete
            }
            
            val deleted = readingService.deleteBookmark(user.id, bookmarkId)
            if (deleted) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Bookmark deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Bookmark not found"))
            }
        }
    }
    
    get("/api/user/bookmarks") {
        val user = getCurrentUser(call, userService) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
        
        val bookmarks = readingService.getAllUserBookmarks(user.id, limit, offset)
        call.respond(BookmarksResponse(bookmarks = bookmarks, total = bookmarks.size))
    }
    
    // ============ 阅读器设置 API ============
    route("/api/user/reader-settings") {
        get {
            val user = getCurrentUser(call, userService) ?: return@get
            
            val settings = readingService.getReaderSettings(user.id)
            if (settings != null) {
                call.respond(settings)
            } else {
                // 返回默认设置
                call.respond(ReaderSettingsDTO())
            }
        }
        
        put {
            val user = getCurrentUser(call, userService) ?: return@put
            
            val dto = call.receive<ReaderSettingsDTO>()
            val settings = readingService.updateReaderSettings(user.id, dto)
            call.respond(settings)
        }
        
        patch {
            val user = getCurrentUser(call, userService) ?: return@patch
            
            val request = call.receive<ReaderSettingsPatchRequest>()
            val settings = readingService.patchReaderSettings(
                userId = user.id,
                fontFamily = request.fontFamily,
                fontSize = request.fontSize,
                fontWeight = request.fontWeight,
                lineHeight = request.lineHeight,
                letterSpacing = request.letterSpacing,
                paragraphSpacing = request.paragraphSpacing,
                textAlign = request.textAlign,
                theme = request.theme,
                backgroundColor = request.backgroundColor,
                textColor = request.textColor,
                pageMode = request.pageMode,
                brightness = request.brightness,
                marginHorizontal = request.marginHorizontal,
                marginVertical = request.marginVertical
            )
            call.respond(settings)
        }
    }
}

private suspend fun getCurrentUser(call: ApplicationCall, userService: UserService): com.bookd.domain.model.User? {
    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
    if (token == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No token provided"))
        return null
    }
    
    val user = userService.validateToken(token)
    if (user == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
        return null
    }
    
    return user
}
