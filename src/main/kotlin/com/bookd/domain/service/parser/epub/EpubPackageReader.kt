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
        val tocHref: String?
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
        
        val (spineItems, tocHref) = parseOpf(zipFile, opfEntry)
        logger.info("Found ${spineItems.size} spine items")
        
        return PackageInfo(
            opfPath = opfPath,
            baseDir = opfBaseDir,
            spineItems = spineItems,
            tocHref = tocHref
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
}
