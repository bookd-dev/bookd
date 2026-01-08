package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class BookChapter(
    val id: Int,
    val bookId: Int,
    val index: Int,
    val title: String?,
    val level: Int = 0,
    val wordCount: Int = 0,
    val imageCount: Int = 0,
    val href: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

@Serializable
data class BookManifest(
    val id: Int,
    val title: String,
    val author: String?,
    val format: String,
    val totalChapters: Int,
    val toc: List<TocItem>,
    val spine: List<Int>,
    val metadata: BookMetadata?
)

@Serializable
data class TocItem(
    val index: Int,
    val title: String,
    val level: Int = 0,
    val wordCount: Int = 0,
    val imageCount: Int = 0,
    val children: List<TocItem> = emptyList()
)

@Serializable
data class BookMetadata(
    val publisher: String? = null,
    val language: String? = null,
    val publishDate: String? = null,
    val description: String? = null,
    val isbn: String? = null
)

@Serializable
data class ChapterContent(
    val index: Int,
    val title: String?,
    val elements: List<ContentElement>,
    val prevIndex: Int?,
    val nextIndex: Int?
)

@Serializable
sealed class ContentElement {
    
    @Serializable
    data class Paragraph(
        val spans: List<TextSpan>
    ) : ContentElement()
    
    @Serializable
    data class Heading(
        val level: Int,
        val text: String
    ) : ContentElement()
    
    @Serializable
    data class Image(
        val src: String,
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null
    ) : ContentElement()
    
    @Serializable
    data class Quote(
        val spans: List<TextSpan>
    ) : ContentElement()
    
    @Serializable
    data class Code(
        val text: String,
        val language: String? = null
    ) : ContentElement()
    
    @Serializable
    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItem>
    ) : ContentElement()
    
    @Serializable
    data object Divider : ContentElement()
    
    @Serializable
    data class Footnote(
        val id: String,
        val spans: List<TextSpan>
    ) : ContentElement()
}

@Serializable
data class TextSpan(
    val text: String,
    val styles: List<TextStyle> = emptyList(),
    val link: String? = null,
    val footnoteId: String? = null,  // 脚注引用 ID
    val footnoteImage: String? = null  // 脚注原始图片路径
)

@Serializable
enum class TextStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    CODE
}

@Serializable
data class ListItem(
    val spans: List<TextSpan>
)
