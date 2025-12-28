package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object BookSources : IntIdTable("book_sources") {
    val name = varchar("name", 100)
    val path = varchar("path", 500)
    val enabled = bool("enabled").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
