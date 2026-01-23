package com.bookd.routes

import com.bookd.com.bookd.extension.buildBaseUrl
import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BookContentService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.bookContentRoutes() {
    route("/api/books/{id}") {

        // 获取书籍清单（目录结构）
        get("/manifest") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookRepository = get<com.bookd.data.repository.BookRepository>(com.bookd.data.repository.BookRepository::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()

            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            // 检查书籍是否存在
            val book = bookRepository.findById(bookId)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@get
            }

            // 如果未解析，触发按需解析
            if (!book.chaptersParsed) {
                try {
                    val parsed = contentService.parseOnDemand(bookId, book.filePath)
                    if (!parsed) {
                        call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED)
                        return@get
                    }
                } catch (e: Exception) {
                    call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED, e.message)
                    return@get
                }
            }

            val manifest = contentService.getBookManifest(bookId)
            if (manifest == null) {
                call.respondError(ErrorCode.BOOK_MANIFEST_NOT_FOUND)
            } else {
                call.respondSuccess(manifest)
            }
        }
        
        // 获取带阅读进度的书籍清单（需要认证）
        get("/manifest-with-progress") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookRepository = get<com.bookd.data.repository.BookRepository>(com.bookd.data.repository.BookRepository::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            val userId = call.getAuthenticatedUserId() ?: return@get
            
            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            // 检查书籍是否存在
            val book = bookRepository.findById(bookId)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@get
            }

            // 如果未解析，触发按需解析
            if (!book.chaptersParsed) {
                try {
                    val parsed = contentService.parseOnDemand(bookId, book.filePath)
                    if (!parsed) {
                        call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED)
                        return@get
                    }
                } catch (e: Exception) {
                    call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED, e.message)
                    return@get
                }
            }
            
            val manifest = contentService.getBookManifestWithProgress(bookId, userId)
            if (manifest == null) {
                call.respondError(ErrorCode.BOOK_MANIFEST_NOT_FOUND)
            } else {
                call.respondSuccess(manifest)
            }
        }

        // 重新解析书籍内容
        post("/reparse") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()

            if (bookId == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@post
            }

            val queued = contentService.queueForReparse(bookId)
            if (queued) {
                call.respondSuccessMessage(MessageBundle.Success.BOOK_QUEUED_REPARSE)
            } else {
                call.respondError(ErrorCode.BOOK_ALREADY_PARSING)
            }
        }

        // 获取章节内容
        get("/chapters/{index}") {
            val contentService = get<BookContentService>(BookContentService::class.java)
            val bookId = call.parameters["id"]?.toIntOrNull()
            val chapterIndex = call.parameters["index"]?.toIntOrNull()

            if (bookId == null || chapterIndex == null) {
                call.respondError(ErrorCode.BOOK_INVALID_PARAMS)
                return@get
            }

            // 构建完整的 URL
            val baseUrl = call.buildBaseUrl()

            val chapterContent = contentService.getChapterContent(bookId, chapterIndex, baseUrl)
            if (chapterContent == null) {
                call.respondError(ErrorCode.BOOK_CHAPTER_NOT_FOUND)
            } else {
                call.respondSuccess(chapterContent)
            }
        }
    }
}
