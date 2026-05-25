package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.ReferenceOption

object Books : IntIdTable("books") {
    val title = varchar("title", 255)
    val author = varchar("author", 255).nullable()
    val format = varchar("format", 10)
    val filePath = varchar("file_path", 500).uniqueIndex()
    val coverPath = varchar("cover_path", 500).nullable()
    val coverWidth = integer("cover_width").nullable() // 封面宽度
    val coverHeight = integer("cover_height").nullable() // 封面高度
    val fileSize = long("file_size")
    val isbn = varchar("isbn", 20).nullable()
    val publisher = varchar("publisher", 255).nullable()
    val publishDate = varchar("publish_date", 20).nullable()
    val description = text("description").nullable()
    val sourceId = reference("source_id", BookSources, onDelete = ReferenceOption.CASCADE).nullable()
    
    // 章节解析相关字段
    val chaptersParsed = bool("chapters_parsed").default(false)
    val chaptersCount = integer("chapters_count").default(0)
    val lastParsedAt = datetime("last_parsed_at").nullable()
    val parseStatus = varchar("parse_status", 20).nullable() // pending, parsing, completed, failed
    val parseProgress = integer("parse_progress").default(0) // 0-100

    // 内部统计字段：用于避免列表/详情接口每次聚合 book_documents
    val tocChapterCount = integer("toc_chapter_count").nullable()
    val totalWordCount = integer("total_word_count").nullable()
    val totalImageCount = integer("total_image_count").nullable()
    val statsUpdatedAt = datetime("stats_updated_at").nullable()
    
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        index(false, sourceId, title)
        index(false, parseStatus, chaptersParsed)
        index(false, statsUpdatedAt)
    }
}
