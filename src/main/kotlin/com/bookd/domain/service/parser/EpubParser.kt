package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import com.bookd.domain.model.TextStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import org.w3c.dom.Node.ELEMENT_NODE
import org.w3c.dom.Element as DomElement
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {
    
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
                // 1. 找到 content.opf 路径
                val opfPath = findOpfPath(zipFile) ?: "EPUB/content.opf"
                logger.info("Found OPF path: $opfPath")
                
                // 2. 解析 content.opf 获取 spine 和 toc
                val opfEntry = zipFile.getEntry(opfPath) ?: return null
                val opfBaseDir = opfPath.substringBeforeLast("/", "")
                
                val (spineItems, tocHref) = parseOpf(zipFile, opfEntry)
                logger.info("Found ${spineItems.size} spine items")
                
                // 3. 解析 toc.ncx 或 nav.xhtml 获取章节标题
                val chapters = parseToc(zipFile, opfBaseDir, tocHref, spineItems)
                logger.info("Parsed ${chapters.size} chapters")
                
                // 4. 提取资源（图片等）
                val resources = extractResources(zipFile, opfBaseDir)
                logger.info("Extracted ${resources.size} resources")
                
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
                val opfPath = findOpfPath(zipFile) ?: "EPUB/content.opf"
                val baseDir = opfPath.substringBeforeLast("/", "")
                val fullPath = if (baseDir.isEmpty()) href else "$baseDir/$href"
                
                val entry = zipFile.getEntry(fullPath) ?: return null
                val html = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
                
                // Pass chapter href to resolve relative image paths
                return parseHtmlToElements(html, baseDir, href)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse chapter content: $href", e)
            return null
        }
    }
    
    /**
     * 查找 content.opf 文件路径
     */
    private fun findOpfPath(zipFile: ZipFile): String? {
        val containerEntry = zipFile.getEntry("META-INF/container.xml") ?: return null
        
        return zipFile.getInputStream(containerEntry).use { stream ->
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(stream)
            val rootfiles = doc.getElementsByTagName("rootfile")
            
            if (rootfiles.length > 0) {
                rootfiles.item(0).attributes.getNamedItem("full-path")?.nodeValue
            } else null
        }
    }
    
    /**
     * 解析 OPF 文件获取 spine 和 toc 路径
     */
    private fun parseOpf(zipFile: ZipFile, opfEntry: ZipEntry): Pair<List<String>, String?> {
        val spineItems = mutableListOf<String>()
        var tocHref: String? = null
        
        zipFile.getInputStream(opfEntry).use { stream ->
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(stream)
            
            // 获取 manifest 中的 id -> href 映射
            val manifestMap = mutableMapOf<String, String>()
            val manifest = doc.getElementsByTagName("manifest")
            if (manifest.length > 0) {
                val items = manifest.item(0).childNodes
                for (i in 0 until items.length) {
                    val node = items.item(i)
                    if (node.nodeName == "item") {
                        val id = node.attributes.getNamedItem("id")?.nodeValue
                        val href = node.attributes.getNamedItem("href")?.nodeValue
                        val mediaType = node.attributes.getNamedItem("media-type")?.nodeValue
                        
                        if (id != null && href != null) {
                            manifestMap[id] = href
                            
                            // 查找 TOC
                            if (mediaType == "application/x-dtbncx+xml" || id == "ncx") {
                                tocHref = href
                            }
                        }
                    }
                }
            }
            
            // 获取 spine 顺序
            val spine = doc.getElementsByTagName("spine")
            if (spine.length > 0) {
                val itemrefs = spine.item(0).childNodes
                for (i in 0 until itemrefs.length) {
                    val node = itemrefs.item(i)
                    if (node.nodeName == "itemref") {
                        val idref = node.attributes.getNamedItem("idref")?.nodeValue
                        if (idref != null) {
                            manifestMap[idref]?.let { spineItems.add(it) }
                        }
                    }
                }
            }
        }
        
        return spineItems to tocHref
    }
    
    /**
     * 解析 TOC (toc.ncx) 获取章节标题
     */
    private fun parseToc(
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
        val tocPath = if (baseDir.isEmpty()) tocHref else "$baseDir/$tocHref"
        val tocEntry = zipFile.getEntry(tocPath) ?: return spineItems.mapIndexed { index, href ->
            ChapterInfo(index, detectChapterTitle(href), href)
        }
        
        try {
            zipFile.getInputStream(tocEntry).use { stream ->
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val doc = factory.newDocumentBuilder().parse(stream)
                
                val hrefToIndex = spineItems.withIndex().associate { it.value to it.index }
                
                // 只获取 navMap 下的直接子 navPoint，然后递归处理
                val navMap = doc.getElementsByTagName("navMap")
                if (navMap.length == 0) {
                    return spineItems.mapIndexed { index, href ->
                        ChapterInfo(index, detectChapterTitle(href), href)
                    }
                }
                
                val tocChapters = mutableListOf<ChapterInfo>()
                val navMapElement = navMap.item(0) as DomElement
                
                // 创建访问节点集合，用于防止循环引用
                val visitedNodes = mutableSetOf<DomElement>()
                
                // 只处理 navMap 的直接子 navPoint
                val children = navMapElement.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == ELEMENT_NODE &&
                        child.nodeName == "navPoint") {
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
                
                // Create a map of spine index to TOC chapter info
                val tocInfoMap = uniqueTocChapters.toMap()
                
                logger.info("TOC info map has ${tocInfoMap.size} entries for indices: ${tocInfoMap.keys.sorted()}")
                logger.info("Spine has ${spineItems.size} items")
                
                // Include ALL spine items, using TOC info when available
                val allChapters = spineItems.mapIndexed { index, href ->
                    val tocInfo = tocInfoMap[index]
                    if (tocInfo != null) {
                        // Use TOC information
                        logger.debug("Using TOC info for index $index")
                        tocInfo
                    } else {
                        // Spine item not in TOC - use detected title
                        logger.debug("No TOC info for index $index, href=$href")
                        ChapterInfo(index, detectChapterTitle(href), href, 0)
                    }
                }
                
                logger.info("After including all spine items: ${allChapters.size} chapters")
                return allChapters
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse TOC, using spine items", e)
            return spineItems.mapIndexed { index, href ->
                ChapterInfo(index, detectChapterTitle(href), href)
            }
        }
    }
    
    /**
     * 递归解析单个 navPoint 及其直接子 navPoint
     */
    private fun parseNavPointRecursive(
        navPoint: DomElement,
        hrefToIndex: Map<String, Int>,
        chapters: MutableList<ChapterInfo>,
        level: Int,
        visitedNodes: MutableSet<DomElement> = mutableSetOf(),
        maxDepth: Int = 50
    ) {
        // 防止无限递归：检查最大深度
        if (level > maxDepth) {
            logger.warn("Reached maximum recursion depth ($maxDepth) while parsing TOC, stopping recursion")
            return
        }
        
        // 防止循环引用：检查是否已访问过此节点
        if (!visitedNodes.add(navPoint)) {
            logger.warn("Detected circular reference in TOC structure, skipping node")
            return
        }
        
        // 获取标题 - 只获取直接子元素的 text
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
        
        // 获取链接 - 只获取直接子元素的 content
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
        
        // 递归处理直接子 navPoint
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
        
        // 尝试匹配常见章节模式
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
    
    /**
     * 提取资源文件（图片等）
     */
    private fun extractResources(zipFile: ZipFile, baseDir: String): Map<String, ByteArray> {
        val resources = mutableMapOf<String, ByteArray>()
        
        zipFile.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry ->
                val name = entry.name.lowercase()
                name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".svg") || name.endsWith(".webp")
            }
            .forEach { entry ->
                try {
                    val bytes = zipFile.getInputStream(entry).readBytes()
                    val relativePath = if (baseDir.isNotEmpty() && entry.name.startsWith(baseDir)) {
                        entry.name.removePrefix("$baseDir/")
                    } else {
                        entry.name
                    }
                    resources[relativePath] = bytes
                } catch (e: Exception) {
                    logger.warn("Failed to extract resource: ${entry.name}", e)
                }
            }
        
        return resources
    }
    
    /**
     * 将 HTML 解析为结构化内容元素
     */
    private fun parseHtmlToElements(html: String, baseDir: String, chapterHref: String): List<ContentElement> {
        val elements = mutableListOf<ContentElement>()
        
        try {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            
            body.children().forEach { element ->
                parseElement(element, elements, baseDir, chapterHref)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse HTML to elements", e)
        }
        
        return elements
    }
    
    /**
     * 递归解析 HTML 元素
     */
    private fun parseElement(element: Element, elements: MutableList<ContentElement>, baseDir: String, chapterHref: String) {
        when (val tagName = element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tagName.substring(1).toIntOrNull() ?: 1
                elements.add(ContentElement.Heading(level, element.text()))
            }
            "p" -> {
                // Check if paragraph contains images
                val images = element.select("img")
                if (images.isNotEmpty()) {
                    // If paragraph has images, extract them as separate Image elements
                    images.forEach { img ->
                        val src = img.attr("src")
                        val alt = img.attr("alt")
                        if (src.isNotEmpty()) {
                            val normalizedSrc = normalizeImagePath(src, chapterHref)
                            elements.add(ContentElement.Image(normalizedSrc, alt))
                        }
                    }
                }
                
                // Parse text content (excluding images)
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Paragraph(spans))
                }
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                if (src.isNotEmpty()) {
                    // Normalize image path relative to chapter location
                    val normalizedSrc = normalizeImagePath(src, chapterHref)
                    elements.add(ContentElement.Image(normalizedSrc, alt))
                }
            }
            "blockquote" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Quote(spans))
                }
            }
            "pre", "code" -> {
                elements.add(ContentElement.Code(element.text()))
            }
            "ul", "ol" -> {
                val ordered = element.tagName() == "ol"
                val items = element.children()
                    .filter { it.tagName() == "li" }
                    .map { com.bookd.domain.model.ListItem(parseInlineElements(it)) }
                
                if (items.isNotEmpty()) {
                    elements.add(ContentElement.ListBlock(ordered, items))
                }
            }
            "hr" -> {
                elements.add(ContentElement.Divider)
            }
            "div", "section", "article" -> {
                // Check if this div has direct text content (mixed text and br tags)
                val hasDirectText = element.childNodes().any { it is TextNode && it.text().trim().isNotEmpty() }
                val hasBrTags = element.select("br").isNotEmpty()
                
                if (hasDirectText && hasBrTags) {
                    // This div contains text mixed with <br/> tags
                    // Process line by line, treating <br/> as paragraph separator
                    val currentLineText = StringBuilder()
                    
                    element.childNodes().forEach { node ->
                        when (node) {
                            is TextNode -> {
                                val text = node.text()
                                currentLineText.append(text)
                            }

                            is Element if node.tagName().lowercase() == "br" -> {
                                // <br/> marks end of line, flush current text as paragraph
                                val text = currentLineText.toString().trim()
                                if (text.isNotEmpty()) {
                                    elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                                }
                                currentLineText.clear()
                            }

                            is Element if node.tagName().lowercase() == "img" -> {
                                // Flush any text before image
                                val text = currentLineText.toString().trim()
                                if (text.isNotEmpty()) {
                                    elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                                    currentLineText.clear()
                                }
                                // Add image
                                val src = node.attr("src")
                                val alt = node.attr("alt")
                                if (src.isNotEmpty()) {
                                    val normalizedSrc = normalizeImagePath(src, chapterHref)
                                    elements.add(ContentElement.Image(normalizedSrc, alt))
                                }
                            }

                            is Element -> {
                                // Other inline elements like <strong>, <em> etc
                                currentLineText.append(node.text())
                            }
                        }
                    }
                    
                    // Flush remaining text
                    val text = currentLineText.toString().trim()
                    if (text.isNotEmpty()) {
                        elements.add(ContentElement.Paragraph(listOf(TextSpan(text))))
                    }
                } else {
                    // 递归处理容器元素
                    element.children().forEach { child ->
                        parseElement(child, elements, baseDir, chapterHref)
                    }
                }
            }
        }
    }
    
    /**
     * 解析行内元素为 TextSpan
     */
    private fun parseInlineElements(element: Element): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        
        element.childNodes().forEach { node ->
            when (node) {
                is TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) {
                        spans.add(TextSpan(text))
                    }
                }
                is Element -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) {
                        val styles = mutableListOf<TextStyle>()
                        var link: String? = null
                        
                        when (node.tagName().lowercase()) {
                            "strong", "b" -> styles.add(TextStyle.BOLD)
                            "em", "i" -> styles.add(TextStyle.ITALIC)
                            "u" -> styles.add(TextStyle.UNDERLINE)
                            "s", "strike", "del" -> styles.add(TextStyle.STRIKETHROUGH)
                            "code" -> styles.add(TextStyle.CODE)
                            "a" -> {
                                styles.add(TextStyle.UNDERLINE)
                                link = node.attr("href")
                            }
                        }
                        
                        spans.add(TextSpan(text, styles, link))
                    }
                }
            }
        }
        
        return spans
    }
    
    /**
     * Normalize image path relative to chapter location
     * Converts relative paths like "../Images/xxx.jpg" to "Images/xxx.jpg"
     */
    private fun normalizeImagePath(src: String, chapterHref: String): String {
        // If already absolute or has protocol, return as is
        if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("/")) {
            return src
        }
        
        // Get the directory of the chapter
        val chapterDir = if (chapterHref.contains("/")) {
            chapterHref.substringBeforeLast("/")
        } else {
            ""
        }
        
        // Resolve relative path
        val parts = mutableListOf<String>()
        if (chapterDir.isNotEmpty()) {
            parts.addAll(chapterDir.split("/"))
        }
        
        src.split("/").forEach { part ->
            when (part) {
                "." -> {} // current directory, skip
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1) // parent directory
                "" -> {} // empty part, skip
                else -> parts.add(part)
            }
        }
        
        return parts.joinToString("/")
    }
}
