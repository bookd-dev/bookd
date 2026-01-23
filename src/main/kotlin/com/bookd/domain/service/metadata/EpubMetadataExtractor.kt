package com.bookd.domain.service.metadata

import com.bookd.config.EbookParserConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 元数据提取器（支持微服务降级）
 * 
 * 优先使用 Python ebooklib 微服务解析，失败时降级到本地 Kotlin 解析器
 */
class EpubMetadataExtractor(
    private val parserClient: EbookParserClient?,
    private val config: EbookParserConfig
) : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(EpubMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        // 优先使用微服务
        if (config.enabled && parserClient != null) {
            return runBlocking {
                try {
                    // 注意：这里需要一个 bookId，但在这个阶段可能还没有
                    // 使用文件哈希码作为临时 ID
                    val tempBookId = file.absolutePath.hashCode()
                    val result = parserClient.extractMetadata(file.absolutePath, tempBookId)
                    if (result != null) {
                        logger.info("Successfully extracted metadata via eBook Parser service: ${file.name}")
                        return@runBlocking result
                    } else {
                        logger.warn("eBook Parser service returned null, falling back to local parser")
                    }
                } catch (e: Exception) {
                    logger.warn("eBook Parser service failed, falling back to local parser: ${e.message}")
                }
                null
            }?.let { return it }
        }
        
        // 降级到本地实现
        logger.debug("Using local EPUB parser for: ${file.name}")
        return extractMetadataLocal(file)
    }
    
    /**
     * 本地 EPUB 元数据提取实现（原有逻辑）
     */
    private fun extractMetadataLocal(file: File): BookMetadata? {
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
        // 优先使用微服务
        if (config.enabled && parserClient != null) {
            return runBlocking {
                try {
                    val result = parserClient.extractCover(file.absolutePath, bookId)
                    if (result != null) {
                        logger.info("Successfully extracted cover via EPUB Parser service: ${file.name}")
                        return@runBlocking result
                    } else {
                        logger.warn("EPUB Parser service failed to extract cover, falling back to local parser")
                    }
                } catch (e: Exception) {
                    logger.warn("EPUB Parser service failed, falling back to local parser: ${e.message}")
                }
                null
            }?.let { return it }
        }
        
        // 降级到本地实现
        logger.debug("Using local EPUB cover extractor for: ${file.name}")
        return extractCoverLocal(file, bookId)
    }
    
    /**
     * 本地 EPUB 封面提取实现（原有逻辑）
     */
    private fun extractCoverLocal(file: File, bookId: Int): String? {
        return try {
            ZipFile(file).use { zipFile ->
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
                zipFile.getInputStream(containerEntry).use { stream ->
                    val factory = DocumentBuilderFactory.newInstance()
                    factory.isNamespaceAware = true
                    val doc = factory.newDocumentBuilder().parse(stream)
                    val rootfiles = doc.getElementsByTagName("rootfile")
                    if (rootfiles.length > 0) {
                        opfPath = rootfiles.item(0).attributes.getNamedItem("full-path")?.nodeValue ?: opfPath
                    }
                }
            }
            
            val opfEntry = zipFile.getEntry(opfPath) ?: return null
            val opfBaseDir = opfPath.substringBeforeLast("/", "")
            
            // 解析 OPF 查找封面
            zipFile.getInputStream(opfEntry).use { stream ->
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val doc = factory.newDocumentBuilder().parse(stream)
                
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
            
            val coversDir = File("covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }
            
            val extension = entryPath.substringAfterLast(".").lowercase()
            val outputFile = File(coversDir, "book_${bookId}.$extension")
            
            zipFile.getInputStream(entry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            logger.info("Extracted EPUB cover for book $bookId from $entryPath")
            return "/covers/book_${bookId}.$extension"
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
}
