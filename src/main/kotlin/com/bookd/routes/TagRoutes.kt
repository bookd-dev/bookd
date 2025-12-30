package com.bookd.routes

import com.bookd.domain.service.TagService
import com.bookd.domain.service.BookService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get

@Serializable
data class AddTagRequest(
    val tagName: String
)

@Serializable
data class CreateTagRequest(
    val name: String
)

@Serializable
data class MergeTagsRequest(
    val sourceTagIds: List<Int>,
    val targetTagName: String
)

@Serializable
data class MergeTagsResponse(
    val success: Boolean,
    val targetTag: TagWithStats,
    val mergedCount: Int
)

@Serializable
data class AutoTagResponse(
    val success: Boolean,
    val booksTagged: Int,
    val tagsCreated: Int,
    val totalBooks: Int
)

@Serializable
data class TagWithStats(
    val id: Int,
    val name: String,
    val bookCount: Int,
    val createdAt: String
)

fun Route.tagRoutes() {
    route("/api/tags") {
        // Get all tags with statistics
        get {
            val tagService = get<TagService>(TagService::class.java)
            val tagStats = tagService.getTagStats()
            val response = tagStats.map { (tag, count) ->
                TagWithStats(
                    id = tag.id,
                    name = tag.name,
                    bookCount = count,
                    createdAt = tag.createdAt?.toString() ?: ""
                )
            }
            call.respond(response)
        }
        
        // Create a new tag manually
        post {
            val tagService = get<TagService>(TagService::class.java)
            val request = call.receive<CreateTagRequest>()
            
            if (request.name.trim().isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tag name cannot be empty"))
                return@post
            }
            
            val tag = tagService.createTag(request.name.trim())
            call.respond(HttpStatusCode.Created, tag)
        }
        
        // Merge multiple tags into one
        post("/merge") {
            val tagService = get<TagService>(TagService::class.java)
            val request = call.receive<MergeTagsRequest>()
            
            if (request.sourceTagIds.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Source tag IDs cannot be empty"))
                return@post
            }
            
            if (request.targetTagName.trim().isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Target tag name cannot be empty"))
                return@post
            }
            
            val result = tagService.mergeTags(request.sourceTagIds, request.targetTagName.trim())
            
            val tagStats = tagService.getTagStats()
            val targetTagWithStats = tagStats.entries.find { it.key.name == result.name }
            
            if (targetTagWithStats != null) {
                call.respond(MergeTagsResponse(
                    success = true,
                    targetTag = TagWithStats(
                        id = targetTagWithStats.key.id,
                        name = targetTagWithStats.key.name,
                        bookCount = targetTagWithStats.value,
                        createdAt = targetTagWithStats.key.createdAt?.toString() ?: ""
                    ),
                    mergedCount = request.sourceTagIds.size
                ))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to retrieve merged tag"))
            }
        }
        
        // Get tags for a specific book
        get("/book/{bookId}") {
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@get
            }
            
            val tags = tagService.getTagsForBook(bookId)
            call.respond(tags)
        }
        
        // Add tag to book
        post("/book/{bookId}") {
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@post
            }
            
            val request = call.receive<AddTagRequest>()
            val tag = tagService.addTagToBook(bookId, request.tagName)
            call.respond(HttpStatusCode.Created, tag)
        }
        
        // Remove tag from book
        delete("/book/{bookId}/{tagId}") {
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            val tagId = call.parameters["tagId"]?.toIntOrNull()
            
            if (bookId == null || tagId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID or tag ID"))
                return@delete
            }
            
            val removed = tagService.removeTagFromBook(bookId, tagId)
            if (removed) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Tag not found for this book"))
            }
        }
        
        // Auto-tag a specific book
        post("/auto-tag/book/{bookId}") {
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.parameters["bookId"]?.toIntOrNull()
            if (bookId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid book ID"))
                return@post
            }
            
            val tags = tagService.autoTagBook(bookId)
            call.respond(mapOf("tags" to tags, "count" to tags.size))
        }
        
        // Auto-tag all books
        post("/auto-tag/all") {
            val tagService = get<TagService>(TagService::class.java)
            val result = tagService.autoTagAllBooks()
            
            call.respond(AutoTagResponse(
                success = true,
                booksTagged = result["booksTagged"] ?: 0,
                tagsCreated = result["tagsCreated"] ?: 0,
                totalBooks = result["totalBooks"] ?: 0
            ))
        }
        
        // Get books by tag
        get("/{tagId}/books") {
            val tagService = get<TagService>(TagService::class.java)
            val bookService = get<BookService>(BookService::class.java)
            val tagId = call.parameters["tagId"]?.toIntOrNull()
            
            if (tagId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tag ID"))
                return@get
            }
            
            val bookIds = tagService.getBooksByTagId(tagId)
            val books = bookIds.mapNotNull { bookService.getBookById(it) }
            call.respond(books)
        }
        
        // Delete a tag
        delete("/{tagId}") {
            val tagService = get<TagService>(TagService::class.java)
            val tagId = call.parameters["tagId"]?.toIntOrNull()
            
            if (tagId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tag ID"))
                return@delete
            }
            
            val deleted = tagService.deleteTag(tagId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Tag not found"))
            }
        }
    }
}
