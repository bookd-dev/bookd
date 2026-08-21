package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.Books
import com.bookd.data.entity.ReadingProgress
import com.bookd.domain.model.ReadingProgressResponse
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import java.math.BigDecimal

class ReadingProgressRepository {
    
    suspend fun findByUserAndBook(userId: Int, bookId: Int): ReadingProgressResponse? = dbQuery {
        findByUserAndBookInCurrentTransaction(userId, bookId)
    }
    
    suspend fun findByUser(userId: Int, limit: Int = 50, offset: Long = 0): List<ReadingProgressResponse> = dbQuery {
        ReadingProgress.selectAll()
            .where { ReadingProgress.userId eq userId }
            .orderBy(ReadingProgress.lastReadAt, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { toResponse(it) }
    }
    
    /**
     * 批量查询多本书的阅读进度
     * 用于优化书架书籍列表的 N+1 查询问题
     * 
     * @param userId 用户ID
     * @param bookIds 书籍ID列表
     * @return Map<bookId, ReadingProgressResponse>
     */
    suspend fun findByUserAndBooks(userId: Int, bookIds: List<Int>): Map<Int, ReadingProgressResponse> = dbQuery {
        if (bookIds.isEmpty()) return@dbQuery emptyMap()
        
        ReadingProgress.selectAll()
            .where { (ReadingProgress.userId eq userId) and (ReadingProgress.bookId inList bookIds) }
            .associate { row ->
                row[ReadingProgress.bookId].value to toResponse(row)
            }
    }
    
    suspend fun upsert(
        userId: Int,
        bookId: Int,
        progress: Double,
        currentPage: Int?,
        totalPages: Int?,
        cfiLocation: String?,
        documentId: String?,
        deviceId: String?,
        anchorId: String? = null,
        paragraphIndex: Int? = null,
        scrollOffset: Int? = null,
        chapterPageIndex: Int? = null,
        chapterTotalPages: Int? = null,
        chapterScrollPercent: Double? = null
    ): ReadingProgressResponse = dbQuery {
        val now = TimeProvider.now()
        
        val existing = ReadingProgress.selectAll()
            .where { (ReadingProgress.userId eq userId) and (ReadingProgress.bookId eq bookId) }
            .singleOrNull()
        
        if (existing != null) {
            ReadingProgress.update({ (ReadingProgress.userId eq userId) and (ReadingProgress.bookId eq bookId) }) {
                it[ReadingProgress.progress] = BigDecimal.valueOf(progress)
                if (currentPage != null) it[ReadingProgress.currentPage] = currentPage
                if (totalPages != null) it[ReadingProgress.totalPages] = totalPages
                if (cfiLocation != null) it[ReadingProgress.cfiLocation] = cfiLocation
                if (documentId != null) it[ReadingProgress.documentId] = documentId
                if (deviceId != null) it[ReadingProgress.deviceId] = deviceId
                // PUT 表示完整阅读位置快照。可空位置字段也必须覆盖，避免切换章节后
                // 将上一章节的锚点、偏移或分页信息残留到新位置。
                it[ReadingProgress.anchorId] = anchorId
                it[ReadingProgress.paragraphIndex] = paragraphIndex
                it[ReadingProgress.scrollOffset] = scrollOffset
                it[ReadingProgress.chapterPageIndex] = chapterPageIndex
                it[ReadingProgress.chapterTotalPages] = chapterTotalPages
                it[ReadingProgress.chapterScrollPercent] = chapterScrollPercent?.let { value ->
                    BigDecimal.valueOf(value)
                }
                it[lastReadAt] = now
            }
        } else {
            ReadingProgress.insert {
                it[ReadingProgress.userId] = userId
                it[ReadingProgress.bookId] = bookId
                it[ReadingProgress.progress] = BigDecimal.valueOf(progress)
                it[ReadingProgress.currentPage] = currentPage ?: 0
                it[ReadingProgress.totalPages] = totalPages
                it[ReadingProgress.cfiLocation] = cfiLocation
                it[ReadingProgress.documentId] = documentId
                it[ReadingProgress.deviceId] = deviceId
                it[ReadingProgress.anchorId] = anchorId
                it[ReadingProgress.paragraphIndex] = paragraphIndex
                it[ReadingProgress.scrollOffset] = scrollOffset
                it[ReadingProgress.chapterPageIndex] = chapterPageIndex
                it[ReadingProgress.chapterTotalPages] = chapterTotalPages
                it[ReadingProgress.chapterScrollPercent] = chapterScrollPercent?.let { BigDecimal.valueOf(it) }
                it[lastReadAt] = now
            }
        }
        
        findByUserAndBookInCurrentTransaction(userId, bookId)!!
    }
    
    suspend fun deleteByUserAndBook(userId: Int, bookId: Int): Int = dbQuery {
        ReadingProgress.deleteWhere { 
            (ReadingProgress.userId eq userId) and (ReadingProgress.bookId eq bookId) 
        }
    }
    
    data class ReadingHistoryItem(
        val progress: ReadingProgressResponse,
        val bookTitle: String,
        val bookAuthor: String?,
        val bookCoverPath: String?,
        val bookFormat: String
    )
    
    suspend fun getReadingHistory(userId: Int, limit: Int = 20): List<ReadingHistoryItem> = dbQuery {
        (ReadingProgress innerJoin Books)
            .selectAll()
            .where { ReadingProgress.userId eq userId }
            .orderBy(ReadingProgress.lastReadAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                ReadingHistoryItem(
                    progress = toResponse(row),
                    bookTitle = row[Books.title],
                    bookAuthor = row[Books.author],
                    bookCoverPath = row[Books.coverPath],
                    bookFormat = row[Books.format]
                )
            }
    }

    private fun findByUserAndBookInCurrentTransaction(userId: Int, bookId: Int): ReadingProgressResponse? {
        return ReadingProgress.selectAll()
            .where { (ReadingProgress.userId eq userId) and (ReadingProgress.bookId eq bookId) }
            .map { toResponse(it) }
            .singleOrNull()
    }
    
    private fun toResponse(row: ResultRow) = ReadingProgressResponse(
        id = row[ReadingProgress.id].value,
        bookId = row[ReadingProgress.bookId].value,
        progress = row[ReadingProgress.progress].toDouble(),
        currentPage = row[ReadingProgress.currentPage],
        totalPages = row[ReadingProgress.totalPages],
        cfiLocation = row[ReadingProgress.cfiLocation],
        documentId = row[ReadingProgress.documentId],
        deviceId = row[ReadingProgress.deviceId],
        lastReadAt = row[ReadingProgress.lastReadAt],
        chapterIndex = row[ReadingProgress.currentPage],
        anchorId = row[ReadingProgress.anchorId],
        paragraphIndex = row[ReadingProgress.paragraphIndex],
        scrollOffset = row[ReadingProgress.scrollOffset],
        chapterPageIndex = row[ReadingProgress.chapterPageIndex],
        chapterTotalPages = row[ReadingProgress.chapterTotalPages],
        chapterScrollPercent = row[ReadingProgress.chapterScrollPercent]?.toDouble()
    )
}
