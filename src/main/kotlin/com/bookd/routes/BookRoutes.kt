package com.bookd.routes

import com.bookd.com.bookd.extension.buildBaseUrl
import com.bookd.data.repository.BookDocumentRepository
import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BookService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.domain.service.BookDetailService
import com.bookd.domain.service.UserService
import com.bookd.data.repository.BookRepository
import com.bookd.domain.service.BookContentService
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

            call.respondSuccess(BooksResponse(books, total))
        }

        get("/count") {
            val bookService = get<BookService>(BookService::class.java)
            val sourceId = call.request.queryParameters["sourceId"]?.toIntOrNull()

            val count = if (sourceId != null) {
                bookService.getCountBySourceId(sourceId)
            } else {
                bookService.getTotalCount()
            }

            call.respondSuccess(BookCountResponse(count))
        }

        get("/{id}") {
            val bookService = get<BookService>(BookService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            val book = bookService.getBookById(id)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
            } else {
                // 构建完整的 URL
                val baseUrl = call.buildBaseUrl()
                val bookWithFullUrls = book.copy(
                    coverPath = book.coverPath?.let { path ->
                        if (path.startsWith("http")) path else "$baseUrl$path"
                    }
                )
                call.respondSuccess(bookWithFullUrls)
            }
        }

        // 获取书籍详情（包含标签、阅读进度、书架信息）- 需要认证
        get("/{id}/detail") {
            val userService = get<UserService>(UserService::class.java)
            val bookDetailService = get<BookDetailService>(BookDetailService::class.java)
            
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }
            
            // 获取当前用户
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                call.respondError(ErrorCode.AUTH_NO_TOKEN)
                return@get
            }
            
            val user = userService.validateToken(token)
            if (user == null) {
                call.respondError(ErrorCode.AUTH_INVALID_TOKEN)
                return@get
            }
            
            val bookDetail = bookDetailService.getBookDetail(id, user.id)
            if (bookDetail == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@get
            }
            
            // 构建完整的封面 URL
            val baseUrl = call.buildBaseUrl()
            val bookWithFullUrls = bookDetail.book.copy(
                coverPath = bookDetail.book.coverPath?.let { path ->
                    if (path.startsWith("http")) path else "$baseUrl$path"
                }
            )
            
            call.respondSuccess(bookDetail.copy(book = bookWithFullUrls))
        }

        // 获取书籍章节列表（仅目录项）
        get("/{id}/chapters") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            val documentRepository = get<BookDocumentRepository>(BookDocumentRepository::class.java)
            val contentService = get<BookContentService>(BookContentService::class.java)

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@get
            }

            // 1. 获取书籍信息
            val book = bookRepository.findById(id)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@get
            }

            // 2. 如果未解析，触发按需解析
            if (!book.chaptersParsed) {
                try {
                    val parsed = contentService.parseOnDemand(id, book.filePath)
                    if (!parsed) {
                        call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED)
                        return@get
                    }
                } catch (e: Exception) {
                    call.respondError(ErrorCode.BOOK_PARSE_CHAPTERS_FAILED, e.message)
                    return@get
                }
            }

            // 3. 从数据库获取目录项（仅 inToc=true）
            val tocDocuments = documentRepository.findTocByBookId(id)

            call.respondSuccess(
                ChaptersResponse(
                    bookId = id,
                    total = tocDocuments.size,
                    chapters = tocDocuments.map { doc ->
                        ChapterInfo(
                            index = doc.index,
                            title = doc.title ?: "第${doc.index + 1}章",
                            wordCount = doc.wordCount,
                            imageCount = doc.imageCount,
                            level = doc.level
                        )
                    }
                )
            )
        }

        put("/{id}/metadata") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@put
            }

            val request = call.receive<UpdateMetadataRequest>()

            val updated = bookRepository.updateMetadata(
                id = id,
                author = request.author,
                coverPath = request.coverPath,
                isbn = request.isbn,
                publisher = request.publisher,
                description = request.description
            )

            if (updated > 0) {
                val book = bookRepository.findById(id)
                call.respondSuccess(UpdateMetadataResponse(success = true, book = book))
            } else {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
            }
        }

        post("/{id}/cover") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            val imageStorage = get<com.bookd.infrastructure.storage.BookImageStorage>(
                com.bookd.infrastructure.storage.BookImageStorage::class.java
            )

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@post
            }

            val book = bookRepository.findById(id)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@post
            }

            var coverPath: String? = null
            var uploadError: String? = null

            try {
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            if (part.name == "cover") {
                                try {
                                    val fileBytes = part.provider().toInputStream().readBytes()
                                    val ext = part.originalFileName?.substringAfterLast('.') ?: "jpg"

                                    // 使用 BookImageStorage 保存封面
                                    coverPath = imageStorage.saveCover(id, "cover.$ext", fileBytes, isGenerated = false)

                                    logger.info("Uploaded cover for book $id: $coverPath")
                                } catch (e: Exception) {
                                    uploadError = e.message
                                    logger.error("Error saving cover for book $id", e)
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }
            } catch (e: Exception) {
                uploadError = e.message
                logger.error("Error processing multipart for book $id", e)
            }

            val finalCoverPath = coverPath
            if (finalCoverPath != null) {
                bookRepository.updateMetadata(id, coverPath = finalCoverPath)
                call.respondSuccess(CoverUploadResponse(success = true, coverPath = finalCoverPath))
            } else {
                if (uploadError != null) {
                    call.respondError(ErrorCode.BOOK_FILE_SAVE_FAILED, uploadError)
                } else {
                    call.respondError(ErrorCode.BOOK_NO_COVER_FILE)
                }
            }
        }

        post("/{id}/generate-cover") {
            val coverGenerator = get<CoverGeneratorService>(CoverGeneratorService::class.java)
            val bookRepository = get<BookRepository>(BookRepository::class.java)

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.BOOK_INVALID_ID)
                return@post
            }

            val book = bookRepository.findById(id)
            if (book == null) {
                call.respondError(ErrorCode.BOOK_NOT_FOUND)
                return@post
            }

            val generatedCoverPath = coverGenerator.generateCover(id, book.title, book.author)
            if (generatedCoverPath != null) {
                // Update book with generated cover path
                bookRepository.updateMetadata(id, coverPath = generatedCoverPath)
                call.respondSuccess(CoverGenerateResponse(success = true, coverPath = generatedCoverPath))
            } else {
                call.respondError(ErrorCode.BOOK_COVER_GENERATION_FAILED)
            }
        }
    }
}
