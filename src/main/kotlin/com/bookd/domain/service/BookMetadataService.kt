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
    private val bookRepository: BookRepository,
    private val coverGeneratorService: CoverGeneratorService
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
                
                // If no metadata found, try to extract from filename
                if (author == null && title == null) {
                    logger.info("No embedded metadata, attempting to parse from filename: ${file.name}")
                    val filenameMetadata = parseMetadataFromFilename(file.nameWithoutExtension)
                    author = author ?: filenameMetadata.author
                    title = title ?: filenameMetadata.title
                    logger.info("Parsed from filename - title: $title, author: $author")
                }
                
                // Fallback: Parse specific formats directly if Tika failed
                if (author == null && title == null) {
                    val format = filePath.substringAfterLast('.', "").lowercase()
                    logger.info("Tika failed to extract metadata for $format, trying format-specific parser")
                    
                    when (format) {
                        "epub" -> {
                            val epubMetadata = parseEpubMetadataDirectly(file)
                            if (epubMetadata != null) {
                                author = author ?: epubMetadata.author
                                title = title ?: epubMetadata.title
                                publisher = publisher ?: epubMetadata.publisher
                                description = description ?: epubMetadata.description
                                logger.info("Successfully extracted from EPUB XML - author: $author, title: $title")
                            }
                        }
                        "mobi" -> {
                            // MOBI metadata extraction
                            val mobiMetadata = parseMobiMetadata(file)
                            if (mobiMetadata != null) {
                                author = author ?: mobiMetadata.author
                                title = title ?: mobiMetadata.title
                                publisher = publisher ?: mobiMetadata.publisher
                                description = description ?: mobiMetadata.description
                                logger.info("Successfully extracted from MOBI - author: $author, title: $title")
                            }
                        }
                        "azw3", "azw" -> {
                            // AZW3 is similar to MOBI
                            val azw3Metadata = parseMobiMetadata(file)
                            if (azw3Metadata != null) {
                                author = author ?: azw3Metadata.author
                                title = title ?: azw3Metadata.title
                                publisher = publisher ?: azw3Metadata.publisher
                                description = description ?: azw3Metadata.description
                                logger.info("Successfully extracted from AZW3 - author: $author, title: $title")
                            }
                        }
                        "pdf" -> {
                            // PDF metadata - Tika usually works well, but add fallback
                            val pdfMetadata = parsePdfMetadata(file)
                            if (pdfMetadata != null) {
                                author = author ?: pdfMetadata.author
                                title = title ?: pdfMetadata.title
                                publisher = publisher ?: pdfMetadata.publisher
                                description = description ?: pdfMetadata.description
                                logger.info("Successfully extracted from PDF - author: $author, title: $title")
                            }
                        }
                    }
                }
                
                // Extract cover image based on format
                var coverPath: String? = null
                val format = filePath.substringAfterLast('.', "").lowercase()
                when (format) {
                    "epub" -> coverPath = extractAndSaveEpubCover(file, bookId)
                    "mobi", "azw3", "azw" -> coverPath = extractAndSaveMobiCover(file, bookId)
                    "pdf" -> coverPath = extractAndSavePdfCover(file, bookId)
                }
                
                // If no cover extracted, generate a text-based cover
                if (coverPath == null) {
                    logger.info("No cover found for book ID: $bookId, generating text-based cover")
                    val bookTitle = title ?: file.nameWithoutExtension
                    coverPath = coverGeneratorService.generateCover(bookId, bookTitle, author)
                    if (coverPath != null) {
                        logger.info("Generated text-based cover for book ID: $bookId")
                    }
                }
                
                logger.info("Extracted metadata for ${file.name} - author: $author, title: $title, publisher: $publisher, isbn: $isbn, coverPath: $coverPath")
                
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
    
    /**
     * Parse MOBI/AZW3 metadata using Tika with specific configuration
     */
    private fun parseMobiMetadata(file: File): EpubMetadata? {
        try {
            val metadata = Metadata()
            val handler = BodyContentHandler(-1) // No limit for MOBI
            
            FileInputStream(file).use { stream ->
                parser.parse(stream, handler, metadata)
            }
            
            // MOBI specific metadata keys
            val author = metadata.get("Author") 
                ?: metadata.get("creator")
                ?: metadata.get("dc:creator")
            
            val title = metadata.get("title") 
                ?: metadata.get("dc:title")
            
            val publisher = metadata.get("Publisher")
                ?: metadata.get("publisher")
                ?: metadata.get("dc:publisher")
            
            val description = metadata.get("description")
                ?: metadata.get("dc:description")
                ?: metadata.get("subject")
            
            logger.info("MOBI/AZW3 parsed - title: $title, author: $author")
            
            if (title != null || author != null) {
                return EpubMetadata(title, author, publisher, description)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse MOBI/AZW3 metadata: ${e.message}")
        }
        return null
    }
    
    /**
     * Parse PDF metadata
     */
    private fun parsePdfMetadata(file: File): EpubMetadata? {
        try {
            val metadata = Metadata()
            val handler = BodyContentHandler(-1)
            
            FileInputStream(file).use { stream ->
                parser.parse(stream, handler, metadata)
            }
            
            // PDF specific metadata keys
            val author = metadata.get("Author")
                ?: metadata.get("pdf:docinfo:author")
                ?: metadata.get("creator")
                ?: metadata.get("dc:creator")
            
            val title = metadata.get("title")
                ?: metadata.get("pdf:docinfo:title")
                ?: metadata.get("dc:title")
            
            val publisher = metadata.get("publisher")
                ?: metadata.get("dc:publisher")
            
            val description = metadata.get("subject")
                ?: metadata.get("pdf:docinfo:subject")
                ?: metadata.get("dc:subject")
                ?: metadata.get("description")
            
            logger.info("PDF parsed - title: $title, author: $author")
            
            if (title != null || author != null) {
                return EpubMetadata(title, author, publisher, description)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse PDF metadata: ${e.message}")
        }
        return null
    }
    
    /**
     * Extract cover from MOBI/AZW3 (not commonly available, generate instead)
     */
    private fun extractAndSaveMobiCover(file: File, bookId: Int): String? {
        // MOBI/AZW3 cover extraction is complex and not well supported
        // Most MOBI files don't have easily extractable covers
        logger.debug("MOBI/AZW3 cover extraction not implemented, will use generated cover")
        return null
    }
    
    /**
     * Extract cover from PDF (first page as thumbnail)
     */
    private fun extractAndSavePdfCover(file: File, bookId: Int): String? {
        // PDF cover extraction would require PDFBox or similar library
        // For now, return null to use generated cover
        logger.debug("PDF cover extraction not implemented, will use generated cover")
        return null
    }
    
    /**
     * Parse metadata from filename
     * Common patterns:
     * - "书名 (作者).ext"
     * - "书名 - 作者.ext"
     * - "书名（作者）.ext"
     * - "作者 - 书名.ext"
     */
    private fun parseMetadataFromFilename(filename: String): FilenameMetadata {
        var title: String? = null
        var author: String? = null
        
        // Try pattern: "书名 (作者)" or "书名（作者）"
        val pattern1 = Regex("""^(.+?)\s*[（(](.+?)[）)].*$""")
        val match1 = pattern1.find(filename)
        if (match1 != null) {
            title = match1.groupValues[1].trim()
            author = match1.groupValues[2].trim()
            
            // Remove common suffixes from author
            author = author.replace(Regex("""\s*\(.*?\).*$"""), "")
                .replace(Regex("""\s*（.*?）.*$"""), "")
                .replace(Regex("""\s*(Z-Library|epub|mobi|pdf).*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            
            // Clean up title
            title = title.replace(Regex("""\s*[:：]\s*.*$"""), "").trim()
        }
        
        // If pattern failed or author looks suspicious (too long), try another pattern
        if (author != null && author.length > 50) {
            author = null
            title = filename.trim()
        }
        
        // Fallback: use whole filename as title
        if (title == null) {
            title = filename
                .replace(Regex("""\s*\(.*?\)"""), "")
                .replace(Regex("""\s*（.*?）"""), "")
                .trim()
        }
        
        return FilenameMetadata(title, author)
    }
}

/**
 * Data class to hold filename-parsed metadata
 */
data class FilenameMetadata(
    val title: String?,
    val author: String?
)

/**
 * Data class to hold EPUB metadata
 */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val publisher: String?,
    val description: String?
)
