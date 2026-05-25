package com.bookd.routes

import com.bookd.domain.model.BookDocument
import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookDetailService
import com.bookd.domain.service.BookContentService
import com.bookd.domain.service.ChapterListResult
import com.bookd.domain.service.CoverGenerateResult
import com.bookd.domain.service.CoverUploadResult
import com.bookd.extension.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BookRoutes")

@Serializable
data class BooksResponse(
    val books: List<com.bookd.domain.model.Book>,
    val total: Int
)

@Serializable
data class ChapterInfo(
    val index: Int,
    val title: String,
    val wordCount: Int,
    val imageCount: Int,
    val level: Int
)

@Serializable
data class ChaptersResponse(
    val bookId: Int,
    val total: Int,
    val chapters: List<ChapterInfo>
)

@Serializable
data class UpdateMetadataRequest(
    val title: String? = null,
    val author: String? = null,
    val coverPath: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val description: String? = null
)

@Serializable
data class UpdateMetadataResponse(
    val success: Boolean,
    val book: com.bookd.domain.model.Book?
)

@Serializable
data class CoverUploadResponse(
    val success: Boolean,
    val coverPath: String
)

@Serializable
data class CoverGenerateResponse(
    val success: Boolean,
    val coverPath: String
)

@Serializable
data class BookCountResponse(
    val count: Long
)

fun Route.bookRoutes() {
    route("/api/books") {
        get {
            val bookService = get<BookService>(BookService::class.java)
            val limit = call.intQueryParameter("limit", 100)
            val offset = call.longQueryParameter("offset", 0)
            val sourceId = call.optionalIntQueryParameter("sourceId")

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

            val baseUrl = call.buildBaseUrl()
            call.respondSuccess(BooksResponse(books.map { it.withPublicCoverUrl(baseUrl) }, total))
        }

        get("/count") {
            val bookService = get<BookService>(BookService::class.java)
            val sourceId = call.optionalIntQueryParameter("sourceId")

            val count = if (sourceId != null) {
                bookService.getCountBySourceId(sourceId)
            } else {
                bookService.getTotalCount()
            }

            call.respondSuccess(BookCountResponse(count))
        }

        get("/{id}") {
            val bookService = get<BookService>(BookService::class.java)
            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@get

            val book = bookService.getBookById(id)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
            } else {
                // 构建完整的 URL
                val baseUrl = call.buildBaseUrl()
                call.respondSuccess(book.withPublicCoverUrl(baseUrl))
            }
        }

        // 获取书籍详情（包含标签、阅读进度、书架信息）- 需要认证
        get("/{id}/detail") {
            val bookDetailService = get<BookDetailService>(BookDetailService::class.java)
            
            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@get
            
            // 获取当前用户
            val userId = call.getAuthenticatedUserId() ?: return@get
            
            val bookDetail = bookDetailService.getBookDetail(id, userId)
            if (bookDetail == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@get
            }
            
            // 构建完整的封面 URL
            val baseUrl = call.buildBaseUrl()
            val bookWithFullUrls = bookDetail.book.withPublicCoverUrl(baseUrl)
            
            call.respondSuccess(bookDetail.copy(book = bookWithFullUrls))
        }

        // 获取书籍章节列表（仅目录项）
        get("/{id}/chapters") {
            val contentService = get<BookContentService>(BookContentService::class.java)

            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@get

            when (val result = contentService.getChapterList(id)) {
                ChapterListResult.NotFound -> call.respondError(ErrorCode.BOOK_NOT_FOUND)
                ChapterListResult.ParseFailed -> call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED)
                is ChapterListResult.Success -> {
                    val tocDocuments = result.documents
                    call.respondSuccess(
                        ChaptersResponse(
                            bookId = id,
                            total = tocDocuments.size,
                            chapters = tocDocuments.map { it.toChapterInfo() }
                        )
                    )
                }
            }
        }

        put("/{id}/metadata") {
            call.requireAdminUser() ?: return@put
            val bookService = get<BookService>(BookService::class.java)

            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@put

            val request = call.receive<UpdateMetadataRequest>()
            if (request.title != null && request.title.isBlank()) {
                call.respondError(ErrorCode.BOOK_INVALID_PARAMS)
                return@put
            }

            val book = bookService.updateMetadata(
                id = id,
                title = request.title?.trim(),
                author = request.author,
                coverPath = request.coverPath,
                isbn = request.isbn,
                publisher = request.publisher,
                description = request.description
            )

            if (book != null) {
                call.respondSuccess(UpdateMetadataResponse(success = true, book = book))
            } else {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
            }
        }

        post("/{id}/cover") {
            call.requireAdminUser() ?: return@post
            val bookService = get<BookService>(BookService::class.java)

            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@post

            var coverPath: String? = null
            var uploadError: String? = null

            try {
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            if (part.name == "cover") {
                                val fileBytes = part.provider().toInputStream().readBytes()
                                when (val result = bookService.uploadCover(id, part.originalFileName, fileBytes)) {
                                    CoverUploadResult.BookNotFound -> {
                                        uploadError = ErrorCode.BOOK_NOT_FOUND.code
                                    }
                                    is CoverUploadResult.SaveFailed -> {
                                        uploadError = result.details
                                    }
                                    is CoverUploadResult.Saved -> {
                                        coverPath = result.coverPath
                                        logger.info(
                                            "Uploaded cover for book $id: ${result.coverPath} (${result.width}x${result.height})"
                                        )
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                    part.release()
                }
            } catch (e: Exception) {
                uploadError = e.message
                logger.error("Error processing multipart for book $id", e)
            }

            val finalCoverPath = coverPath
            if (finalCoverPath != null) {
                call.respondSuccess(CoverUploadResponse(success = true, coverPath = finalCoverPath))
            } else {
                if (uploadError == ErrorCode.BOOK_NOT_FOUND.code) {
                    call.respondError(ErrorCode.BOOK_NOT_FOUND)
                } else if (uploadError != null) {
                    call.respondError(ErrorCode.BOOK_FILE_SAVE_FAILED, uploadError)
                } else {
                    call.respondError(ErrorCode.BOOK_NO_COVER_FILE)
                }
            }
        }

        post("/{id}/generate-cover") {
            call.requireAdminUser() ?: return@post
            val bookService = get<BookService>(BookService::class.java)

            val id = call.requiredIntParameter("id", ErrorCode.BOOK_INVALID_ID) ?: return@post

            when (val result = bookService.generateCover(id)) {
                CoverGenerateResult.BookNotFound -> call.respondError(ErrorCode.BOOK_NOT_FOUND)
                CoverGenerateResult.GenerationFailed -> call.respondError(ErrorCode.BOOK_COVER_GENERATION_FAILED)
                is CoverGenerateResult.Generated -> {
                    call.respondSuccess(CoverGenerateResponse(success = true, coverPath = result.coverPath))
                }
            }
        }
    }
}

private fun BookDocument.toChapterInfo(): ChapterInfo = ChapterInfo(
    index = index,
    title = title ?: "第${index + 1}章",
    wordCount = wordCount,
    imageCount = imageCount,
    level = level
)
