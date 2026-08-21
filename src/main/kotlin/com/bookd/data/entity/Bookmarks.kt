package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

object Bookmarks : IntIdTable("bookmarks") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val positionType = varchar("position_type", 20)  // cfi | page | percentage
    val positionValue = varchar("position_value", 500)
    val documentId = varchar("document_id", 100).nullable()
    val chapterIndex = integer("chapter_index").nullable()
    val anchorId = varchar("anchor_id", 500).nullable()
    val paragraphIndex = integer("paragraph_index").nullable()
    val scrollOffset = integer("scroll_offset").nullable()
    val title = varchar("title", 255).nullable()
    val note = text("note").nullable()
    val color = varchar("color", 20).default("#FFD700")
    val createdAt = datetime("created_at")
    
    init {
        uniqueIndex(userId, bookId, positionValue)
        index(false, userId, bookId, createdAt)
        index(false, userId, createdAt)
    }
}
