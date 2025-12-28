package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.ReferenceOption

object Books : IntIdTable("books") {
    val title = varchar("title", 255)
    val author = varchar("author", 255).nullable()
    val format = varchar("format", 10)
    val filePath = varchar("file_path", 500).uniqueIndex()
    val coverPath = varchar("cover_path", 500).nullable()
    val fileSize = long("file_size")
    val isbn = varchar("isbn", 20).nullable()
    val publisher = varchar("publisher", 255).nullable()
    val publishDate = varchar("publish_date", 20).nullable()
    val description = text("description").nullable()
    val sourceId = reference("source_id", BookSources, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
