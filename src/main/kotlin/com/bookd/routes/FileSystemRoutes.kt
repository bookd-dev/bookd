package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.DirectoryListResult
import com.bookd.domain.service.FileSystemService
import com.bookd.extension.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.fileSystemRoutes() {
    route("/api/filesystem") {
        // 列出目录内容
        get("/list") {
            call.requireAdminUser() ?: return@get
            val fileSystemService = get<FileSystemService>(FileSystemService::class.java)
            val path = call.request.queryParameters["path"] ?: "/"

            when (val result = fileSystemService.listDirectoryAsync(path)) {
                is DirectoryListResult.Success -> call.respondSuccess(result.response)
                is DirectoryListResult.Failure -> call.respondError(result.errorCode, result.details)
            }
        }

        // 验证路径是否存在
        get("/validate") {
            call.requireAdminUser() ?: return@get
            val fileSystemService = get<FileSystemService>(FileSystemService::class.java)
            val path = call.request.queryParameters["path"]

            if (path.isNullOrBlank()) {
                call.respondError(ErrorCode.FS_PATH_REQUIRED)
                return@get
            }

            call.respondSuccess(fileSystemService.validatePathAsync(path))
        }

        // 获取根目录列表（用于初始化）
        get("/roots") {
            call.requireAdminUser() ?: return@get
            val fileSystemService = get<FileSystemService>(FileSystemService::class.java)
            call.respondSuccess(fileSystemService.rootsAsync())
        }
    }
}
