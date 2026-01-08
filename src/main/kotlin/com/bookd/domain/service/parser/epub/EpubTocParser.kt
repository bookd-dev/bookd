package com.bookd.domain.service.parser.epub

import org.slf4j.LoggerFactory
import org.w3c.dom.Node.ELEMENT_NODE
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element as DomElement

/**
 * EPUB 目录解析器
 * 负责解析 toc.ncx 或 nav.xhtml 获取文档列表
 * 返回所有 spine 项，并标记哪些在 TOC 中
 */
class EpubTocParser {
    
    private val logger = LoggerFactory.getLogger(EpubTocParser::class.java)
    
    data class DocumentInfo(
        val index: Int,
        val href: String,
        val inToc: Boolean,
        val title: String?,
        val level: Int = 0
    )
    
    /**
     * 解析 TOC 并返回所有 spine 文档
     * 
     * @param zipFile EPUB 压缩文件
     * @param baseDir OPF 文件所在目录
     * @param tocHref TOC 文件路径
     * @param spineItems spine 中的文档列表
     * @return 文档信息列表（包含所有 spine 项，TOC 项标记 inToc=true）
     */
    fun parseToc(
        zipFile: ZipFile,
        baseDir: String,
        tocHref: String?,
        spineItems: List<String>
    ): List<DocumentInfo> {
        // 解析 TOC 获取目录项信息
        val tocMap = parseTocMap(zipFile, baseDir, tocHref, spineItems)
        
        // 构建完整的文档列表：所有 spine 项，使用 TOC 信息（如果有）
        return spineItems.mapIndexed { index, href ->
            val tocInfo = tocMap[index]
            if (tocInfo != null) {
                DocumentInfo(
                    index = index,
                    href = href,
                    inToc = true,
                    title = tocInfo.title,
                    level = tocInfo.level
                )
            } else {
                DocumentInfo(
                    index = index,
                    href = href,
                    inToc = false,
                    title = null,
                    level = 0
                )
            }
        }
    }
    
    /**
     * 内部 TOC 项信息
     */
    private data class TocEntry(
        val title: String?,
        val level: Int
    )
    
    /**
     * 解析 TOC 获取 index -> TocEntry 映射
     * 如果 TOC 不存在或解析失败，返回空 Map（所有项 inToc=false）
     */
    private fun parseTocMap(
        zipFile: ZipFile,
        baseDir: String,
        tocHref: String?,
        spineItems: List<String>
    ): Map<Int, TocEntry> {
        // 如果没有 TOC，返回空 Map（所有项 inToc=false）
        if (tocHref == null) {
            logger.info("No TOC found, all spine items will have inToc=false")
            return emptyMap()
        }
        
        // 解析 toc.ncx
        val tocPath = EpubPathUtils.resolveFullPath(baseDir, tocHref)
        val tocEntry = zipFile.getEntry(tocPath) ?: run {
            logger.warn("TOC file not found: $tocPath, all spine items will have inToc=false")
            return emptyMap()
        }
        
        return try {
            zipFile.getInputStream(tocEntry).use { stream ->
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val doc = factory.newDocumentBuilder().parse(stream)
                
                val hrefToIndex = spineItems.withIndex().associate { it.value to it.index }
                
                // 获取 navMap
                val navMap = doc.getElementsByTagName("navMap")
                if (navMap.length == 0) {
                    logger.warn("No navMap found in TOC, all spine items will have inToc=false")
                    return@use emptyMap()
                }
                
                val tocEntries = mutableListOf<Pair<Int, TocEntry>>()
                val navMapElement = navMap.item(0) as DomElement
                val visitedNodes = mutableSetOf<DomElement>()
                
                // 解析 navPoints
                val children = navMapElement.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == ELEMENT_NODE && child.nodeName == "navPoint") {
                        parseNavPointRecursive(
                            child as DomElement,
                            hrefToIndex,
                            tocEntries,
                            0,
                            visitedNodes
                        )
                    }
                }
                
                logger.info("Parsed ${tocEntries.size} TOC entries")
                
                // 去重：同一个 index 只保留第一个
                tocEntries.distinctBy { it.first }.toMap()
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse TOC, all spine items will have inToc=false", e)
            emptyMap()
        }
    }
    
    /**
     * 递归解析 navPoint
     */
    private fun parseNavPointRecursive(
        navPoint: DomElement,
        hrefToIndex: Map<String, Int>,
        entries: MutableList<Pair<Int, TocEntry>>,
        level: Int,
        visitedNodes: MutableSet<DomElement>,
        maxDepth: Int = 50
    ) {
        if (level > maxDepth) {
            logger.warn("Reached maximum recursion depth ($maxDepth) while parsing TOC")
            return
        }
        
        if (!visitedNodes.add(navPoint)) {
            logger.warn("Detected circular reference in TOC structure")
            return
        }
        
        // 获取标题
        var title: String? = null
        val children = navPoint.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == ELEMENT_NODE && child.nodeName == "navLabel") {
                val navLabel = child as DomElement
                val textNodes = navLabel.getElementsByTagName("text")
                if (textNodes.length > 0) {
                    title = textNodes.item(0).textContent?.trim()
                }
                break
            }
        }
        
        // 获取链接
        var href: String? = null
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == ELEMENT_NODE && child.nodeName == "content") {
                val src = child.attributes.getNamedItem("src")?.nodeValue
                if (src != null) {
                    href = src.substringBefore("#")
                }
                break
            }
        }
        
        // 添加 TOC 条目（如果在 spine 中存在）
        if (href != null) {
            val index = hrefToIndex[href]
            if (index != null) {
                entries.add(index to TocEntry(title, level))
            }
        }
        
        // 递归处理子 navPoint
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == ELEMENT_NODE && child.nodeName == "navPoint") {
                parseNavPointRecursive(
                    child as DomElement,
                    hrefToIndex,
                    entries,
                    level + 1,
                    visitedNodes,
                    maxDepth
                )
            }
        }
    }
}
