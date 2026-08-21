package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime
import java.math.BigDecimal

object ReadingProgress : IntIdTable("reading_progress") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val progress = decimal("progress", 5, 2).default(BigDecimal.ZERO)
    val currentPage = integer("current_page").default(0)
    val totalPages = integer("total_pages").nullable()
    val cfiLocation = varchar("cfi_location", 500).nullable()
    val documentId = varchar("document_id", 100).nullable()
    val deviceId = varchar("device_id", 100).nullable()
    val lastReadAt = datetime("last_read_at")
    val anchorId = varchar("anchor_id", 500).nullable()
    val paragraphIndex = integer("paragraph_index").nullable()
    val scrollOffset = integer("scroll_offset").nullable()
    
    // 章节详细进度
    val chapterPageIndex = integer("chapter_page_index").nullable()       // 翻页模式：当前页索引
    val chapterTotalPages = integer("chapter_total_pages").nullable()     // 翻页模式：章节总页数
    val chapterScrollPercent = decimal("chapter_scroll_percent", 5, 4).nullable() // 滚动模式：滚动百分比
    
    init {
        uniqueIndex(userId, bookId)
        index(false, userId, lastReadAt)
    }
}
