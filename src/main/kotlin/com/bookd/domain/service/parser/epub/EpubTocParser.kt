package com.bookd.domain.service.parser.epub

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.w3c.dom.Node.ELEMENT_NODE
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element as DomElement

/**
 * EPUB 目录解析器
 * 负责解析 nav.xhtml (EPUB 3) 或 toc.ncx (EPUB 2) 获取文档列表
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
     * 优先使用 nav.xhtml (EPUB 3)，回退到 toc.ncx (EPUB 2)
     * 
     * @param zipFile EPUB 压缩文件
     * @param baseDir OPF 文件所在目录
     * @param ncxHref NCX 目录文件路径 (EPUB 2)
     * @param navHref Nav 目录文件路径 (EPUB 3)
     * @param spineItems spine 中的文档列表
     * @return 文档信息列表（包含所有 spine 项，TOC 项标记 inToc=true）
     */
    fun parseToc(
        zipFile: ZipFile,
        baseDir: String,
        ncxHref: String?,
        navHref: String?,
        spineItems: List<String>
    ): List<DocumentInfo> {
        // 优先使用 nav.xhtml (EPUB 3)
        var tocMap = if (navHref != null) {
            parseNavXhtml(zipFile, baseDir, navHref, spineItems)
        } else {
            emptyMap()
        }
        
        // 如果 nav.xhtml 解析失败或为空，回退到 ncx
        if (tocMap.isEmpty() && ncxHref != null) {
            tocMap = parseNcx(zipFile, baseDir, ncxHref, spineItems)
        }
        
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
     * 兼容旧接口：只传入 tocHref（NCX）
     */
    fun parseToc(
        zipFile: ZipFile,
        baseDir: String,
        tocHref: String?,
        spineItems: List<String>
    ): List<DocumentInfo> {
        return parseToc(zipFile, baseDir, ncxHref = tocHref, navHref = null, spineItems = spineItems)
    }
    
    /**
     * 内部 TOC 项信息
     */
    private data class TocEntry(
        val title: String?,
        val level: Int
    )
    
    // ==================== Nav.xhtml 解析 (EPUB 3) ====================
    
    /**
     * 解析 nav.xhtml (EPUB 3 导航文档)
     */
    private fun parseNavXhtml(
        zipFile: ZipFile,
        baseDir: String,
        navHref: String,
        spineItems: List<String>
    ): Map<Int, TocEntry> {
        val navPath = EpubPathUtils.resolveFullPath(baseDir, navHref)
        val navEntry = zipFile.getEntry(navPath) ?: run {
            logger.warn("Nav file not found: $navPath")
            return emptyMap()
        }
        
        return try {
            zipFile.getInputStream(navEntry).use { stream ->
                val html = stream.bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(html)
                
                // 查找 epub:type="toc" 的 nav 元素
                val tocNav = doc.select("nav[epub|type=toc]").firstOrNull()
                    ?: doc.select("nav#toc").firstOrNull()
                    ?: doc.select("nav").firstOrNull()
                
                if (tocNav == null) {
                    logger.warn("No toc nav element found in nav.xhtml")
                    return@use emptyMap()
                }
                
                val hrefToIndex = spineItems.withIndex().associate { it.value to it.index }
                val tocEntries = mutableListOf<Pair<Int, TocEntry>>()
                
                // 解析 ol > li > a 结构
                val rootOl = tocNav.selectFirst("ol")
                if (rootOl != null) {
                    parseNavListRecursive(rootOl, hrefToIndex, tocEntries, 0)
                }
                
                logger.info("Parsed ${tocEntries.size} TOC entries from nav.xhtml")
                
                // 去重：同一个 index 只保留第一个
                tocEntries.distinctBy { it.first }.toMap()
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse nav.xhtml", e)
            emptyMap()
        }
    }
    
    /**
     * 递归解析 nav.xhtml 中的 ol 列表
     */
    private fun parseNavListRecursive(
        ol: org.jsoup.nodes.Element,
        hrefToIndex: Map<String, Int>,
        entries: MutableList<Pair<Int, TocEntry>>,
        level: Int,
        maxDepth: Int = 50
    ) {
        if (level > maxDepth) {
            logger.warn("Reached maximum recursion depth ($maxDepth) while parsing nav")
            return
        }
        
        for (li in ol.children().filter { it.tagName() == "li" }) {
            // 获取链接
            val anchor = li.selectFirst("a")
            if (anchor != null) {
                val href = anchor.attr("href").substringBefore("#")
                val title = anchor.text().trim()
                
                val index = hrefToIndex[href]
                if (index != null && title.isNotEmpty()) {
                    entries.add(index to TocEntry(title, level))
                }
            }
            
            // 递归处理子列表
            val childOl = li.selectFirst("> ol")
            if (childOl != null) {
                parseNavListRecursive(childOl, hrefToIndex, entries, level + 1, maxDepth)
            }
        }
    }
    
    // ==================== NCX 解析 (EPUB 2) ====================
    
    /**
     * 解析 toc.ncx (EPUB 2 导航文档)
     */
    private fun parseNcx(
        zipFile: ZipFile,
        baseDir: String,
        ncxHref: String,
        spineItems: List<String>
    ): Map<Int, TocEntry> {
        val ncxPath = EpubPathUtils.resolveFullPath(baseDir, ncxHref)
        val ncxEntry = zipFile.getEntry(ncxPath) ?: run {
            logger.warn("NCX file not found: $ncxPath")
            return emptyMap()
        }
        
        return try {
            zipFile.getInputStream(ncxEntry).use { stream ->
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val doc = factory.newDocumentBuilder().parse(stream)
                
                val hrefToIndex = spineItems.withIndex().associate { it.value to it.index }
                
                // 获取 navMap
                val navMap = doc.getElementsByTagName("navMap")
                if (navMap.length == 0) {
                    logger.warn("No navMap found in NCX")
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
                
                logger.info("Parsed ${tocEntries.size} TOC entries from NCX")
                
                // 去重：同一个 index 只保留第一个
                tocEntries.distinctBy { it.first }.toMap()
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse NCX", e)
            emptyMap()
        }
    }
    
    /**
     * 递归解析 navPoint (NCX)
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
            logger.warn("Reached maximum recursion depth ($maxDepth) while parsing NCX")
            return
        }
        
        if (!visitedNodes.add(navPoint)) {
            logger.warn("Detected circular reference in NCX structure")
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
