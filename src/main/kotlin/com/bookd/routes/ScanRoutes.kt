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

@Serializable
data class ScanStatusResponse(
    val scanning: Boolean,
    val sourceStatuses: Map<String, SourceScanStatus>
)

@Serializable
data class SourceScanStatus(
    val sourceId: Int,
    val scanning: Boolean,
    val found: Int,
    val imported: Int
)

fun Route.scanRoutes() {
    route("/api/scan") {
        // Get current scan status
        get("/status") {
            val scanService = get<BookScanService>(BookScanService::class.java)
            val scanning = scanService.isScanningInProgress()
            val statuses = scanService.getAllScanStatuses()
                .mapKeys { it.key.toString() }
                .mapValues { SourceScanStatus(it.value.sourceId, it.value.scanning, it.value.found, it.value.imported) }
            
            call.respond(ScanStatusResponse(scanning, statuses))
        }
        
        // 扫描所有启用的书籍源
        post("/all") {
            val scanService = get<BookScanService>(BookScanService::class.java)
            val fullScan = call.request.queryParameters["fullScan"]?.toBoolean() ?: false
            val result = scanService.scanAllSources(fullScan)
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
            
            val fullScan = call.request.queryParameters["fullScan"]?.toBoolean() ?: false
            val result = scanService.scanBookSource(id, fullScan)
            call.respond(ScanResponse(result.found, result.imported, result.message))
        }
    }
}
