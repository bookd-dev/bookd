package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.TagService
import com.bookd.domain.service.BookService
import com.bookd.extension.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
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

@Serializable
data class AutoTagBookResponse(
    val tags: List<com.bookd.domain.model.Tag>,
    val count: Int
)

fun Route.tagRoutes() {
    route("/api/tags") {
        // Get all tags with statistics
        get {
            val tagService = get<TagService>(TagService::class.java)
            val tagStats = tagService.getTagStatsAsync()
            val response = tagStats.map { (tag, count) ->
                TagWithStats(
                    id = tag.id,
                    name = tag.name,
                    bookCount = count,
                    createdAt = tag.createdAt?.toString() ?: ""
                )
            }
            call.respondSuccess(response)
        }

        // Create a new tag manually
        post {
            call.requireAdminUser() ?: return@post
            val tagService = get<TagService>(TagService::class.java)
            val request = call.receive<CreateTagRequest>()

            if (request.name.trim().isEmpty()) {
                call.respondError(ErrorCode.TAG_NAME_EMPTY)
                return@post
            }

            val tag = tagService.createTagAsync(request.name.trim())
            call.respondSuccess(HttpStatusCode.Created, tag)
        }

        // Merge multiple tags into one
        post("/merge") {
            call.requireAdminUser() ?: return@post
            val tagService = get<TagService>(TagService::class.java)
            val request = call.receive<MergeTagsRequest>()

            if (request.sourceTagIds.isEmpty()) {
                call.respondError(ErrorCode.TAG_SOURCE_IDS_EMPTY)
                return@post
            }

            if (request.targetTagName.trim().isEmpty()) {
                call.respondError(ErrorCode.TAG_TARGET_NAME_EMPTY)
                return@post
            }

            val result = tagService.mergeTagsAsync(request.sourceTagIds, request.targetTagName.trim())

            val tagStats = tagService.getTagStatsAsync()
            val targetTagWithStats = tagStats.entries.find { it.key.name == result.name }

            if (targetTagWithStats != null) {
                call.respondSuccess(MergeTagsResponse(
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
                call.respondError(ErrorCode.TAG_MERGE_FAILED)
            }
        }

        // Get tags for a specific book
        get("/book/{bookId}") {
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.requiredIntParameter("bookId", ErrorCode.BOOK_INVALID_ID) ?: return@get

            val tags = tagService.getTagsForBookAsync(bookId)
            call.respondSuccess(tags)
        }

        // Add tag to book
        post("/book/{bookId}") {
            call.requireAdminUser() ?: return@post
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.requiredIntParameter("bookId", ErrorCode.BOOK_INVALID_ID) ?: return@post

            val request = call.receive<AddTagRequest>()
            val tag = tagService.addTagToBookAsync(bookId, request.tagName)
            call.respondSuccess(HttpStatusCode.Created, tag)
        }

        // Remove tag from book
        delete("/book/{bookId}/{tagId}") {
            call.requireAdminUser() ?: return@delete
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.requiredIntParameter("bookId", ErrorCode.TAG_INVALID_BOOK_OR_TAG_ID) ?: return@delete
            val tagId = call.requiredIntParameter("tagId", ErrorCode.TAG_INVALID_BOOK_OR_TAG_ID) ?: return@delete

            val removed = tagService.removeTagFromBookAsync(bookId, tagId)
            if (removed) {
                call.respondNoContent()
            } else {
                call.respondError(ErrorCode.TAG_NOT_FOUND_FOR_BOOK)
            }
        }

        // Auto-tag a specific book
        post("/auto-tag/book/{bookId}") {
            call.requireAdminUser() ?: return@post
            val tagService = get<TagService>(TagService::class.java)
            val bookId = call.requiredIntParameter("bookId", ErrorCode.BOOK_INVALID_ID) ?: return@post

            val tags = tagService.autoTagBookAsync(bookId)
            call.respondSuccess(AutoTagBookResponse(tags = tags, count = tags.size))
        }

        // Auto-tag all books
        post("/auto-tag/all") {
            call.requireAdminUser() ?: return@post
            val tagService = get<TagService>(TagService::class.java)
            val result = tagService.autoTagAllBooksAsync()

            call.respondSuccess(AutoTagResponse(
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
            val tagId = call.requiredIntParameter("tagId", ErrorCode.TAG_INVALID_ID) ?: return@get

            val bookIds = tagService.getBooksByTagIdAsync(tagId)
            val books = bookService.getBooksByIds(bookIds)
            call.respondSuccess(books)
        }

        // Delete a tag
        delete("/{tagId}") {
            call.requireAdminUser() ?: return@delete
            val tagService = get<TagService>(TagService::class.java)
            val tagId = call.requiredIntParameter("tagId", ErrorCode.TAG_INVALID_ID) ?: return@delete

            val deleted = tagService.deleteTagAsync(tagId)
            if (deleted) {
                call.respondNoContent()
            } else {
                call.respondError(ErrorCode.TAG_NOT_FOUND)
            }
        }
    }
}
