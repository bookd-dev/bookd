package com.bookd.routes

import com.bookd.domain.model.*
import com.bookd.domain.service.ReadingService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.request.*
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
    val pageMode: String? = null,
    val brightness: Int? = null,
    val marginHorizontal: Int? = null,
    val marginVertical: Int? = null,
    val firstLineIndent: Boolean? = null,
    val pageAnimationType: String? = null
)

fun Route.readingRoutes() {
    val readingService = get<ReadingService>(ReadingService::class.java)

    // ============ 阅读进度 API ============
    route("/api/books/{bookId}/progress") {
        get {
            val user = call.getAuthenticatedUser() ?: return@get
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            val progress = readingService.getProgress(user.id, bookId)
            if (progress != null) {
                call.respondSuccess(progress)
            } else {
                call.respondError(ErrorCode.READ_PROGRESS_NOT_FOUND)
            }
        }

        put {
            val user = call.getAuthenticatedUser() ?: return@put
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@put
            }

            val dto = call.receive<ReadingProgressDTO>()
            val progress = readingService.updateProgress(user.id, bookId, dto)
            call.respondSuccess(progress)
        }
    }

    // ============ 阅读历史 API ============
    get("/api/user/reading-history") {
        val user = call.getAuthenticatedUser() ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

        val history = readingService.getReadingHistory(user.id, limit)
        call.respondSuccess(ReadingHistoryResponse(items = history))
    }

    // ============ 书签 API ============
    route("/api/books/{bookId}/bookmarks") {
        get {
            val user = call.getAuthenticatedUser() ?: return@get
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            val bookmarks = readingService.getBookmarks(user.id, bookId)
            call.respondSuccess(BookmarksResponse(bookmarks = bookmarks, total = bookmarks.size))
        }

        post {
            val user = call.getAuthenticatedUser() ?: return@post
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@post
            }

            val dto = call.receive<BookmarkDTO>()
            val bookmark = readingService.addBookmark(user.id, bookId, dto)
            call.respondSuccess(HttpStatusCode.Created, bookmark)
        }
    }

    route("/api/bookmarks/{id}") {
        put {
            val user = call.getAuthenticatedUser() ?: return@put
            val bookmarkId = call.parameters["id"]?.toIntOrNull()
            if (bookmarkId == null) {
                call.respondError(ErrorCode.READ_INVALID_BOOKMARK_ID)
                return@put
            }

            val dto = call.receive<BookmarkDTO>()
            val bookmark = readingService.updateBookmark(user.id, bookmarkId, dto)
            if (bookmark != null) {
                call.respondSuccess(bookmark)
            } else {
                call.respondError(ErrorCode.READ_BOOKMARK_NOT_FOUND)
            }
        }

        delete {
            val user = call.getAuthenticatedUser() ?: return@delete
            val bookmarkId = call.parameters["id"]?.toIntOrNull()
            if (bookmarkId == null) {
                call.respondError(ErrorCode.READ_INVALID_BOOKMARK_ID)
                return@delete
            }

            val deleted = readingService.deleteBookmark(user.id, bookmarkId)
            if (deleted) {
                call.respondSuccessMessage(MessageBundle.Success.BOOKMARK_DELETED)
            } else {
                call.respondError(ErrorCode.READ_BOOKMARK_NOT_FOUND)
            }
        }
    }

    get("/api/user/bookmarks") {
        val user = call.getAuthenticatedUser() ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

        val bookmarks = readingService.getAllUserBookmarks(user.id, limit, offset)
        call.respondSuccess(BookmarksResponse(bookmarks = bookmarks, total = bookmarks.size))
    }

    // ============ 阅读器设置 API ============
    route("/api/user/reader-settings") {
        get {
            val user = call.getAuthenticatedUser() ?: return@get

            val settings = readingService.getReaderSettings(user.id)
            if (settings != null) {
                call.respondSuccess(settings)
            } else {
                // 返回默认设置
                call.respondSuccess(ReaderSettingsDTO())
            }
        }

        put {
            val user = call.getAuthenticatedUser() ?: return@put

            val dto = call.receive<ReaderSettingsDTO>()
            val settings = readingService.updateReaderSettings(user.id, dto)
            call.respondSuccess(settings)
        }

        patch {
            val user = call.getAuthenticatedUser() ?: return@patch

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
                pageMode = request.pageMode,
                brightness = request.brightness,
                marginHorizontal = request.marginHorizontal,
                marginVertical = request.marginVertical,
                firstLineIndent = request.firstLineIndent,
                pageAnimationType = request.pageAnimationType
            )
            call.respondSuccess(settings)
        }
    }
}
