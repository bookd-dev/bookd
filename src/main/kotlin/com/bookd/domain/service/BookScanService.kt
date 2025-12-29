package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.Book
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BookScanService(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val metadataService: BookMetadataService
) {
    private val logger = LoggerFactory.getLogger(BookScanService::class.java)
    private val supportedFormats = setOf("txt", "epub", "mobi", "azw3", "pdf")
    
    // Track scanning status
    private val scanningInProgress = AtomicBoolean(false)
    private val sourceScanStatus = ConcurrentHashMap<Int, ScanStatus>()
    
    /**
     * Check if a scan is currently in progress
     */
    fun isScanningInProgress(): Boolean = scanningInProgress.get()
    
    /**
     * Get scan status for a specific source
     */
    fun getScanStatus(sourceId: Int): ScanStatus? = sourceScanStatus[sourceId]
    
    /**
     * Get all active scan statuses
     */
    fun getAllScanStatuses(): Map<Int, ScanStatus> = sourceScanStatus.toMap()
    
    /**
     * 扫描指定书籍源
     */
    fun scanBookSource(sourceId: Int): ScanResult {
        // Check if already scanning this source
        if (sourceScanStatus.containsKey(sourceId)) {
            logger.warn("Source $sourceId is already being scanned")
            return ScanResult(0, 0, "Source is already being scanned")
        }
        
        val source = bookSourceRepository.findById(sourceId)
        if (source == null) {
            logger.warn("Book source not found: $sourceId")
            return ScanResult(0, 0, "Book source not found")
        }
        
        if (!source.enabled) {
            logger.info("Book source is disabled: ${source.name}")
            return ScanResult(0, 0, "Book source is disabled")
        }
        
        // Mark as scanning
        sourceScanStatus[sourceId] = ScanStatus(sourceId, true, 0, 0)
        scanningInProgress.set(true)
        
        return try {
            val result = scanDirectory(source.path, source.id)
            result
        } finally {
            // Clear scanning status
            sourceScanStatus.remove(sourceId)
            if (sourceScanStatus.isEmpty()) {
                scanningInProgress.set(false)
            }
        }
    }
    
    /**
     * 扫描所有启用的书籍源
     */
    fun scanAllSources(): ScanResult {
        if (scanningInProgress.get()) {
            logger.warn("A scan is already in progress")
            return ScanResult(0, 0, "A scan is already in progress")
        }
        
        val sources = bookSourceRepository.findAll().filter { it.enabled }
        logger.info("Scanning ${sources.size} enabled book sources")
        
        scanningInProgress.set(true)
        
        return try {
            var totalFound = 0
            var totalImported = 0
            
            sources.forEach { source ->
                sourceScanStatus[source.id] = ScanStatus(source.id, true, 0, 0)
                val result = scanDirectory(source.path, source.id)
                totalFound += result.found
                totalImported += result.imported
                logger.info("Scanned ${source.name}: found ${result.found}, imported ${result.imported}")
                sourceScanStatus.remove(source.id)
            }
            
            ScanResult(totalFound, totalImported, "Scan completed")
        } finally {
            scanningInProgress.set(false)
            sourceScanStatus.clear()
        }
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
                    // Update scan status
                    sourceId?.let { 
                        sourceScanStatus[it] = ScanStatus(it, true, found, imported)
                    }
                    
                    try {
                        val book = importBook(file, sourceId)
                        if (book != null) {
                            imported++
                            // Update scan status
                            sourceId?.let { 
                                sourceScanStatus[it] = ScanStatus(it, true, found, imported)
                            }
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
        
        // 使用文件名作为初始标题
        val title = fileName
        val author = extractAuthorFromFileName(fileName)
        
        return try {
            val book = bookRepository.create(
                title = title,
                author = author,
                format = format,
                filePath = filePath,
                fileSize = fileSize,
                sourceId = sourceId
            )
            
            // 异步提取详细元数据（不影响主流程）
            try {
                metadataService.extractMetadataAsync(book.id, filePath)
            } catch (e: Exception) {
                logger.error("Failed to start metadata extraction for $fileName", e)
                // 不影响主流程，继续
            }
            
            book
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

data class ScanStatus(
    val sourceId: Int,
    val scanning: Boolean,
    val found: Int,
    val imported: Int
)
