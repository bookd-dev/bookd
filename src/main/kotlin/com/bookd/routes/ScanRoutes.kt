package com.bookd.routes

import com.bookd.domain.service.BookScanService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.get

@Serializable
data class ScanResponse(
    val found: Int,
    val imported: Int,
    val message: String
)

fun Route.scanRoutes() {
    route("/api/scan") {
        // 扫描所有启用的书籍源
        post("/all") {
            val scanService = get<BookScanService>(BookScanService::class.java)
            val result = scanService.scanAllSources()
            call.respond(ScanResponse(result.found, result.imported, result.message))
        }
        
        // 扫描指定书籍源
        post("/source/{id}") {
            val scanService = get<BookScanService>(BookScanService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
                return@post
            }
            
            val result = scanService.scanBookSource(id)
            call.respond(ScanResponse(result.found, result.imported, result.message))
        }
    }
}
