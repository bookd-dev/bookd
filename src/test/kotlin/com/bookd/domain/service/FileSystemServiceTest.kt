package com.bookd.domain.service

import com.bookd.domain.model.ErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class FileSystemServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given directory path when listing then directories and files are returned in stable order`() {
        val service = FileSystemService()
        tempDir.resolve("z-file.txt").createFile().writeText("content")
        tempDir.resolve("a-dir").createDirectory()

        val result = service.listDirectory(tempDir.toString())

        assertTrue(result is DirectoryListResult.Success)
        result as DirectoryListResult.Success
        assertEquals("a-dir", result.response.directories.single().name)
        assertEquals("z-file.txt", result.response.files.single().name)
    }

    @Test
    fun `given async directory list when directory exists then response matches blocking path`() = runBlocking {
        val service = FileSystemService()
        tempDir.resolve("async.txt").createFile().writeText("content")

        val result = service.listDirectoryAsync(tempDir.toString())

        assertTrue(result is DirectoryListResult.Success)
        result as DirectoryListResult.Success
        assertEquals("async.txt", result.response.files.single().name)
    }

    @Test
    fun `given missing directory when listing then not found error is returned`() {
        val service = FileSystemService()

        val result = service.listDirectory(tempDir.resolve("missing").toString())

        assertTrue(result is DirectoryListResult.Failure)
        result as DirectoryListResult.Failure
        assertEquals(ErrorCode.FS_DIR_NOT_FOUND, result.errorCode)
    }
}
