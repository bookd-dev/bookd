package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object DocumentContents : IntIdTable("document_contents") {
    val documentId = reference("document_id", BookDocuments, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val content = text("content") // JSON 序列化的 List<ContentElement>
}
