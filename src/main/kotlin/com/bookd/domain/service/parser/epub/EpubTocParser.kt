package com.bookd.domain.service.parser.epub

import org.slf4j.LoggerFactory
import org.w3c.dom.Node.ELEMENT_NODE
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element as DomElement

/**
 * EPUB 目录解析器
 * 负责解析 toc.ncx 或 nav.xhtml 获取章节列表
 */
class EpubTocParser {
    
    private val logger = LoggerFactory.getLogger(EpubTocParser::class.java)
    
    data class ChapterInfo(
        val index: Int,
        val title: String?,
        val href: String,
        val level: Int = 0
    )
    
    /**
     * 解析 TOC 获取章节列表
     * @param zipFile EPUB 压缩文件
     * @param baseDir OPF 文件所在目录
     * @param tocHref TOC 文件路径
     * @param spineItems spine 中的文档列表
     * @return 章节信息列表
     */
    fun parseToc(
        zipFile: ZipFile,
        baseDir: String,
        tocHref: String?,
        spineItems: List<String>
    ): List<ChapterInfo> {
        // 如果没有 TOC，根据 spine 生成默认章节
        if (tocHref == null) {
            return spineItems.mapIndexed { index, href ->
                ChapterInfo(index, detectChapterTitle(href), href)
            }
        }
        
        // 解析 toc.ncx
        val tocPath = EpubPathUtils.resolveFullPath(baseDir, tocHref)
        val tocEntry = zipFile.getEntry(tocPath) ?: return spineItems.mapIndexed { index, href ->
            ChapterInfo(index, detectChapterTitle(href), href)
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
                    return@use spineItems.mapIndexed { index, href ->
                        ChapterInfo(index, detectChapterTitle(href), href)
                    }
                }
                
                val tocChapters = mutableListOf<ChapterInfo>()
                val navMapElement = navMap.item(0) as DomElement
                val visitedNodes = mutableSetOf<DomElement>()
                
                // 只处理 navMap 的直接子 navPoint
                val children = navMapElement.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == ELEMENT_NODE && child.nodeName == "navPoint") {
                        parseNavPointRecursive(
                            child as DomElement,
                            hrefToIndex,
                            tocChapters,
                            0,
                            visitedNodes
                        )
                    }
                }
                
                logger.info("Parsed ${tocChapters.size} chapters from TOC (before dedup)")
                
                // 去重：同一个 index 只保留第一个章节
                val uniqueTocChapters = tocChapters
                    .groupBy { it.index }
                    .mapValues { it.value.first() }
                
                val tocInfoMap = uniqueTocChapters.toMap()
                
                // 包含所有 spine 项，使用 TOC 信息（如果有）
                spineItems.mapIndexed { index, href ->
                    tocInfoMap[index] ?: ChapterInfo(index, detectChapterTitle(href), href, 0)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse TOC, using spine items", e)
            spineItems.mapIndexed { index, href ->
                ChapterInfo(index, detectChapterTitle(href), href)
            }
        }
    }
    
    /**
     * 递归解析 navPoint
     */
    private fun parseNavPointRecursive(
        navPoint: DomElement,
        hrefToIndex: Map<String, Int>,
        chapters: MutableList<ChapterInfo>,
        level: Int,
        visitedNodes: MutableSet<DomElement>,
        maxDepth: Int = 50
    ) {
        // 防止无限递归
        if (level > maxDepth) {
            logger.warn("Reached maximum recursion depth ($maxDepth) while parsing TOC")
            return
        }
        
        // 防止循环引用
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
        
        // 添加章节（如果在 spine 中存在）
        if (href != null) {
            val index = hrefToIndex[href]
            if (index != null) {
                chapters.add(ChapterInfo(index, title, href, level))
            }
        }
        
        // 递归处理子 navPoint
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == ELEMENT_NODE && child.nodeName == "navPoint") {
                parseNavPointRecursive(
                    child as DomElement,
                    hrefToIndex,
                    chapters,
                    level + 1,
                    visitedNodes,
                    maxDepth
                )
            }
        }
    }
    
    /**
     * 从文件名检测章节标题
     */
    private fun detectChapterTitle(href: String): String {
        val fileName = href.substringAfterLast("/").substringBeforeLast(".")
        
        val patterns = listOf(
            """(?i)chapter[\s_-]*(\d+)""".toRegex(),
            """(?i)ch[\s_-]*(\d+)""".toRegex(),
            """第([零一二三四五六七八九十百千万\d]+)[章节回]""".toRegex(),
            """([零一二三四五六七八九十百千万\d]+)[章节回]""".toRegex()
        )
        
        patterns.forEach { pattern ->
            pattern.find(fileName)?.let { match ->
                return match.value
            }
        }
        
        return fileName.replace("_", " ").replace("-", " ")
    }
}
