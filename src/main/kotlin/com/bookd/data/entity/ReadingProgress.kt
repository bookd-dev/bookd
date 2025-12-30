package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object ReadingProgress : IntIdTable("reading_progress") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val progress = decimal("progress", 5, 2).default(java.math.BigDecimal.ZERO)
    val currentPage = integer("current_page").default(0)
    val totalPages = integer("total_pages").nullable()
    val cfiLocation = varchar("cfi_location", 500).nullable()
    val chapterId = varchar("chapter_id", 100).nullable()
    val deviceId = varchar("device_id", 100).nullable()
    val lastReadAt = datetime("last_read_at")
    
    init {
        uniqueIndex(userId, bookId)
    }
}
