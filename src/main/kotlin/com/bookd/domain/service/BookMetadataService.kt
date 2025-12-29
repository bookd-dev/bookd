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
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

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
                    logger.warn("No metadata keys found in file: ${file.name}, Tika failed to parse")
                }
                
                // Extract metadata fields - try multiple variations
                var author = metadata.get("author") 
                    ?: metadata.get("creator") 
                    ?: metadata.get("dc:creator")
                    ?: metadata.get("meta:author")
                    ?: metadata.get("Author")
                
                var title = metadata.get("title") 
                    ?: metadata.get("dc:title")
                    ?: metadata.get("Title")
                
                var publisher = metadata.get("publisher") 
                    ?: metadata.get("dc:publisher")
                    ?: metadata.get("Publisher")
                
                var isbn = metadata.get("isbn")
                    ?: metadata.get("ISBN")
                
                var description = metadata.get("description") 
                    ?: metadata.get("dc:description")
                    ?: metadata.get("subject")
                    ?: metadata.get("dc:subject")
                    ?: metadata.get("Description")
                
                // Fallback: Parse EPUB XML directly if Tika failed
                if (filePath.endsWith(".epub", ignoreCase = true) && author == null && title == null) {
                    logger.info("Tika failed to extract EPUB metadata, parsing XML directly")
                    val epubMetadata = parseEpubMetadataDirectly(file)
                    if (epubMetadata != null) {
                        author = author ?: epubMetadata.author
                        title = title ?: epubMetadata.title
                        publisher = publisher ?: epubMetadata.publisher
                        description = description ?: epubMetadata.description
                        logger.info("Successfully extracted from EPUB XML - author: $author, title: $title")
                    }
                }
                
                // Extract cover image if available (for EPUB)
                var coverPath: String? = null
                if (filePath.endsWith(".epub", ignoreCase = true)) {
                    coverPath = extractAndSaveEpubCover(file, bookId)
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
     * Parse EPUB metadata directly from content.opf file
     */
    private fun parseEpubMetadataDirectly(file: File): EpubMetadata? {
        try {
            ZipFile(file).use { zipFile ->
                // Find content.opf location from container.xml
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                var opfPath = "EPUB/content.opf" // Default path
                
                if (containerEntry != null) {
                    zipFile.getInputStream(containerEntry).use { stream ->
                        val factory = DocumentBuilderFactory.newInstance()
                        factory.isNamespaceAware = true
                        val doc = factory.newDocumentBuilder().parse(stream)
                        val rootfiles = doc.getElementsByTagName("rootfile")
                        if (rootfiles.length > 0) {
                            val fullPath = rootfiles.item(0).attributes.getNamedItem("full-path")?.nodeValue
                            if (fullPath != null) {
                                opfPath = fullPath
                            }
                        }
                    }
                }
                
                // Parse content.opf
                val opfEntry = zipFile.getEntry(opfPath)
                if (opfEntry != null) {
                    zipFile.getInputStream(opfEntry).use { stream ->
                        val factory = DocumentBuilderFactory.newInstance()
                        factory.isNamespaceAware = true
                        val doc = factory.newDocumentBuilder().parse(stream)
                        
                        // Extract metadata - try both with and without namespace
                        var title = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title")
                            .item(0)?.textContent?.trim()
                        if (title == null) {
                            title = doc.getElementsByTagName("dc:title")
                                .item(0)?.textContent?.trim()
                        }
                        
                        var creator = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator")
                            .item(0)?.textContent?.trim()
                        if (creator == null) {
                            creator = doc.getElementsByTagName("dc:creator")
                                .item(0)?.textContent?.trim()
                        }
                        
                        var publisher = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "publisher")
                            .item(0)?.textContent?.trim()
                        if (publisher == null) {
                            publisher = doc.getElementsByTagName("dc:publisher")
                                .item(0)?.textContent?.trim()
                        }
                        
                        var description = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "description")
                            .item(0)?.textContent?.trim()
                        if (description == null) {
                            description = doc.getElementsByTagName("dc:description")
                                .item(0)?.textContent?.trim()
                        }
                        
                        logger.info("Parsed EPUB XML - title: $title, creator: $creator")
                        
                        if (title != null || creator != null) {
                            return EpubMetadata(title, creator, publisher, description)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse EPUB metadata directly: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Extract and save cover image from EPUB file
     */
    private fun extractAndSaveEpubCover(file: File, bookId: Int): String? {
        try {
            ZipFile(file).use { zipFile ->
                // Common cover image paths in EPUB files
                val coverPatterns = listOf(
                    "EPUB/cover.png", "EPUB/cover.jpg", "EPUB/cover.jpeg",
                    "cover.jpg", "cover.jpeg", "cover.png",
                    "Cover.jpg", "Cover.jpeg", "Cover.png",
                    "OEBPS/Images/cover.jpg", "OEBPS/Images/cover.jpeg", "OEBPS/Images/cover.png",
                    "OEBPS/images/cover.jpg", "OEBPS/images/cover.jpeg", "OEBPS/images/cover.png",
                    "OPS/images/cover.jpg", "OPS/images/cover.jpeg", "OPS/images/cover.png"
                )
                
                var coverEntry: java.util.zip.ZipEntry? = null
                
                // Try to find cover image using patterns
                for (pattern in coverPatterns) {
                    val entry = zipFile.getEntry(pattern)
                    if (entry != null) {
                        coverEntry = entry
                        logger.info("Found cover image in EPUB: $pattern")
                        break
                    }
                }
                
                // If not found, try to find any image with "cover" in the name
                if (coverEntry == null) {
                    coverEntry = zipFile.entries().asSequence().find { entry ->
                        entry.name.lowercase().contains("cover") && 
                        (entry.name.endsWith(".jpg", true) || 
                         entry.name.endsWith(".jpeg", true) || 
                         entry.name.endsWith(".png", true))
                    }
                    
                    if (coverEntry != null) {
                        logger.info("Found cover image in EPUB: ${coverEntry.name}")
                    }
                }
                
                if (coverEntry != null) {
                    // Create covers directory
                    val coversDir = File("covers")
                    if (!coversDir.exists()) {
                        coversDir.mkdirs()
                        logger.info("Created covers directory: ${coversDir.absolutePath}")
                    }
                    
                    // Get file extension
                    val extension = coverEntry.name.substringAfterLast(".").lowercase()
                    val outputFile = File(coversDir, "book_${bookId}.$extension")
                    
                    // Extract and save cover
                    zipFile.getInputStream(coverEntry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    logger.info("Saved cover to: ${outputFile.absolutePath}")
                    return "/covers/book_${bookId}.$extension"
                }
                
                logger.debug("No cover image found in EPUB: ${file.name}")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract cover from EPUB: ${file.name} - ${e.message}")
            e.printStackTrace()
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

/**
 * Data class to hold EPUB metadata
 */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val publisher: String?,
    val description: String?
)
