package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * 书籍文档表
 * 存储 EPUB spine 中的所有文档（完整阅读顺序）
 * inToc 标记是否在目录中显示
 */
object BookDocuments : IntIdTable("book_documents") {
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val index = integer("index") // spine 顺序索引
    val href = varchar("href", 500).nullable() // 文档路径
    val inToc = bool("in_toc").default(false) // 是否在目录中显示
    val title = varchar("title", 500).nullable() // 目录标题（仅 inToc=true 时有值）
    val level = integer("level").default(0) // 目录层级（仅 inToc=true 时有意义）
    val wordCount = integer("word_count").default(0) // 字数统计
    val imageCount = integer("image_count").default(0) // 图片数量
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    
    init {
        uniqueIndex(bookId, index)
        index(false, bookId, inToc, index)
    }
}
