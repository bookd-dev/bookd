package com.bookd.domain.service

import com.bookd.config.EbookParserConfig
import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.TagRepository
import com.bookd.domain.service.metadata.BookMetadata
import com.bookd.domain.service.metadata.EbookParserClient
import com.bookd.domain.service.metadata.MetadataExtractorFactory
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors

class BookMetadataService(
    private val bookRepository: BookRepository,
    private val coverGeneratorService: CoverGeneratorService,
    private val tagRepository: TagRepository,
    private val parserClient: EbookParserClient?,
    private val config: EbookParserConfig
) {
    private val logger = LoggerFactory.getLogger(BookMetadataService::class.java)
    private val extractorFactory = MetadataExtractorFactory(parserClient, config)
    
    // Create a dedicated dispatcher for metadata extraction with a thread pool
    private val metadataDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = CoroutineScope(metadataDispatcher + SupervisorJob())
    
    /**
     * Asynchronously extract metadata from book file
     */
    fun extractMetadataAsync(bookId: Int, filePath: String) {
        scope.launch {
            delay(500) // Small delay to avoid interfering with scan response
            try {
                logger.info("Starting metadata extraction for book ID: $bookId, file: $filePath")
                extractAndUpdateMetadata(bookId, filePath)
                logger.info("Completed metadata extraction for book ID: $bookId")
            } catch (e: Exception) {
                logger.error("Failed to extract metadata for book ID: $bookId", e)
            }
        }
    }
    
    /**
     * Extract metadata and update book record
     */
    private suspend fun extractAndUpdateMetadata(bookId: Int, filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist or is not a file: $filePath")
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                logger.info("Extracting metadata from: $filePath")
                
                // 使用工厂创建对应格式的提取器
                val extractor = extractorFactory.createExtractor(file)
                
                // 提取元数据
                var metadata = extractor.extractMetadata(file)
                
                // 如果标题为空,使用文件名作为标题
                if (metadata?.title == null) {
                    val fileNameWithoutExt = file.nameWithoutExtension
                    logger.info("No title found in metadata, using filename: $fileNameWithoutExt")
                    metadata = metadata?.copy(title = fileNameWithoutExt) 
                        ?: BookMetadata(title = fileNameWithoutExt)
                }
                
                // 提取封面
                var coverPath = extractor.extractCover(file, bookId)
                
                // 如果没有提取到封面,生成文字封面
                if (coverPath == null) {
                    logger.info("No cover found for book ID: $bookId, generating text-based cover")
                    val bookTitle = metadata.title ?: "Unknown"
                    val result = coverGeneratorService.generateCover(bookId, bookTitle, metadata.author)
                    coverPath = result?.first
                    if (coverPath != null) {
                        logger.info("Generated text-based cover for book ID: $bookId")
                    }
                }
                
                logger.info("Extracted metadata for ${file.name} - author: ${metadata?.author}, title: ${metadata?.title}, publisher: ${metadata?.publisher}, coverPath: $coverPath")

                val updated = bookRepository.updateMetadata(
                    id = bookId,
                    title = metadata.title,  // 更新标题
                    author = metadata.author,
                    coverPath = coverPath,
                    isbn = metadata.isbn,
                    publisher = metadata.publisher,
                    description = metadata.description?.take(1000) // Limit description length
                )

                if (updated > 0) {
                    logger.info("Successfully updated metadata for book ID: $bookId (${file.name})")
                    
                    // 从元数据中提取并添加标签
                    if (metadata.tags.isNotEmpty()) {
                        processMetadataTags(bookId, metadata.tags)
                    }
                } else {
                    logger.warn("Failed to update database for book ID: $bookId (${file.name})")
                }
            } catch (e: Exception) {
                logger.error("Error extracting metadata from file: ${file.name} - ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理从元数据中提取的标签
     */
    private fun processMetadataTags(bookId: Int, tags: List<String>) {
        try {
            logger.info("Processing ${tags.size} tags for book ID: $bookId - ${tags.joinToString(", ")}")
            
            var addedCount = 0
            for (tagName in tags) {
                try {
                    // 查找或创建标签
                    val tag = tagRepository.findOrCreateTag(tagName)
                    
                    // 添加标签到书籍 (如果已存在会被忽略)
                    val added = tagRepository.addTagToBook(bookId, tag.id)
                    if (added) {
                        addedCount++
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to add tag '$tagName' to book $bookId: ${e.message}")
                }
            }
            
            if (addedCount > 0) {
                logger.info("Successfully added $addedCount tags to book ID: $bookId")
            }
        } catch (e: Exception) {
            logger.error("Error processing metadata tags for book ID: $bookId", e)
        }
    }
    
    /**
     * Shutdown the service gracefully
     */
    fun shutdown() {
        scope.cancel()
        metadataDispatcher.close()
    }
}
