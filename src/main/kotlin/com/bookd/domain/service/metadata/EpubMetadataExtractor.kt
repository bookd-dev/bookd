package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.epub.EpubArchiveSafety
import com.bookd.domain.service.parser.epub.SecureXml
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * EPUB 元数据提取器
 */
class EpubMetadataExtractor internal constructor(
    private val coverStorage: LegacyCoverStorage
) : MetadataExtractor {

    constructor() : this(LegacyCoverStorage())
    
    private val logger = LoggerFactory.getLogger(EpubMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        return try {
            ZipFile(file).use { zipFile ->
                EpubArchiveSafety.validate(zipFile)
                // Find container.xml to locate content.opf
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                var opfPath = "EPUB/content.opf" // Default path
                
                if (containerEntry != null) {
                    EpubArchiveSafety.readBytes(zipFile, containerEntry, EpubArchiveSafety.MAX_XML_BYTES).inputStream().use { stream ->
                        val doc = SecureXml.parse(stream)
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
                
                EpubArchiveSafety.readBytes(zipFile, opfEntry, EpubArchiveSafety.MAX_XML_BYTES).inputStream().use { stream ->
                    val doc = SecureXml.parse(stream)
                    
                    BookMetadata(
                        title = extractElement(doc, "http://purl.org/dc/elements/1.1/", "title", "dc:title"),
                        author = extractElement(doc, "http://purl.org/dc/elements/1.1/", "creator", "dc:creator"),
                        publisher = extractElement(doc, "http://purl.org/dc/elements/1.1/", "publisher", "dc:publisher"),
                        description = extractElement(doc, "http://purl.org/dc/elements/1.1/", "description", "dc:description"),
                        tags = extractTags(doc)
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
                EpubArchiveSafety.validate(zipFile)
                // 1. 先尝试从 OPF 的 manifest 中查找封面定义 (标准方式)
                val coverFromOpf = findCoverFromOpf(zipFile)
                if (coverFromOpf != null) {
                    return saveCover(zipFile, coverFromOpf, bookId)
                }
                
                // 2. 尝试预定义的路径模式
                val coverPatterns = listOf(
                    "EPUB/cover.png", "EPUB/cover.jpg", "EPUB/cover.jpeg",
                    "cover.jpg", "cover.jpeg", "cover.png",
                    "OEBPS/Images/cover.jpg", "OEBPS/Images/cover.png",
                    "OPS/images/cover.jpg", "OPS/images/cover.png"
                )
                
                for (pattern in coverPatterns) {
                    val entry = zipFile.getEntry(pattern)
                    if (entry != null) {
                        return saveCover(zipFile, pattern, bookId)
                    }
                }
                
                logger.debug("No cover image found in EPUB: ${file.name}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to extract EPUB cover from ${file.name}: ${e.message}")
            null
        }
    }
    
    /**
     * 从 OPF manifest 中查找封面图片路径
     */
    private fun findCoverFromOpf(zipFile: ZipFile): String? {
        try {
            // 查找 content.opf 位置
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            var opfPath = "EPUB/content.opf"
            
            if (containerEntry != null) {
                EpubArchiveSafety.readBytes(zipFile, containerEntry, EpubArchiveSafety.MAX_XML_BYTES).inputStream().use { stream ->
                    val doc = SecureXml.parse(stream)
                    val rootfiles = doc.getElementsByTagName("rootfile")
                    if (rootfiles.length > 0) {
                        opfPath = rootfiles.item(0).attributes.getNamedItem("full-path")?.nodeValue ?: opfPath
                    }
                }
            }
            
            val opfEntry = zipFile.getEntry(opfPath) ?: return null
            val opfBaseDir = opfPath.substringBeforeLast("/", "")
            
            // 解析 OPF 查找封面
            EpubArchiveSafety.readBytes(zipFile, opfEntry, EpubArchiveSafety.MAX_XML_BYTES).inputStream().use { stream ->
                val doc = SecureXml.parse(stream)
                
                // 方法1: 查找 metadata 中的 cover meta 标签
                val metaTags = doc.getElementsByTagName("meta")
                for (i in 0 until metaTags.length) {
                    val meta = metaTags.item(i)
                    val name = meta.attributes.getNamedItem("name")?.nodeValue
                    val content = meta.attributes.getNamedItem("content")?.nodeValue
                    
                    if (name == "cover" && content != null) {
                        // content 是 manifest 中的 id,需要查找对应的 href
                        val coverHref = findManifestHref(doc, content)
                        if (coverHref != null) {
                            val fullPath = if (opfBaseDir.isEmpty()) coverHref else "$opfBaseDir/$coverHref"
                            logger.debug("Found cover from OPF metadata: $fullPath")
                            return fullPath
                        }
                    }
                }
                
                // 方法2: 在 manifest 中查找 id 包含 "cover" 的图片项
                val manifestItems = doc.getElementsByTagName("item")
                for (i in 0 until manifestItems.length) {
                    val item = manifestItems.item(i)
                    val id = item.attributes.getNamedItem("id")?.nodeValue ?: ""
                    val href = item.attributes.getNamedItem("href")?.nodeValue
                    val mediaType = item.attributes.getNamedItem("media-type")?.nodeValue ?: ""
                    
                    if (id.lowercase().contains("cover") && mediaType.startsWith("image/") && href != null) {
                        val fullPath = if (opfBaseDir.isEmpty()) href else "$opfBaseDir/$href"
                        logger.debug("Found cover from OPF manifest: $fullPath")
                        return fullPath
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse OPF for cover: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 从 manifest 中查找指定 id 的 href
     */
    private fun findManifestHref(doc: Document, id: String): String? {
        val manifestItems = doc.getElementsByTagName("item")
        for (i in 0 until manifestItems.length) {
            val item = manifestItems.item(i)
            val itemId = item.attributes.getNamedItem("id")?.nodeValue
            if (itemId == id) {
                return item.attributes.getNamedItem("href")?.nodeValue
            }
        }
        return null
    }
    
    /**
     * 保存封面到本地
     */
    private fun saveCover(zipFile: ZipFile, entryPath: String, bookId: Int): String? {
        try {
            val entry = zipFile.getEntry(entryPath) ?: return null
            
            val extension = entryPath.substringAfterLast(".").lowercase()
            if (extension !in SUPPORTED_COVER_EXTENSIONS) return null
            
            val coverBytes = EpubArchiveSafety.readBytes(zipFile, entry, EpubArchiveSafety.MAX_IMAGE_BYTES)
            val coverPath = coverBytes.inputStream().use { input ->
                coverStorage.saveCover(bookId, extension, input)
            }
            
            logger.info("Extracted EPUB cover for book $bookId from $entryPath")
            return coverPath
        } catch (e: Exception) {
            logger.error("Failed to save cover: ${e.message}")
            return null
        }
    }
    
    private fun extractElement(doc: Document, namespace: String, tagName: String, fallbackTag: String): String? {
        var value = doc.getElementsByTagNameNS(namespace, tagName)
            .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        
        if (value == null) {
            value = doc.getElementsByTagName(fallbackTag)
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
        }
        
        return value
    }
    
    /**
     * 提取标签 (dc:subject)
     */
    private fun extractTags(doc: Document): List<String> {
        val tags = mutableListOf<String>()
        
        // 尝试从 dc:subject 提取
        val namespace = "http://purl.org/dc/elements/1.1/"
        val subjectNodes = doc.getElementsByTagNameNS(namespace, "subject")
        for (i in 0 until subjectNodes.length) {
            val tag = subjectNodes.item(i)?.textContent?.trim()
            if (!tag.isNullOrBlank() && tag.length <= 50) {
                tags.add(tag)
            }
        }
        
        // 如果命名空间方式失败，尝试直接标签名
        if (tags.isEmpty()) {
            val fallbackNodes = doc.getElementsByTagName("dc:subject")
            for (i in 0 until fallbackNodes.length) {
                val tag = fallbackNodes.item(i)?.textContent?.trim()
                if (!tag.isNullOrBlank() && tag.length <= 50) {
                    tags.add(tag)
                }
            }
        }
        
        logger.debug("Extracted ${tags.size} tags from EPUB: ${tags.joinToString(", ")}")
        return tags.distinct()
    }

    private companion object {
        val SUPPORTED_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}

internal class LegacyCoverStorage(
    private val coversDir: File = File("covers")
) {
    fun saveCover(bookId: Int, extension: String, input: InputStream): String {
        val normalizedExtension = extension.lowercase()
        Files.createDirectories(coversDir.toPath())

        val targetFile = File(coversDir, "book_${bookId}.$normalizedExtension")
        val tempFile = File.createTempFile(".book_${bookId}.", ".$normalizedExtension.tmp", coversDir)

        try {
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
            validateCoverImage(tempFile)
            publish(tempFile, targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        return "/covers/book_${bookId}.$normalizedExtension"
    }

    private fun validateCoverImage(imageFile: File) {
        val image = ImageIO.read(imageFile)
            ?: throw IOException("Invalid cover image data")
        if (image.width <= 0 || image.height <= 0) {
            throw IOException("Invalid cover image dimensions")
        }
    }

    private fun publish(tempFile: File, targetFile: File) {
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
