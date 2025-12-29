package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import kotlinx.coroutines.*
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

class BookMetadataService(
    private val bookRepository: BookRepository
) {
    private val logger = LoggerFactory.getLogger(BookMetadataService::class.java)
    private val parser = AutoDetectParser()
    
    // Create a dedicated dispatcher for metadata extraction with a thread pool
    private val metadataDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = CoroutineScope(metadataDispatcher + SupervisorJob())
    
    /**
     * Asynchronously extract metadata from book file
     */
    fun extractMetadataAsync(bookId: Int, filePath: String) {
        scope.launch {
            delay(1000) // Small delay to avoid interfering with scan response
            try {
                logger.info("Starting metadata extraction for book ID: $bookId")
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
                val metadata = Metadata()
                val handler = BodyContentHandler(10 * 1024 * 1024) // 10MB limit
                
                FileInputStream(file).use { stream ->
                    parser.parse(stream, handler, metadata)
                }
                
                // Log available metadata keys for debugging
                val keys = metadata.names()
                if (keys.isNotEmpty()) {
                    logger.debug("Available metadata keys for $filePath: ${keys.joinToString(", ")}")
                }
                
                // Extract metadata fields
                val author = metadata.get("author") 
                    ?: metadata.get("creator") 
                    ?: metadata.get("dc:creator")
                
                val title = metadata.get("title") 
                    ?: metadata.get("dc:title")
                
                val publisher = metadata.get("publisher") 
                    ?: metadata.get("dc:publisher")
                
                val isbn = metadata.get("isbn")
                
                val description = metadata.get("description") 
                    ?: metadata.get("dc:description")
                    ?: metadata.get("subject")
                
                // Extract cover image if available (for EPUB)
                var coverPath: String? = null
                if (filePath.endsWith(".epub", ignoreCase = true)) {
                    coverPath = extractEpubCover(file)
                }
                
                logger.info("Extracted metadata - author: $author, publisher: $publisher, isbn: $isbn")
                
                // Update database - only update if we have at least one metadata field
                if (author != null || publisher != null || isbn != null || description != null || coverPath != null) {
                    val updated = bookRepository.updateMetadata(
                        id = bookId,
                        author = author,
                        coverPath = coverPath,
                        isbn = isbn,
                        publisher = publisher,
                        description = description?.take(1000) // Limit description length
                    )
                    
                    if (updated > 0) {
                        logger.info("Successfully updated metadata for book ID: $bookId")
                    } else {
                        logger.warn("Failed to update metadata for book ID: $bookId")
                    }
                } else {
                    logger.info("No metadata found in file: $filePath")
                }
            } catch (e: Exception) {
                logger.error("Error extracting metadata from file: $filePath - ${e.message}")
            }
        }
    }
    
    /**
     * Extract cover image from EPUB file
     * This is a simplified version - can be enhanced to actually extract and save the cover
     */
    private fun extractEpubCover(file: File): String? {
        // TODO: Implement EPUB cover extraction
        // This would involve:
        // 1. Opening the EPUB as a ZIP file
        // 2. Finding the cover image from META-INF/container.xml or content.opf
        // 3. Extracting the image to a covers directory
        // 4. Returning the path to the saved cover image
        return null
    }
    
    /**
     * Shutdown the service gracefully
     */
    fun shutdown() {
        scope.cancel()
        metadataDispatcher.close()
    }
}
