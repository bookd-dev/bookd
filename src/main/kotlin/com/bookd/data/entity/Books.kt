package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.ReferenceOption

object Books : IntIdTable("books") {
    val title = varchar("title", 500)  // 255 -> 500
    val author = varchar("author", 500).nullable()  // 255 -> 500
    val format = varchar("format", 20)  // 10 -> 20
    val filePath = varchar("file_path", 1000).uniqueIndex()  // 500 -> 1000
    val coverPath = varchar("cover_path", 1000).nullable()  // 500 -> 1000
    val coverWidth = integer("cover_width").nullable() // 封面宽度
    val coverHeight = integer("cover_height").nullable() // 封面高度
    val fileSize = long("file_size")
    val isbn = varchar("isbn", 100).nullable()  // 50 -> 100
    val publisher = varchar("publisher", 500).nullable()  // 255 -> 500
    val publishDate = varchar("publish_date", 100).nullable()  // 50 -> 100
    val description = text("description").nullable()
    val sourceId = reference("source_id", BookSources, onDelete = ReferenceOption.CASCADE).nullable()
    
    // 章节解析相关字段
    val chaptersParsed = bool("chapters_parsed").default(false)
    val chaptersCount = integer("chapters_count").default(0)
    val lastParsedAt = datetime("last_parsed_at").nullable()
    val parseStatus = varchar("parse_status", 50).nullable() // 20 -> 50
    val parseProgress = integer("parse_progress").default(0) // 0-100
    
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
