package com.bookd.domain.service

import com.bookd.data.repository.CoverDimensionUpdate
import com.bookd.data.repository.ImageDimensionMigrationRepository
import com.bookd.data.repository.ResourceDimensionUpdate
import com.bookd.infrastructure.storage.BookImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO

/**
 * 图片尺寸迁移服务
 * 用于补全旧数据的宽高信息
 */
class ImageDimensionMigrationService(
    private val bookImageStorage: BookImageStorage,
    private val migrationRepository: ImageDimensionMigrationRepository
) {
    private val logger = LoggerFactory.getLogger(ImageDimensionMigrationService::class.java)
    
    /**
     * 迁移 document_resources 表中的图片尺寸
     */
    suspend fun migrateResourceDimensions(): MigrationResult {
        var success = 0
        var failed = 0

        val resources = migrationRepository.findResourcesMissingDimensions()

        logger.info("Found ${resources.size} resources without dimensions")

        val updates = mutableListOf<ResourceDimensionUpdate>()
        resources.forEach { resource ->
            try {
                val dimensions = extractStoredImageDimensions(resource.storedPath)

                if (dimensions != null) {
                    val (width, height) = dimensions
                    updates.add(ResourceDimensionUpdate(resource.id, width, height))
                    success++
                    logger.debug("Extracted dimensions for resource ${resource.id}: ${width}x$height")
                } else {
                    failed++
                    logger.warn("Failed to extract dimensions for resource ${resource.id} (path: ${resource.storedPath})")
                }
            } catch (e: Exception) {
                failed++
                logger.error("Error processing resource ${resource.id}", e)
            }
        }

        migrationRepository.updateResourceDimensions(updates)
        
        logger.info("Resource migration completed: $success succeeded, $failed failed, ${resources.size} total")
        return MigrationResult(resources.size, success, failed)
    }
    
    /**
     * 迁移 books 表中的封面尺寸
     */
    suspend fun migrateCoverDimensions(): MigrationResult {
        var success = 0
        var failed = 0

        val books = migrationRepository.findCoversMissingDimensions()

        logger.info("Found ${books.size} books without cover dimensions")

        val updates = mutableListOf<CoverDimensionUpdate>()
        books.forEach { book ->
            try {
                val dimensions = extractCoverDimensions(book.coverPath)

                if (dimensions != null) {
                    val (width, height) = dimensions
                    updates.add(CoverDimensionUpdate(book.bookId, width, height))
                    success++
                    logger.debug("Extracted cover dimensions for book ${book.bookId}: ${width}x$height")
                } else {
                    failed++
                    logger.warn("Failed to extract cover dimensions for book ${book.bookId} (path: ${book.coverPath})")
                }
            } catch (e: Exception) {
                failed++
                logger.error("Error processing book ${book.bookId}", e)
            }
        }

        migrationRepository.updateCoverDimensions(updates)
        
        logger.info("Cover migration completed: $success succeeded, $failed failed, ${books.size} total")
        return MigrationResult(books.size, success, failed)
    }

    private suspend fun extractStoredImageDimensions(storedPath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        bookImageStorage.extractImageDimensionsFromFile(storedPath)
    }

    private suspend fun extractCoverDimensions(coverPath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        when {
            coverPath.startsWith("/book_images/") -> {
                val relativePath = coverPath.removePrefix("/book_images/")
                bookImageStorage.extractImageDimensionsFromFile(relativePath)
            }
            coverPath.startsWith("/covers/") -> {
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
    }
    
    /**
     * 重试失败的图片尺寸提取（仅处理之前失败的记录）
     * 与初次迁移相同，但这是一个明确的"重试"操作
     */
    suspend fun retryFailedMigrations(): MigrationResponse {
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
