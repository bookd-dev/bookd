package com.bookd.infrastructure.storage

import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

/**
 * 书籍图片存储服务
 * 负责将书籍封面和内部图片持久化到文件系统
 */
class BookImageStorage(
    private val baseDir: String = "book_images"
) {
    private val logger = LoggerFactory.getLogger(BookImageStorage::class.java)
    
    // 封面子目录
    private val coversSubDir = "covers"
    
    init {
        // 确保基础目录和封面目录存在
        File(baseDir).mkdirs()
        File(baseDir, coversSubDir).mkdirs()
        logger.info("Book image storage initialized at: $baseDir")
    }
    
    /**
     * 保存书籍内部图片并返回存储路径
     * @param bookId 书籍ID
     * @param imageName 原始图片名称（用于提取扩展名）
     * @param imageData 图片二进制数据
     * @return 相对于baseDir的存储路径，格式: {bookId}/{hash}.{ext}
     */
    fun saveImage(bookId: Int, imageName: String, imageData: ByteArray): String {
        try {
            // 创建书籍专属目录
            val bookDir = File(baseDir, bookId.toString())
            bookDir.mkdirs()
            
            // 提取扩展名
            val extension = imageName.substringAfterLast('.', "jpg").lowercase()
            
            // 使用内容哈希作为文件名（避免重复，节省空间）
            val hash = calculateHash(imageData)
            val fileName = "$hash.$extension"
            val imageFile = File(bookDir, fileName)
            
            // 如果文件已存在（相同内容），直接返回路径
            if (imageFile.exists()) {
                logger.debug("Image already exists: $bookId/$fileName")
                return "$bookId/$fileName"
            }
            
            // 保存图片
            imageFile.writeBytes(imageData)
            logger.debug("Saved image: $bookId/$fileName (${imageData.size} bytes)")
            
            return "$bookId/$fileName"
        } catch (e: Exception) {
            logger.error("Failed to save image: bookId=$bookId, name=$imageName", e)
            throw e
        }
    }
    
    /**
     * 保存书籍封面
     * @param bookId 书籍ID
     * @param coverName 封面文件名（用于提取扩展名）
     * @param coverData 封面图片数据
     * @param isGenerated 是否为生成的封面（生成的封面使用固定名称）
     * @return HTTP 访问路径，格式: /book_images/covers/{filename}
     */
    fun saveCover(bookId: Int, coverName: String, coverData: ByteArray, isGenerated: Boolean = false): String {
        try {
            val coversDir = File(baseDir, coversSubDir)
            coversDir.mkdirs()
            
            val extension = coverName.substringAfterLast('.', "png").lowercase()
            
            val fileName = if (isGenerated) {
                // 生成的封面使用固定名称，便于覆盖
                "book_${bookId}_generated.$extension"
            } else {
                // 上传的封面使用哈希
                val hash = calculateHash(coverData)
                "${hash}.$extension"
            }
            
            val coverFile = File(coversDir, fileName)
            
            // 如果已存在且内容相同，直接返回
            if (coverFile.exists() && !isGenerated) {
                logger.debug("Cover already exists: $coversSubDir/$fileName")
                return "/book_images/$coversSubDir/$fileName"
            }
            
            // 保存封面
            coverFile.writeBytes(coverData)
            logger.info("Saved cover: $coversSubDir/$fileName (${coverData.size} bytes)")
            
            return "/book_images/$coversSubDir/$fileName"
        } catch (e: Exception) {
            logger.error("Failed to save cover: bookId=$bookId, name=$coverName", e)
            throw e
        }
    }
    
    /**
     * 获取图片文件
     * @param relativePath 相对路径，格式: {bookId}/{filename} 或 covers/{filename}
     * @return File对象，如果不存在返回null
     */
    fun getImage(relativePath: String): File? {
        val file = File(baseDir, relativePath)
        return if (file.exists() && file.isFile) file else null
    }
    
    /**
     * 删除书籍的所有图片（不包括封面）
     * @param bookId 书籍ID
     */
    fun deleteBookImages(bookId: Int) {
        try {
            val bookDir = File(baseDir, bookId.toString())
            if (bookDir.exists() && bookDir.isDirectory) {
                val deleted = bookDir.deleteRecursively()
                if (deleted) {
                    logger.info("Deleted all images for book: $bookId")
                } else {
                    logger.warn("Failed to delete images for book: $bookId")
                }
            }
        } catch (e: Exception) {
            logger.error("Error deleting images for book: $bookId", e)
        }
    }
    
    /**
     * 删除书籍封面
     * @param coverPath 封面路径，如 /book_images/covers/xxx.jpg
     */
    fun deleteCover(coverPath: String?) {
        if (coverPath.isNullOrBlank()) return
        
        try {
            // 从路径中提取文件名
            val fileName = coverPath.substringAfterLast('/')
            val coverFile = File(baseDir, "$coversSubDir/$fileName")
            
            if (coverFile.exists() && coverFile.delete()) {
                logger.info("Deleted cover: $coverPath")
            }
        } catch (e: Exception) {
            logger.error("Error deleting cover: $coverPath", e)
        }
    }
    
    /**
     * 计算数据的SHA-256哈希值（取前16位）
     */
    private fun calculateHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
    
    /**
     * 检测媒体类型
     */
    fun detectMediaType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
    }
}
