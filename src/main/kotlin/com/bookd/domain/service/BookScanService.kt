package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.Book
import org.slf4j.LoggerFactory
import java.io.File

class BookScanService(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository
) {
    private val logger = LoggerFactory.getLogger(BookScanService::class.java)
    private val supportedFormats = setOf("txt", "epub", "mobi", "azw3", "pdf")
    
    /**
     * 扫描指定书籍源
     */
    fun scanBookSource(sourceId: Int): ScanResult {
        val source = bookSourceRepository.findById(sourceId)
        if (source == null) {
            logger.warn("Book source not found: $sourceId")
            return ScanResult(0, 0, "Book source not found")
        }
        
        if (!source.enabled) {
            logger.info("Book source is disabled: ${source.name}")
            return ScanResult(0, 0, "Book source is disabled")
        }
        
        return scanDirectory(source.path, source.id)
    }
    
    /**
     * 扫描所有启用的书籍源
     */
    fun scanAllSources(): ScanResult {
        val sources = bookSourceRepository.findAll().filter { it.enabled }
        logger.info("Scanning ${sources.size} enabled book sources")
        
        var totalFound = 0
        var totalImported = 0
        
        sources.forEach { source ->
            val result = scanDirectory(source.path, source.id)
            totalFound += result.found
            totalImported += result.imported
            logger.info("Scanned ${source.name}: found ${result.found}, imported ${result.imported}")
        }
        
        return ScanResult(totalFound, totalImported, "Scan completed")
    }
    
    /**
     * 扫描目录并导入书籍
     */
    private fun scanDirectory(path: String, sourceId: Int? = null): ScanResult {
        val directory = File(path)
        
        if (!directory.exists()) {
            logger.warn("Directory does not exist: $path")
            return ScanResult(0, 0, "Directory does not exist: $path")
        }
        
        if (!directory.isDirectory) {
            logger.warn("Path is not a directory: $path")
            return ScanResult(0, 0, "Path is not a directory")
        }
        
        var found = 0
        var imported = 0
        
        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { file ->
                    val extension = file.extension.lowercase()
                    supportedFormats.contains(extension)
                }
                .forEach { file ->
                    found++
                    try {
                        val book = importBook(file, sourceId)
                        if (book != null) {
                            imported++
                            logger.debug("Imported: ${file.name}")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to import ${file.absolutePath}", e)
                    }
                }
        } catch (e: Exception) {
            logger.error("Error scanning directory: $path", e)
            return ScanResult(found, imported, "Error: ${e.message}")
        }
        
        return ScanResult(found, imported, "Success")
    }
    
    /**
     * 导入单个书籍文件
     */
    private fun importBook(file: File, sourceId: Int? = null): Book? {
        val filePath = file.absolutePath
        
        // 检查是否已存在
        val existing = bookRepository.findByFilePath(filePath)
        if (existing != null) {
            return null // 已存在，跳过
        }
        
        // 提取基本信息
        val fileName = file.nameWithoutExtension
        val format = file.extension.lowercase()
        val fileSize = file.length()
        
        // TODO: 使用 Apache Tika 解析元数据
        // 目前使用文件名作为标题
        val title = fileName
        val author = extractAuthorFromFileName(fileName)
        
        return try {
            bookRepository.create(
                title = title,
                author = author,
                format = format,
                filePath = filePath,
                fileSize = fileSize,
                sourceId = sourceId
            )
        } catch (e: Exception) {
            logger.error("Failed to create book: $fileName", e)
            null
        }
    }
    
    /**
     * 从文件名提取作者（简单规则）
     */
    private fun extractAuthorFromFileName(fileName: String): String? {
        // 常见格式: "书名-作者", "作者-书名", "[作者]书名"
        return when {
            fileName.contains("-") -> {
                val parts = fileName.split("-")
                if (parts.size >= 2) parts[1].trim() else null
            }
            fileName.contains("[") && fileName.contains("]") -> {
                val start = fileName.indexOf("[")
                val end = fileName.indexOf("]")
                if (end > start) fileName.substring(start + 1, end).trim() else null
            }
            else -> null
        }
    }
}

data class ScanResult(
    val found: Int,
    val imported: Int,
    val message: String
)
