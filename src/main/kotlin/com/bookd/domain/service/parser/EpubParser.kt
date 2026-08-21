package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.parser.epub.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * EPUB 解析器
 * 协调各组件完成 EPUB 文件的解析
 */
class EpubParser(
    private val packageReader: EpubPackageReader = EpubPackageReader(),
    private val tocParser: EpubTocParser = EpubTocParser(),
    private val resourceExtractor: EpubResourceExtractor = EpubResourceExtractor(),
    private val contentParser: EpubContentParser = EpubContentParser()
) {
    private val logger = LoggerFactory.getLogger(EpubParser::class.java)
    
    data class EpubStructure(
        val chapters: List<DocumentInfo>
    )
    
    /**
     * 文档信息（对应 spine 中的一个项）
     */
    data class DocumentInfo(
        val index: Int,
        val href: String,
        val inToc: Boolean,
        val title: String?,
        val level: Int = 0
    )
    
    /**
     * 解析 EPUB 文件结构
     */
    fun parseStructure(file: File): EpubStructure? {
        try {
            ZipFile(file).use { zipFile ->
                EpubArchiveSafety.validate(zipFile)
                // 1. 读取包信息
                val packageInfo = packageReader.readPackage(zipFile) ?: return null
                
                // 2. 解析目录，返回所有 spine 文档
                // 优先使用 nav.xhtml (EPUB 3)，回退到 ncx (EPUB 2)
                val documents = tocParser.parseToc(
                    zipFile,
                    packageInfo.baseDir,
                    ncxHref = packageInfo.tocHref,
                    navHref = packageInfo.navHref,
                    spineItems = packageInfo.spineItems
                )
                
                val tocCount = documents.count { it.inToc }
                logger.info("Parsed ${documents.size} documents (${tocCount} in TOC)")
                
                // 3. 转换为本地数据格式
                val chapters = documents.map { 
                    DocumentInfo(it.index, it.href, it.inToc, it.title, it.level)
                }
                
                return EpubStructure(chapters)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse EPUB structure: ${file.name}", e)
            return null
        }
    }
    
    /**
     * 解析文档内容为结构化元素
     */
    fun parseChapterContent(file: File, href: String): List<ContentElement>? {
        return parseChapterContents(file, listOf(href))?.get(href)
    }

    /**
     * 批量解析章节：单次打开 EPUB 并只读取一次包信息，避免章节数增长时重复解析 OPF。
     */
    fun parseChapterContents(file: File, hrefs: List<String>): Map<String, List<ContentElement>>? {
        try {
            ZipFile(file).use { zipFile ->
                EpubArchiveSafety.validate(zipFile)
                // 读取包信息获取基础目录
                val packageInfo = packageReader.readPackage(zipFile) ?: return null
                return hrefs.associateWith { href ->
                    val fullPath = EpubPathUtils.resolveFullPath(packageInfo.baseDir, href)
                    val entry = zipFile.getEntry(fullPath)
                        ?: throw EpubArchiveException("EPUB chapter entry not found: $href")
                    val html = EpubArchiveSafety.readText(zipFile, entry, EpubArchiveSafety.MAX_CHAPTER_BYTES)
                    contentParser.parseHtml(html, href)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse EPUB chapter content", e)
            return null
        }
    }

    /** 逐个提取图片，避免大体积 EPUB 的全部资源同时驻留内存。 */
    fun forEachResource(file: File, consumer: (path: String, bytes: ByteArray) -> Unit) {
        ZipFile(file).use { zipFile ->
            EpubArchiveSafety.validate(zipFile)
            val packageInfo = packageReader.readPackage(zipFile)
                ?: throw EpubArchiveException("Failed to read EPUB package")
            resourceExtractor.forEachResource(zipFile, packageInfo.baseDir, consumer)
        }
    }
}
