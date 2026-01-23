package com.bookd.domain.service.metadata

import com.bookd.config.EbookParserConfig
import com.bookd.domain.model.ContentElement
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * HTTP 客户端用于调用 eBook Parser 微服务
 */
class EbookParserClient(private val config: EbookParserConfig) {
    
    private val logger = LoggerFactory.getLogger(EbookParserClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true  // 忽略 Python 返回的 null 字段
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMs
            connectTimeoutMillis = 10000
            socketTimeoutMillis = config.timeoutMs
        }
    }
    
    /**
     * 提取 EPUB 元数据
     */
    suspend fun extractMetadata(filePath: String, bookId: Int): BookMetadata? {
        if (!config.enabled) {
            logger.debug("eBook Parser service is disabled")
            return null
        }
        
        return try {
            logger.debug("Calling eBook Parser service for metadata: $filePath")
            
            val response = client.post("${config.serviceUrl}/api/parse/metadata") {
                contentType(ContentType.Application.Json)
                setBody(ParseRequest(filePath, bookId))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val result: BookMetadataResponse = response.body()
                logger.info("Successfully extracted metadata via microservice for book $bookId")
                result.toBookMetadata()
            } else {
                logger.warn("eBook Parser service returned status ${response.status}")
                null
            }
            
        } catch (e: HttpRequestTimeoutException) {
            logger.error("eBook Parser service timeout for $filePath", e)
            null
        } catch (e: Exception) {
            logger.error("Failed to call eBook Parser service for $filePath", e)
            null
        }
    }
    
    /**
     * 提取 EPUB 封面
     */
    suspend fun extractCover(filePath: String, bookId: Int): String? {
        if (!config.enabled) {
            logger.debug("eBook Parser service is disabled")
            return null
        }
        
        return try {
            logger.debug("Calling eBook Parser service for cover: $filePath")
            
            val response = client.post("${config.serviceUrl}/api/parse/cover") {
                contentType(ContentType.Application.Json)
                setBody(ParseRequest(filePath, bookId))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val result: CoverExtractionResponse = response.body()
                if (result.success && result.cover_path != null) {
                    logger.info("Successfully extracted cover via microservice for book $bookId: ${result.cover_path}")
                    return result.cover_path
                } else {
                    logger.warn("eBook Parser service failed to extract cover: ${result.error}")
                    return null
                }
            } else {
                logger.warn("eBook Parser service returned status ${response.status}")
                null
            }
            
        } catch (e: HttpRequestTimeoutException) {
            logger.error("eBook Parser service timeout for $filePath", e)
            null
        } catch (e: Exception) {
            logger.error("Failed to call eBook Parser service for $filePath", e)
            null
        }
    }
    
    /**
     * 解析 EPUB 结构（章节列表和目录）
     */
    suspend fun parseStructure(filePath: String, bookId: Int): BookStructureResponse? {
        if (!config.enabled) {
            logger.debug("eBook Parser service is disabled")
            return null
        }
        
        return try {
            logger.debug("Calling eBook Parser service for structure: $filePath")
            
            val response = client.post("${config.serviceUrl}/api/parse/structure") {
                contentType(ContentType.Application.Json)
                setBody(ParseRequest(filePath, bookId))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val result: BookStructureResponse = response.body()
                logger.info("Successfully parsed structure via microservice for book $bookId: ${result.total_chapters} chapters")
                result
            } else {
                logger.warn("eBook Parser service returned status ${response.status}")
                null
            }
            
        } catch (e: HttpRequestTimeoutException) {
            logger.error("eBook Parser service timeout for $filePath", e)
            null
        } catch (e: Exception) {
            logger.error("Failed to call eBook Parser service for $filePath", e)
            null
        }
    }
    
    /**
     * 解析 EPUB 章节内容
     */
    suspend fun parseChapterContent(filePath: String, bookId: Int, chapterHref: String): ChapterContentResponse? {
        if (!config.enabled) {
            logger.debug("eBook Parser service is disabled")
            return null
        }
        
        return try {
            logger.debug("Calling eBook Parser service for chapter content: $chapterHref")
            
            val response = client.post("${config.serviceUrl}/api/parse/content") {
                contentType(ContentType.Application.Json)
                setBody(ParseContentRequest(filePath, bookId, chapterHref))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val result: ChapterContentResponse = response.body()
                logger.info("Successfully parsed chapter content via microservice for book $bookId, chapter $chapterHref")
                result
            } else {
                logger.warn("eBook Parser service returned status ${response.status}")
                null
            }
            
        } catch (e: HttpRequestTimeoutException) {
            logger.error("eBook Parser service timeout for chapter $chapterHref", e)
            null
        } catch (e: Exception) {
            logger.error("Failed to call eBook Parser service for chapter $chapterHref", e)
            null
        }
    }
    
    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean {
        if (!config.enabled) {
            return false
        }
        
        return try {
            val response = client.get("${config.serviceUrl}/health")
            val healthy = response.status == HttpStatusCode.OK
            if (healthy) {
                logger.debug("eBook Parser service is healthy")
            } else {
                logger.warn("eBook Parser service health check returned ${response.status}")
            }
            healthy
        } catch (e: Exception) {
            logger.warn("eBook Parser service health check failed: ${e.message}")
            false
        }
    }
    
    /**
     * 关闭客户端
     */
    fun close() {
        client.close()
    }
}

// DTO 类与 Python 服务的 API 模型对应

@Serializable
data class ParseRequest(
    val file_path: String,
    val book_id: Int
)

@Serializable
data class BookMetadataResponse(
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val tags: List<String> = emptyList()
) {
    fun toBookMetadata(): BookMetadata {
        return BookMetadata(
            title = title,
            author = author,
            publisher = publisher,
            description = description,
            isbn = isbn,
            tags = tags
        )
    }
}

@Serializable
data class CoverExtractionResponse(
    val cover_path: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspect_ratio: Double? = null,
    val success: Boolean = false,
    val error: String? = null
)

// ==================== 章节结构 DTO ====================

@Serializable
data class ChapterInfoDto(
    val index: Int,
    val href: String,
    val in_toc: Boolean,
    val title: String? = null,
    val level: Int = 0
)

@Serializable
data class BookStructureResponse(
    val chapters: List<ChapterInfoDto> = emptyList(),
    val total_chapters: Int,
    val success: Boolean = true,
    val error: String? = null
)

// ==================== 章节内容 DTO ====================

@Serializable
data class ParseContentRequest(
    val file_path: String,
    val book_id: Int,
    val chapter_href: String
)

@Serializable
data class ChapterContentResponse(
    val elements: List<ContentElement> = emptyList(),
    val word_count: Int = 0,
    val image_count: Int = 0,
    val success: Boolean = true,
    val error: String? = null
)

