package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.extension.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get

/**
 * APP 端专用路由
 * 
 * 与管理端路由分离，提供针对移动端优化的接口
 */

@Serializable
data class AppBooksResponse(
    val books: List<com.bookd.domain.model.Book>,
    val total: Int,
    val limit: Int,
    val offset: Long,
    val hasMore: Boolean
)

fun Route.appRoutes() {
    route("/api/app") {
        // 获取书籍列表（支持分页）
        get("/books") {
            val bookService = get<BookService>(BookService::class.java)
            val limit = call.intQueryParameter("limit", 20)
            val offset = call.longQueryParameter("offset", 0)
            val sourceId = call.optionalIntQueryParameter("sourceId")
            
            if (sourceId == null) {
                call.respondError(ErrorCode.SOURCE_INVALID_ID)
                return@get
            }
            
            val baseUrl = call.buildBaseUrl()
            
            // 获取分页书籍
            val books = bookService.getBooksBySourceIdPaged(sourceId, limit, offset)
                .map { it.withPublicCoverUrl(baseUrl) }
            
            // 获取总数
            val total = bookService.getCountBySourceId(sourceId).toInt()
            val hasMore = offset + books.size < total
            
            call.respondSuccess(AppBooksResponse(
                books = books,
                total = total,
                limit = limit,
                offset = offset,
                hasMore = hasMore
            ))
        }
        
        // 获取所有书源
        get("/sources") {
            val bookSourceService = get<BookSourceService>(BookSourceService::class.java)
            val sources = bookSourceService.getAllSources()
            call.respondSuccess(sources)
        }
    }
}
