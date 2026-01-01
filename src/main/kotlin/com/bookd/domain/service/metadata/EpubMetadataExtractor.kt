package com.bookd.domain.service.metadata

import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 元数据提取器
 */
class EpubMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(EpubMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        return try {
            ZipFile(file).use { zipFile ->
                // Find container.xml to locate content.opf
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
                val opfEntry = zipFile.getEntry(opfPath) ?: return null
                
                zipFile.getInputStream(opfEntry).use { stream ->
                    val factory = DocumentBuilderFactory.newInstance()
                    factory.isNamespaceAware = true
                    val doc = factory.newDocumentBuilder().parse(stream)
                    
                    BookMetadata(
                        title = extractElement(doc, "http://purl.org/dc/elements/1.1/", "title", "dc:title"),
                        author = extractElement(doc, "http://purl.org/dc/elements/1.1/", "creator", "dc:creator"),
                        publisher = extractElement(doc, "http://purl.org/dc/elements/1.1/", "publisher", "dc:publisher"),
                        description = extractElement(doc, "http://purl.org/dc/elements/1.1/", "description", "dc:description")
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract EPUB metadata from ${file.name}: ${e.message}")
            null
        }
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        return try {
            ZipFile(file).use { zipFile ->
                val coverPatterns = listOf(
                    "EPUB/cover.png", "EPUB/cover.jpg", "EPUB/cover.jpeg",
                    "cover.jpg", "cover.jpeg", "cover.png",
                    "OEBPS/Images/cover.jpg", "OEBPS/Images/cover.png",
                    "OPS/images/cover.jpg", "OPS/images/cover.png"
                )
                
                var coverEntry: ZipEntry? = null
                
                // Try predefined patterns
                for (pattern in coverPatterns) {
                    val entry = zipFile.getEntry(pattern)
                    if (entry != null) {
                        coverEntry = entry
                        break
                    }
                }
                
                // Search for any file with "cover" in name
                if (coverEntry == null) {
                    coverEntry = zipFile.entries().asSequence().find { entry ->
                        entry.name.lowercase().contains("cover") &&
                                (entry.name.endsWith(".jpg", true) ||
                                        entry.name.endsWith(".jpeg", true) ||
                                        entry.name.endsWith(".png", true))
                    }
                }
                
                if (coverEntry != null) {
                    val coversDir = File("covers")
                    if (!coversDir.exists()) {
                        coversDir.mkdirs()
                    }
                    
                    val extension = coverEntry.name.substringAfterLast(".").lowercase()
                    val outputFile = File(coversDir, "book_${bookId}.$extension")
                    
                    zipFile.getInputStream(coverEntry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    logger.info("Extracted EPUB cover for book $bookId")
                    return "/covers/book_${bookId}.$extension"
                }
                
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to extract EPUB cover from ${file.name}: ${e.message}")
            null
        }
    }
    
    private fun extractElement(doc: org.w3c.dom.Document, namespace: String, tagName: String, fallbackTag: String): String? {
        var value = doc.getElementsByTagNameNS(namespace, tagName)
            .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        
        if (value == null) {
            value = doc.getElementsByTagName(fallbackTag)
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        }
        
        return value
    }
}
