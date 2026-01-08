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
        val chapters: List<ChapterInfo>,
        val resources: Map<String, ByteArray>
    )
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val href: String,
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
                
                // 2. 解析目录
                val tocChapters = tocParser.parseToc(
                    zipFile,
                    packageInfo.baseDir,
                    packageInfo.tocHref,
                    packageInfo.spineItems
                )
                logger.info("Parsed ${tocChapters.size} chapters")
                
                // 3. 转换章节信息格式
                val chapters = tocChapters.map { 
                    ChapterInfo(it.index, it.title, it.href, it.level)
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
     * 解析章节内容为结构化元素
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
