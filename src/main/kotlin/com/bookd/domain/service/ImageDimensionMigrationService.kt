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
        var success = 0
        var failed = 0

        val resources = transaction {
            DocumentResources.selectAll()
                .where { 
                    (DocumentResources.width.isNull()) or (DocumentResources.height.isNull()) 
                }
                .map { row ->
                    ResourceDimensionCandidate(
                        id = row[DocumentResources.id].value,
                        storedPath = row[DocumentResources.storedPath]
                    )
                }
        }

        logger.info("Found ${resources.size} resources without dimensions")

        val updates = mutableListOf<ResourceDimensionUpdate>()
        resources.forEach { resource ->
            try {
                val dimensions = bookImageStorage.extractImageDimensionsFromFile(resource.storedPath)

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

        transaction {
            updates.forEach { update ->
                DocumentResources.update({ DocumentResources.id eq update.id }) {
                    it[DocumentResources.width] = update.width
                    it[DocumentResources.height] = update.height
                }
            }
        }
        
        logger.info("Resource migration completed: $success succeeded, $failed failed, ${resources.size} total")
        return MigrationResult(resources.size, success, failed)
    }
    
    /**
     * 迁移 books 表中的封面尺寸
     */
    fun migrateCoverDimensions(): MigrationResult {
        var success = 0
        var failed = 0

        val books = transaction {
            Books.selectAll()
                .where { 
                    Books.coverPath.isNotNull() and 
                    ((Books.coverWidth.isNull()) or (Books.coverHeight.isNull()))
                }
                .mapNotNull { row ->
                    val coverPath = row[Books.coverPath] ?: return@mapNotNull null
                    CoverDimensionCandidate(
                        bookId = row[Books.id].value,
                        coverPath = coverPath
                    )
                }
        }

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

        transaction {
            updates.forEach { update ->
                Books.update({ Books.id eq update.bookId }) {
                    it[coverWidth] = update.width
                    it[coverHeight] = update.height
                }
            }
        }
        
        logger.info("Cover migration completed: $success succeeded, $failed failed, ${books.size} total")
        return MigrationResult(books.size, success, failed)
    }

    private fun extractCoverDimensions(coverPath: String): Pair<Int, Int>? {
        return when {
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

private data class ResourceDimensionCandidate(
    val id: Int,
    val storedPath: String
)

private data class ResourceDimensionUpdate(
    val id: Int,
    val width: Int,
    val height: Int
)

private data class CoverDimensionCandidate(
    val bookId: Int,
    val coverPath: String
)

private data class CoverDimensionUpdate(
    val bookId: Int,
    val width: Int,
    val height: Int
)

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
