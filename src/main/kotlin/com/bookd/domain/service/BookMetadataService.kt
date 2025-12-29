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
                val metadata = Metadata()
                val handler = BodyContentHandler(10 * 1024 * 1024) // 10MB limit
                
                FileInputStream(file).use { stream ->
                    parser.parse(stream, handler, metadata)
                }
                
                // Log all available metadata keys for debugging
                val keys = metadata.names()
                if (keys.isNotEmpty()) {
                    logger.info("Available metadata keys for ${file.name}: ${keys.joinToString(", ")}")
                    // Log all metadata values for debugging
                    keys.forEach { key ->
                        val value = metadata.get(key)
                        if (value != null && value.isNotBlank()) {
                            logger.debug("  $key = $value")
                        }
                    }
                } else {
                    logger.warn("No metadata keys found in file: $filePath")
                }
                
                // Extract metadata fields - try multiple variations
                val author = metadata.get("author") 
                    ?: metadata.get("creator") 
                    ?: metadata.get("dc:creator")
                    ?: metadata.get("meta:author")
                    ?: metadata.get("Author")
                
                val title = metadata.get("title") 
                    ?: metadata.get("dc:title")
                    ?: metadata.get("Title")
                
                val publisher = metadata.get("publisher") 
                    ?: metadata.get("dc:publisher")
                    ?: metadata.get("Publisher")
                
                val isbn = metadata.get("isbn")
                    ?: metadata.get("ISBN")
                
                val description = metadata.get("description") 
                    ?: metadata.get("dc:description")
                    ?: metadata.get("subject")
                    ?: metadata.get("dc:subject")
                    ?: metadata.get("Description")
                
                // Extract cover image if available (for EPUB)
                var coverPath: String? = null
                if (filePath.endsWith(".epub", ignoreCase = true)) {
                    coverPath = extractEpubCover(file)
                }
                
                logger.info("Extracted metadata for ${file.name} - author: $author, title: $title, publisher: $publisher, isbn: $isbn")
                
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
                        logger.info("Successfully updated metadata for book ID: $bookId (${file.name})")
                    } else {
                        logger.warn("Failed to update database for book ID: $bookId (${file.name})")
                    }
                } else {
                    logger.warn("No metadata extracted from file: ${file.name}")
                }
            } catch (e: Exception) {
                logger.error("Error extracting metadata from file: ${file.name} - ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Extract cover image from EPUB file
     */
    private fun extractEpubCover(file: File): String? {
        try {
            // EPUB is a ZIP file
            java.util.zip.ZipFile(file).use { zipFile ->
                // Common cover image paths in EPUB files
                val coverPatterns = listOf(
                    "cover.jpg", "cover.jpeg", "cover.png",
                    "Cover.jpg", "Cover.jpeg", "Cover.png",
                    "OEBPS/Images/cover.jpg", "OEBPS/Images/cover.jpeg", "OEBPS/Images/cover.png",
                    "OEBPS/images/cover.jpg", "OEBPS/images/cover.jpeg", "OEBPS/images/cover.png",
                    "OPS/images/cover.jpg", "OPS/images/cover.jpeg", "OPS/images/cover.png"
                )
                
                // Try to find cover image
                for (pattern in coverPatterns) {
                    val entry = zipFile.getEntry(pattern)
                    if (entry != null) {
                        logger.info("Found cover image in EPUB: $pattern")
                        // TODO: Extract and save the cover image
                        // For now, just return the path within the EPUB
                        return pattern
                    }
                }
                
                // Try to find any image with "cover" in the name
                val coverEntry = zipFile.entries().asSequence().find { entry ->
                    entry.name.lowercase().contains("cover") && 
                    (entry.name.endsWith(".jpg", true) || 
                     entry.name.endsWith(".jpeg", true) || 
                     entry.name.endsWith(".png", true))
                }
                
                if (coverEntry != null) {
                    logger.info("Found cover image in EPUB: ${coverEntry.name}")
                    return coverEntry.name
                }
                
                logger.debug("No cover image found in EPUB: ${file.name}")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract cover from EPUB: ${file.name} - ${e.message}")
        }
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
