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
     * 逐个提取图片资源
     * @param zipFile EPUB 压缩文件
     * @param baseDir OPF 文件所在目录
     */
    fun forEachResource(
        zipFile: ZipFile,
        baseDir: String,
        consumer: (path: String, bytes: ByteArray) -> Unit
    ) {
        var resourceCount = 0
        var totalBytes = 0L
        
        zipFile.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry -> isImageFile(entry.name) }
            .forEach { entry ->
                val bytes = EpubArchiveSafety.readBytes(zipFile, entry, EpubArchiveSafety.MAX_IMAGE_BYTES)
                totalBytes += bytes.size
                if (totalBytes > EpubArchiveSafety.MAX_TOTAL_IMAGE_BYTES) {
                    throw EpubArchiveException("EPUB image resources exceed total limit")
                }
                val relativePath = EpubPathUtils.getRelativePath(entry.name, baseDir)
                consumer(relativePath, bytes)
                resourceCount++
            }
        
        logger.info("Extracted $resourceCount resources from EPUB")
    }
    
    /**
     * 判断文件是否为图片
     */
    private fun isImageFile(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }
}
