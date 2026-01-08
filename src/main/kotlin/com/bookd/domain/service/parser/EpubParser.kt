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
        val chapters: List<DocumentInfo>,
        val resources: Map<String, ByteArray>
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
                
                // 4. 提取资源
                val resources = resourceExtractor.extractResources(zipFile, packageInfo.baseDir)
                
                return EpubStructure(chapters, resources)
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
        try {
            ZipFile(file).use { zipFile ->
                // 读取包信息获取基础目录
                val packageInfo = packageReader.readPackage(zipFile) ?: return null
                val fullPath = EpubPathUtils.resolveFullPath(packageInfo.baseDir, href)
                
                val entry = zipFile.getEntry(fullPath) ?: return null
                val html = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
                
                return contentParser.parseHtml(html, href)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse chapter content: $href", e)
            return null
        }
    }
}
