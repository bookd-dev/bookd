package com.bookd.domain.service

import com.bookd.data.entity.DocumentResources
import com.bookd.data.entity.Books
import com.bookd.infrastructure.storage.BookImageStorage
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.slf4j.LoggerFactory
import kotlinx.serialization.Serializable
import java.io.File
import javax.imageio.ImageIO

/**
 * 图片尺寸迁移服务
 * 用于补全旧数据的宽高信息
 */
class ImageDimensionMigrationService(
    private val bookImageStorage: BookImageStorage
) {
    private val logger = LoggerFactory.getLogger(ImageDimensionMigrationService::class.java)
    
    /**
     * 迁移 document_resources 表中的图片尺寸
     */
    fun migrateResourceDimensions(): MigrationResult {
        var total = 0
        var success = 0
        var failed = 0
        
        transaction {
            val resources = DocumentResources.selectAll()
                .where { 
                    (DocumentResources.width.isNull()) or (DocumentResources.height.isNull()) 
                }
                .toList()
            
            total = resources.size
            logger.info("Found $total resources without dimensions")
            
            resources.forEach { row ->
                val id = row[DocumentResources.id].value
                val storedPath = row[DocumentResources.storedPath]
                
                try {
                    // 从文件系统读取图片尺寸
                    val dimensions = bookImageStorage.extractImageDimensionsFromFile(storedPath)
                    
                    if (dimensions != null) {
                        val (width, height) = dimensions
                        
                        DocumentResources.update({ DocumentResources.id eq id }) {
                            it[DocumentResources.width] = width
                            it[DocumentResources.height] = height
                        }
                        
                        success++
                        logger.debug("Updated dimensions for resource $id: ${width}x$height")
                    } else {
                        failed++
                        logger.warn("Failed to extract dimensions for resource $id (path: $storedPath)")
                    }
                } catch (e: Exception) {
                    failed++
                    logger.error("Error processing resource $id", e)
                }
            }
        }
        
        logger.info("Resource migration completed: $success succeeded, $failed failed, $total total")
        return MigrationResult(total, success, failed)
    }
    
    /**
     * 迁移 books 表中的封面尺寸
     */
    fun migrateCoverDimensions(): MigrationResult {
        var total = 0
        var success = 0
        var failed = 0
        
        transaction {
            val books = Books.selectAll()
                .where { 
                    Books.coverPath.isNotNull() and 
                    ((Books.coverWidth.isNull()) or (Books.coverHeight.isNull()))
                }
                .toList()
            
            total = books.size
            logger.info("Found $total books without cover dimensions")
            
            books.forEach { row ->
                val bookId = row[Books.id].value
                val coverPath = row[Books.coverPath] ?: return@forEach
                
                try {
                    // 从完整路径提取相对路径
                    // coverPath 格式: 
                    //   - 新系统: /book_images/covers/xxx.jpg
                    //   - 旧系统: /covers/xxx.jpg
                    val dimensions = when {
                        coverPath.startsWith("/book_images/") -> {
                            // 新系统：使用 BookImageStorage 的 baseDir (book_images)
                            val relativePath = coverPath.removePrefix("/book_images/")
                            bookImageStorage.extractImageDimensionsFromFile(relativePath)
                        }
                        coverPath.startsWith("/covers/") -> {
                            // 旧系统：封面在独立的 covers 目录
                            val fileName = coverPath.removePrefix("/covers/")
                            val coverFile = File("covers", fileName)
                            if (coverFile.exists() && coverFile.isFile) {
                                try {
                                    val image = ImageIO.read(coverFile)
                                    if (image != null) Pair(image.width, image.height) else null
                                } catch (e: Exception) {
                                    logger.debug("Failed to read old cover: $coverPath", e)
                                    null
                                }
                            } else {
                                logger.debug("Old cover file not found: {}", coverFile)
                                null
                            }
                        }
                        else -> {
                            logger.warn("Unexpected cover path format: $coverPath")
                            null
                        }
                    }
                    
                    if (dimensions != null) {
                        val (width, height) = dimensions
                        
                        Books.update({ Books.id eq bookId }) {
                            it[coverWidth] = width
                            it[coverHeight] = height
                        }
                        
                        success++
                        logger.debug("Updated cover dimensions for book $bookId: ${width}x$height")
                    } else {
                        failed++
                        logger.warn("Failed to extract cover dimensions for book $bookId (path: $coverPath)")
                    }
                } catch (e: Exception) {
                    failed++
                    logger.error("Error processing book $bookId", e)
                }
            }
        }
        
        logger.info("Cover migration completed: $success succeeded, $failed failed, $total total")
        return MigrationResult(total, success, failed)
    }
    
    /**
     * 重试失败的图片尺寸提取（仅处理之前失败的记录）
     * 与初次迁移相同，但这是一个明确的"重试"操作
     */
    fun retryFailedMigrations(): MigrationResponse {
        logger.info("Starting retry of failed image dimension migrations")
        
        // 重试资源图片
        val resourcesResult = migrateResourceDimensions()
        
        // 重试封面
        val coversResult = migrateCoverDimensions()
        
        val response = MigrationResponse(
            resourcesMigrated = resourcesResult.successCount,
            resourcesFailed = resourcesResult.failedCount,
            coversMigrated = coversResult.successCount,
            coversFailed = coversResult.failedCount,
            totalSuccess = resourcesResult.successCount + coversResult.successCount,
            totalFailed = resourcesResult.failedCount + coversResult.failedCount
        )
        
        logger.info("Retry completed: ${response.totalSuccess} succeeded, ${response.totalFailed} failed")
        return response
    }
}

@Serializable
data class MigrationResult(
    val total: Int,
    val successCount: Int,
    val failedCount: Int
)

@Serializable
data class MigrationResponse(
    val resourcesMigrated: Int,
    val resourcesFailed: Int,
    val coversMigrated: Int,
    val coversFailed: Int,
    val totalSuccess: Int,
    val totalFailed: Int
)
