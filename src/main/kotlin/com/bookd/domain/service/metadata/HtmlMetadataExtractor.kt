package com.bookd.domain.service.metadata

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.File

/**
 * HTML 文章元数据提取器
 * 从 HTML meta 标签和结构中提取元数据
 */
class HtmlMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(HtmlMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        try {
            val html = file.readText()
            val doc = Jsoup.parse(html)
            
            // 提取标题 (优先级: og:title > meta[name=title] > title 标签 > 文件名)
            val title = doc.select("meta[property=og:title]").attr("content").ifEmpty {
                doc.select("meta[name=title]").attr("content").ifEmpty {
                    doc.title().ifEmpty {
                        file.nameWithoutExtension
                    }
                }
            }
            
            // 提取作者
            val author = doc.select("meta[name=author]").attr("content").ifEmpty {
                doc.select("meta[property=article:author]").attr("content").ifEmpty {
                    doc.select("meta[property=og:article:author]").attr("content")
                }
            }
            
            // 提取描述
            val description = doc.select("meta[name=description]").attr("content").ifEmpty {
                doc.select("meta[property=og:description]").attr("content")
            }
            
            // 提取发布者（如果有）
            val publisher = doc.select("meta[property=og:site_name]").attr("content").ifEmpty {
                doc.select("meta[name=publisher]").attr("content")
            }
            
            // 提取标签/关键词
            val tags = mutableListOf<String>()
            
            // 从 keywords meta 标签提取
            val keywords = doc.select("meta[name=keywords]").attr("content")
            if (keywords.isNotEmpty()) {
                keywords.split(',', ';', '/', '|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length <= 50 }
                    .forEach { tags.add(it) }
            }
            
            // 从 article:tag 提取 (Open Graph)
            doc.select("meta[property=article:tag]").forEach { element ->
                val tag = element.attr("content").trim()
                if (tag.isNotEmpty() && tag.length <= 50) {
                    tags.add(tag)
                }
            }
            
            // 从 og:article:tag 提取
            doc.select("meta[property=og:article:tag]").forEach { element ->
                val tag = element.attr("content").trim()
                if (tag.isNotEmpty() && tag.length <= 50) {
                    tags.add(tag)
                }
            }
            
            val distinctTags = tags.distinct()
            if (distinctTags.isNotEmpty()) {
                logger.debug("Extracted ${distinctTags.size} tags from HTML: ${distinctTags.joinToString(", ")}")
            }
            
            return BookMetadata(
                title = title.takeIf { it.isNotEmpty() },
                author = author.takeIf { it.isNotEmpty() },
                publisher = publisher.takeIf { it.isNotEmpty() },
                description = description.takeIf { it.isNotEmpty() },
                isbn = null,
                tags = distinctTags
            )
        } catch (e: Exception) {
            logger.error("Failed to extract HTML metadata: ${file.name}", e)
            return null
        }
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        // HTML 文章通常不包含可直接使用的封面图片
        // 图片可能是相对路径或网络 URL，需要额外处理
        // 这里返回 null，让系统自动生成文字封面
        logger.debug("HTML format does not support embedded cover extraction, will use generated cover")
        return null
    }
}
