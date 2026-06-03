package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 书籍文档（spine 项）
 * 代表 EPUB spine 中的一个文档
 */
@Serializable
data class BookDocument(
    val id: Int,
    val bookId: Int,
    val index: Int,
    val href: String? = null,
    val inToc: Boolean = false,
    val title: String? = null,
    val level: Int = 0,
    val wordCount: Int = 0,
    val imageCount: Int = 0,
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TocItem(
    val index: Int,
    val title: String,
    val level: Int = 0,
    val wordCount: Int = 0,
    val imageCount: Int = 0,
    val children: List<TocItem> = emptyList(),
    @EncodeDefault val readStatus: String = "unread",  // "unread" | "reading" | "read"
    @EncodeDefault val readProgress: Double = 0.0      // 0.0-1.0，章节阅读进度
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
    abstract val anchorId: String?
    
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val spans: List<TextSpan>,
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,
        val text: String,
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("image")
    data class Image(
        val src: String,
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val aspectRatio: Double? = null,  // 宽高比 (width / height)
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("quote")
    data class Quote(
        val spans: List<TextSpan>,
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("code")
    data class Code(
        val text: String,
        val language: String? = null,
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("listBlock")
    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItem>,
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("divider")
    data class Divider(
        override val anchorId: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("footnote")
    data class Footnote(
        val footnoteId: String,
        val footnoteImage: String? = null,  // 脚注图片 URL
        val footnoteSpan: TextSpan? = null,  // 脚注图片对应的文本（如果有）
        val width: Int? = null,  // 图片宽度
        val height: Int? = null,  // 图片高度
        val aspectRatio: Double? = null,  // 宽高比 (width / height)
        val contentSpans: List<TextSpan>,  // 脚注内容文本
        override val anchorId: String? = null
    ) : ContentElement()
}

@Serializable
data class TextSpan(
    val text: String,
    val styles: List<TextStyle> = emptyList(),
    val link: String? = null,
    val footnoteId: String? = null  // 脚注引用 ID（引用对应的 Footnote）
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
