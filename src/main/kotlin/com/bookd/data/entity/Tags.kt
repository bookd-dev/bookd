package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

object Tags : IntIdTable("tags") {
    val name = varchar("name", 100).uniqueIndex()
    val createdAt = datetime("created_at")
}

object BookTags : IntIdTable("book_tags") {
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val tagId = reference("tag_id", Tags, onDelete = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at")
    
    init {
        uniqueIndex(bookId, tagId)
        index(false, tagId)
    }
}
