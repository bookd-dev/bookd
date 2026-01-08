package com.bookd.domain.service.parser.epub

import org.slf4j.LoggerFactory
import java.util.zip.ZipFile

/**
 * EPUB 资源提取器
 * 负责从 EPUB 包中提取图片等资源文件
 */
class EpubResourceExtractor {
    
    private val logger = LoggerFactory.getLogger(EpubResourceExtractor::class.java)
    
    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "svg", "webp", "bmp")
    }
    
    /**
     * 提取所有图片资源
     * @param zipFile EPUB 压缩文件
     * @param baseDir OPF 文件所在目录
     * @return 资源路径到字节数组的映射
     */
    fun extractResources(zipFile: ZipFile, baseDir: String): Map<String, ByteArray> {
        val resources = mutableMapOf<String, ByteArray>()
        
        zipFile.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry -> isImageFile(entry.name) }
            .forEach { entry ->
                try {
                    val bytes = zipFile.getInputStream(entry).readBytes()
                    val relativePath = EpubPathUtils.getRelativePath(entry.name, baseDir)
                    resources[relativePath] = bytes
                } catch (e: Exception) {
                    logger.warn("Failed to extract resource: ${entry.name}", e)
                }
            }
        
        logger.info("Extracted ${resources.size} resources from EPUB")
        return resources
    }
    
    /**
     * 判断文件是否为图片
     */
    private fun isImageFile(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }
}
