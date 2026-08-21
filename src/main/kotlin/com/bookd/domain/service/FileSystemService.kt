package com.bookd.domain.service

import com.bookd.domain.model.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

class FileSystemService {
    suspend fun listDirectoryAsync(path: String): DirectoryListResult = withContext(Dispatchers.IO) {
        listDirectory(path)
    }

    fun listDirectory(path: String): DirectoryListResult {
        val dir = File(path)

        if (!dir.exists()) return DirectoryListResult.Failure(ErrorCode.FS_DIR_NOT_FOUND)
        if (!dir.isDirectory) return DirectoryListResult.Failure(ErrorCode.FS_NOT_A_DIRECTORY)
        if (!dir.canRead()) return DirectoryListResult.Failure(ErrorCode.FS_PERMISSION_DENIED)

        return try {
            val items = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            ) ?: emptyList()

            DirectoryListResult.Success(
                DirectoryListResponse(
                    currentPath = dir.absolutePath,
                    parentPath = dir.parentFile?.absolutePath,
                    directories = items.filter { it.isDirectory }.map { file ->
                        DirectoryInfo(
                            path = file.absolutePath,
                            name = file.name,
                            isDirectory = true,
                            canRead = file.canRead()
                        )
                    },
                    files = items.filter { !it.isDirectory }.map { file ->
                        DirectoryInfo(
                            path = file.absolutePath,
                            name = file.name,
                            isDirectory = false,
                            canRead = file.canRead(),
                            size = file.length()
                        )
                    }
                )
            )
        } catch (e: Exception) {
            DirectoryListResult.Failure(ErrorCode.FS_LIST_FAILED, e.message)
        }
    }

    suspend fun validatePathAsync(path: String): ValidationResponse = withContext(Dispatchers.IO) {
        validatePath(path)
    }

    fun validatePath(path: String): ValidationResponse {
        val dir = File(path)
        return ValidationResponse(
            path = path,
            exists = dir.exists(),
            isDirectory = dir.isDirectory,
            canRead = dir.canRead(),
            isValid = (dir.exists() && dir.isDirectory && dir.canRead())
        )
    }

    suspend fun rootsAsync(): RootsResponse = withContext(Dispatchers.IO) {
        roots()
    }

    fun roots(): RootsResponse {
        val rootInfos = File.listRoots().map { root ->
            DirectoryInfo(
                path = root.absolutePath,
                name = root.absolutePath,
                isDirectory = true,
                canRead = root.canRead()
            )
        }
        return RootsResponse(roots = rootInfos)
    }
}

sealed class DirectoryListResult {
    data class Success(val response: DirectoryListResponse) : DirectoryListResult()
    data class Failure(val errorCode: ErrorCode, val details: String? = null) : DirectoryListResult()
}

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

@Serializable
data class ValidationResponse(
    val path: String,
    val exists: Boolean,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val isValid: Boolean
)

@Serializable
data class RootsResponse(
    val roots: List<DirectoryInfo>
)
