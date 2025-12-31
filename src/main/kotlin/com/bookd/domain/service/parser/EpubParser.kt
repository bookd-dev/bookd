package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.model.TextSpan
import com.bookd.domain.model.TextStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.File
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
                
                return parseHtmlToElements(html, baseDir)
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
    private fun parseOpf(zipFile: ZipFile, opfEntry: java.util.zip.ZipEntry): Pair<List<String>, String?> {
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
        val chapters = mutableListOf<ChapterInfo>()
        
        // 如果没有 TOC，根据 spine 生成默认章节
        if (tocHref == null) {
            spineItems.forEachIndexed { index, href ->
                chapters.add(ChapterInfo(index, detectChapterTitle(href), href))
            }
            return chapters
        }
        
        // 解析 toc.ncx
        val tocPath = if (baseDir.isEmpty()) tocHref else "$baseDir/$tocHref"
        val tocEntry = zipFile.getEntry(tocPath) ?: return chapters
        
        try {
            zipFile.getInputStream(tocEntry).use { stream ->
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val doc = factory.newDocumentBuilder().parse(stream)
                
                val navPoints = doc.getElementsByTagName("navPoint")
                val hrefToIndex = spineItems.withIndex().associate { it.value to it.index }
                
                parseNavPoints(navPoints, hrefToIndex, chapters, 0)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse TOC, using spine items", e)
            spineItems.forEachIndexed { index, href ->
                chapters.add(ChapterInfo(index, detectChapterTitle(href), href))
            }
        }
        
        return chapters.sortedBy { it.index }
    }
    
    /**
     * 递归解析 navPoint
     */
    private fun parseNavPoints(
        navPoints: org.w3c.dom.NodeList,
        hrefToIndex: Map<String, Int>,
        chapters: MutableList<ChapterInfo>,
        level: Int
    ) {
        for (i in 0 until navPoints.length) {
            val navPoint = navPoints.item(i)
            
            // 获取标题
            val navLabel = (navPoint as org.w3c.dom.Element).getElementsByTagName("navLabel")
            val title = if (navLabel.length > 0) {
                val text = navLabel.item(0).textContent?.trim()
                text
            } else null
            
            // 获取链接
            val content = navPoint.getElementsByTagName("content")
            if (content.length > 0) {
                val src = content.item(0).attributes.getNamedItem("src")?.nodeValue
                if (src != null) {
                    val href = src.substringBefore("#")
                    val index = hrefToIndex[href] ?: hrefToIndex.values.maxOrNull()?.plus(1) ?: 0
                    chapters.add(ChapterInfo(index, title, href, level))
                }
            }
            
            // 递归处理子 navPoint
            val childNavPoints = navPoint.getElementsByTagName("navPoint")
            if (childNavPoints.length > 0) {
                parseNavPoints(childNavPoints, hrefToIndex, chapters, level + 1)
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
    private fun parseHtmlToElements(html: String, baseDir: String): List<ContentElement> {
        val elements = mutableListOf<ContentElement>()
        
        try {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            
            body.children().forEach { element ->
                parseElement(element, elements, baseDir)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse HTML to elements", e)
        }
        
        return elements
    }
    
    /**
     * 递归解析 HTML 元素
     */
    private fun parseElement(element: Element, elements: MutableList<ContentElement>, baseDir: String) {
        when (element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toIntOrNull() ?: 1
                elements.add(ContentElement.Heading(level, element.text()))
            }
            "p" -> {
                val spans = parseInlineElements(element)
                if (spans.isNotEmpty()) {
                    elements.add(ContentElement.Paragraph(spans))
                }
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                if (src.isNotEmpty()) {
                    elements.add(ContentElement.Image(src, alt))
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
                // 递归处理容器元素
                element.children().forEach { child ->
                    parseElement(child, elements, baseDir)
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
}
