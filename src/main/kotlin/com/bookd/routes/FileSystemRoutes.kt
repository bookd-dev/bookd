package com.bookd.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DirectoryInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val size: Long = 0
)

@Serializable
data class DirectoryListResponse(
    val currentPath: String,
    val parentPath: String?,
    val directories: List<DirectoryInfo>,
    val files: List<DirectoryInfo>
)

fun Route.fileSystemRoutes() {
    route("/api/filesystem") {
        // 列出目录内容
        get("/list") {
            val path = call.request.queryParameters["path"] ?: "/"
            
            val dir = File(path)
            
            if (!dir.exists()) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Directory not found"))
                return@get
            }
            
            if (!dir.isDirectory) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Path is not a directory"))
                return@get
            }
            
            if (!dir.canRead()) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Permission denied"))
                return@get
            }
            
            try {
                val items = dir.listFiles()?.sortedWith(
                    compareBy<File> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                ) ?: emptyList()
                
                val directories = items.filter { it.isDirectory }.map { file ->
                    DirectoryInfo(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = true,
                        canRead = file.canRead()
                    )
                }
                
                val files = items.filter { !it.isDirectory }.map { file ->
                    DirectoryInfo(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = false,
                        canRead = file.canRead(),
                        size = file.length()
                    )
                }
                
                val parentPath = dir.parentFile?.absolutePath
                
                call.respond(DirectoryListResponse(
                    currentPath = dir.absolutePath,
                    parentPath = parentPath,
                    directories = directories,
                    files = files
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to list directory: ${e.message}")
                )
            }
        }
        
        // 验证路径是否存在
        get("/validate") {
            val path = call.request.queryParameters["path"]
            
            if (path.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Path parameter is required"))
                return@get
            }
            
            val dir = File(path)
            
            @Serializable
            data class ValidationResponse(
                val path: String,
                val exists: Boolean,
                val isDirectory: Boolean,
                val canRead: Boolean,
                val isValid: Boolean
            )
            
            call.respond(ValidationResponse(
                path = path,
                exists = dir.exists(),
                isDirectory = dir.isDirectory,
                canRead = dir.canRead(),
                isValid = (dir.exists() && dir.isDirectory && dir.canRead())
            ))
        }
        
        // 获取根目录列表（用于初始化）
        get("/roots") {
            val roots = File.listRoots()
            
            val rootInfos = roots.map { root ->
                DirectoryInfo(
                    path = root.absolutePath,
                    name = root.absolutePath,
                    isDirectory = true,
                    canRead = root.canRead()
                )
            }
            
            call.respond(mapOf("roots" to rootInfos))
        }
    }
}
