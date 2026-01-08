package com.bookd.domain.service.parser.epub

/**
 * EPUB 路径处理工具类
 */
object EpubPathUtils {
    
    /**
     * 正规化图片路径（相对于章节位置）
     * 将相对路径如 "../Images/xxx.jpg" 转换为 "Images/xxx.jpg"
     */
    fun normalizeImagePath(src: String, chapterHref: String): String {
        // 如果是绝对路径或带协议，直接返回
        if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("/")) {
            return src
        }
        
        // 获取章节所在目录
        val chapterDir = if (chapterHref.contains("/")) {
            chapterHref.substringBeforeLast("/")
        } else {
            ""
        }
        
        // 解析相对路径
        val parts = mutableListOf<String>()
        if (chapterDir.isNotEmpty()) {
            parts.addAll(chapterDir.split("/"))
        }
        
        src.split("/").forEach { part ->
            when (part) {
                "." -> {} // 当前目录，跳过
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1) // 父目录
                "" -> {} // 空部分，跳过
                else -> parts.add(part)
            }
        }
        
        return parts.joinToString("/")
    }
    
    /**
     * 解析完整路径（基于 OPF 目录）
     */
    fun resolveFullPath(baseDir: String, href: String): String {
        return if (baseDir.isEmpty()) href else "$baseDir/$href"
    }
    
    /**
     * 获取相对路径（去除基础目录前缀）
     */
    fun getRelativePath(fullPath: String, baseDir: String): String {
        return if (baseDir.isNotEmpty() && fullPath.startsWith(baseDir)) {
            fullPath.removePrefix("$baseDir/")
        } else {
            fullPath
        }
    }
}
