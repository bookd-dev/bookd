package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChapterContents : IntIdTable("chapter_contents") {
    val documentId = reference("document_id", BookDocuments, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val content = text("content") // JSON 序列化的 List<ContentElement>
}
