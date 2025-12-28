package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object ReadingProgress : IntIdTable("reading_progress") {
    val userId = reference("user_id", Users)
    val bookId = reference("book_id", Books)
    val progress = decimal("progress", 5, 2).default(java.math.BigDecimal.ZERO)
    val currentPage = integer("current_page").default(0)
    val lastReadAt = datetime("last_read_at")
    
    init {
        uniqueIndex(userId, bookId)
    }
}
