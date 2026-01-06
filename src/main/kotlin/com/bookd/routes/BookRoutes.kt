package com.bookd.routes

import com.bookd.com.bookd.extension.buildBaseUrl
import com.bookd.data.repository.BookChapterRepository
import com.bookd.domain.service.BookService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.data.repository.BookRepository
import com.bookd.domain.service.BookContentService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

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
                // 构建完整的 URL
                val baseUrl = call.buildBaseUrl()
                val bookWithFullUrls = book.copy(
                    coverPath = book.coverPath?.let { path ->
                        if (path.startsWith("http")) path else "$baseUrl$path"
                    }
                )
                call.respond(bookWithFullUrls)
            }
        }
        
        // 获取书籍章节列表
        get("/{id}/chapters") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            val chapterRepository = get<BookChapterRepository>(BookChapterRepository::class.java)
            val contentService = get<BookContentService>(BookContentService::class.java)
            
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            // 1. 获取书籍信息
            val book = bookRepository.findById(id)
            if (book == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
                return@get
            }
            
            // 2. 如果未解析，触发按需解析
            if (!book.chaptersParsed) {
                try {
                    val parsed = contentService.parseOnDemand(id, book.filePath)
                    if (!parsed) {
                        call.respond(
                            HttpStatusCode.InternalServerError, 
                            mapOf("error" to "Failed to parse chapters")
                        )
                        return@get
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError, 
                        mapOf("error" to "Error parsing chapters: ${e.message}")
                    )
                    return@get
                }
            }
            
            // 3. 从数据库获取章节
            val chapters = chapterRepository.findByBookId(id)
            
            call.respond(
                ChaptersResponse(
                    bookId = id,
                    total = chapters.size,
                    chapters = chapters.map { chapter ->
                        ChapterInfo(
                            index = chapter.index,
                            title = chapter.title ?: "第${chapter.index + 1}章",
                            wordCount = chapter.wordCount,
                            imageCount = chapter.imageCount,
                            level = chapter.level
                        )
                    }
                )
            )
        }
        
        put("/{id}/metadata") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
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
                call.respond(UpdateMetadataResponse(success = true, book = book))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Book not found"))
            }
        }
        
        post("/{id}/cover") {
            val bookRepository = get<BookRepository>(BookRepository::class.java)
            val imageStorage = get<com.bookd.infrastructure.storage.BookImageStorage>(
                com.bookd.infrastructure.storage.BookImageStorage::class.java
            )
            
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
                                    uploadError = "Failed to save file: ${e.message}"
                                    logger.error("Error saving cover for book $id", e)
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }
            } catch (e: Exception) {
                uploadError = "Failed to process multipart: ${e.message}"
                logger.error("Error processing multipart for book $id", e)
            }
            
            val finalCoverPath = coverPath
            if (finalCoverPath != null) {
                bookRepository.updateMetadata(id, coverPath = finalCoverPath)
                call.respond(CoverUploadResponse(success = true, coverPath = finalCoverPath))
            } else {
                val errorMsg = uploadError ?: "No cover file provided"
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to errorMsg))
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
