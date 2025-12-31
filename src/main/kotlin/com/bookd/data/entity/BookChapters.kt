package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object BookChapters : IntIdTable("book_chapters") {
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val index = integer("index") // 章节顺序索引
    val title = varchar("title", 500).nullable() // 章节标题
    val level = integer("level").default(0) // 目录层级 (0=顶级, 1=二级...)
    val wordCount = integer("word_count").default(0) // 字数统计
    val imageCount = integer("image_count").default(0) // 图片数量
    val href = varchar("href", 500).nullable() // EPUB 原始 href
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    
    init {
        uniqueIndex(bookId, index)
    }
}
