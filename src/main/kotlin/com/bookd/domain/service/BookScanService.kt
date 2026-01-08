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
    private val metadataService: BookMetadataService,
    private val contentService: BookContentService
) {
    private val logger = LoggerFactory.getLogger(BookScanService::class.java)
    private val supportedFormats = setOf("txt", "epub")
    
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
     * @param fullScan true=强制全量扫描(重新导入所有书籍), false=只扫描新增书籍(跳过已存在的书籍)
     */
    fun scanBookSource(sourceId: Int, fullScan: Boolean = false): ScanResult {
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
            val result = scanDirectory(source.path, source.id, fullScan)
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
     * @param fullScan true=强制全量扫描(重新导入所有书籍), false=只扫描新增书籍(跳过已存在的书籍)
     */
    fun scanAllSources(fullScan: Boolean = false): ScanResult {
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
                val result = scanDirectory(source.path, source.id, fullScan)
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
     * @param fullScan true=强制全量扫描(重新提取元数据+清理不存在的文件), false=只导入新书籍
     */
    private fun scanDirectory(path: String, sourceId: Int? = null, fullScan: Boolean = false): ScanResult {
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
        var deleted = 0
        
        // 全量扫描：获取该源的所有现有书籍，用于后续清理
        val existingBooks = if (fullScan && sourceId != null) {
            bookRepository.findBySourceId(sourceId)
        } else {
            emptyList()
        }
        val scannedFilePaths = mutableSetOf<String>()
        
        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { file ->
                    val extension = file.extension.lowercase()
                    supportedFormats.contains(extension)
                }
                .forEach { file ->
                    found++
                    val filePath = file.absolutePath
                    scannedFilePaths.add(filePath)
                    
                    // Update scan status
                    sourceId?.let { 
                        sourceScanStatus[it] = ScanStatus(it, true, found, imported)
                    }
                    
                    try {
                        val book = importBook(file, sourceId, fullScan)
                        if (book != null) {
                            imported++
                            // Update scan status
                            sourceId?.let { 
                                sourceScanStatus[it] = ScanStatus(it, true, found, imported)
                            }
                            logger.debug("Processed: ${file.name}")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to import ${file.absolutePath}", e)
                    }
                }
            
            // 全量扫描：删除文件已不存在的书籍
            if (fullScan && sourceId != null) {
                existingBooks.forEach { book ->
                    if (book.filePath !in scannedFilePaths) {
                        logger.info("Deleting book with missing file: ${book.title} (${book.filePath})")
                        bookRepository.deleteById(book.id)
                        deleted++
                    }
                }
                if (deleted > 0) {
                    logger.info("Deleted $deleted books with missing files")
                }
            }
        } catch (e: Exception) {
            logger.error("Error scanning directory: $path", e)
            return ScanResult(found, imported, "Error: ${e.message}")
        }
        
        val message = if (deleted > 0) {
            "Success (deleted $deleted missing files)"
        } else {
            "Success"
        }
        return ScanResult(found, imported, message)
    }
    
    /**
     * 导入单个书籍文件
     * @param fullScan true=强制重新提取元数据, false=跳过已存在的书籍
     */
    private fun importBook(file: File, sourceId: Int? = null, fullScan: Boolean = false): Book? {
        val filePath = file.absolutePath
        
        // 检查是否已存在
        val existing = bookRepository.findByFilePath(filePath)
        if (existing != null) {
            if (fullScan) {
                // 全量扫描模式：强制重新提取元数据
                logger.info("Full scan mode: Re-extracting metadata for existing book: ${file.name}")
                try {
                    metadataService.extractMetadataAsync(existing.id, filePath)
                } catch (e: Exception) {
                    logger.error("Failed to start metadata extraction for existing book: ${file.name}", e)
                }
                
                // 注意：不再自动解析内容，改为按需解析
                // 用户打开书籍时才会触发解析
                
                return existing // 返回已存在的书籍，表示已处理
            } else {
                // 增量扫描模式：只对缺失元数据的书籍提取
                if (existing.author == null || existing.author.isBlank()) {
                    logger.info("Book already exists but missing metadata, triggering extraction for: ${file.name}")
                    try {
                        metadataService.extractMetadataAsync(existing.id, filePath)
                    } catch (e: Exception) {
                        logger.error("Failed to start metadata extraction for existing book: ${file.name}", e)
                    }
                }
                return null // 已存在，跳过
            }
        }
        
        // 提取基本信息
        val fileName = file.nameWithoutExtension
        val format = file.extension.lowercase()
        val fileSize = file.length()
        
        // 直接使用文件名作为标题,让元数据提取器后续更新
        // 不从文件名猜测作者,避免错误提取
        
        return try {
            val book = bookRepository.create(
                title = fileName,  // 使用完整文件名作为初始标题
                author = null,     // 不猜测作者,等待元数据提取
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
            
            // 注意：不再自动解析内容，改为按需解析
            // 用户打开书籍时才会触发解析
            logger.info("Book created, chapters will be parsed on-demand: $fileName")
            
            book
        } catch (e: Exception) {
            logger.error("Failed to create book: $fileName", e)
            null
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
