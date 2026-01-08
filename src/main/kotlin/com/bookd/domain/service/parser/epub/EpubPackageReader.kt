package com.bookd.domain.service.parser.epub

import org.slf4j.LoggerFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 包结构读取器
 * 负责解析 container.xml 和 content.opf 获取 EPUB 基本结构
 */
class EpubPackageReader {
    
    private val logger = LoggerFactory.getLogger(EpubPackageReader::class.java)
    
    data class PackageInfo(
        val opfPath: String,
        val baseDir: String,
        val spineItems: List<String>,
        val tocHref: String?,      // NCX 目录 (EPUB 2)
        val navHref: String?       // Nav 目录 (EPUB 3)
    )
    
    /**
     * 读取 EPUB 包信息
     * @param zipFile EPUB 压缩文件
     * @return 包信息，如果解析失败返回 null
     */
    fun readPackage(zipFile: ZipFile): PackageInfo? {
        // 1. 找到 content.opf 路径
        val opfPath = findOpfPath(zipFile) ?: "EPUB/content.opf"
        logger.info("Found OPF path: $opfPath")
        
        // 2. 解析 content.opf
        val opfEntry = zipFile.getEntry(opfPath) ?: return null
        val opfBaseDir = opfPath.substringBeforeLast("/", "")
        
        val opfResult = parseOpf(zipFile, opfEntry)
        logger.info("Found ${opfResult.spineItems.size} spine items, ncx=${opfResult.tocHref != null}, nav=${opfResult.navHref != null}")
        
        return PackageInfo(
            opfPath = opfPath,
            baseDir = opfBaseDir,
            spineItems = opfResult.spineItems,
            tocHref = opfResult.tocHref,
            navHref = opfResult.navHref
        )
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
    
    private data class OpfResult(
        val spineItems: List<String>,
        val tocHref: String?,
        val navHref: String?
    )
    
    /**
     * 解析 OPF 文件获取 spine、toc.ncx 和 nav.xhtml 路径
     */
    private fun parseOpf(zipFile: ZipFile, opfEntry: ZipEntry): OpfResult {
        val spineItems = mutableListOf<String>()
        var tocHref: String? = null
        var navHref: String? = null
        
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
                        val properties = node.attributes.getNamedItem("properties")?.nodeValue
                        
                        if (id != null && href != null) {
                            manifestMap[id] = href
                            
                            // 查找 NCX 目录 (EPUB 2)
                            if (mediaType == "application/x-dtbncx+xml" || id == "ncx") {
                                tocHref = href
                            }
                            
                            // 查找 Nav 目录 (EPUB 3)
                            // properties 属性包含 "nav" 表示这是导航文档
                            if (properties != null && properties.split(" ").contains("nav")) {
                                navHref = href
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
        
        return OpfResult(spineItems, tocHref, navHref)
    }
}
