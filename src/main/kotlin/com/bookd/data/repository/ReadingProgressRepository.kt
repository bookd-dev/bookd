package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.Books
import com.bookd.data.entity.ReadingProgress
import com.bookd.domain.model.ReadingProgressResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

class ReadingProgressRepository {
    
    fun findByUserAndBook(userId: Int, bookId: Int): ReadingProgressResponse? = transaction {
        ReadingProgress.selectAll()
            .where { (ReadingProgress.userId eq userId) and (ReadingProgress.bookId eq bookId) }
            .map { toResponse(it) }
            .singleOrNull()
    }
    
    fun findByUser(userId: Int, limit: Int = 50, offset: Long = 0): List<ReadingProgressResponse> = transaction {
        ReadingProgress.selectAll()
            .where { ReadingProgress.userId eq userId }
            .orderBy(ReadingProgress.lastReadAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { toResponse(it) }
    }
    
    fun upsert(
        userId: Int,
        bookId: Int,
        progress: Double,
        currentPage: Int?,
        totalPages: Int?,
        cfiLocation: String?,
        chapterId: String?,
        deviceId: String?
    ): ReadingProgressResponse = transaction {
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
                if (chapterId != null) it[ReadingProgress.chapterId] = chapterId
                if (deviceId != null) it[ReadingProgress.deviceId] = deviceId
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
                it[ReadingProgress.chapterId] = chapterId
                it[ReadingProgress.deviceId] = deviceId
                it[lastReadAt] = now
            }
        }
        
        findByUserAndBook(userId, bookId)!!
    }
    
    fun deleteByUserAndBook(userId: Int, bookId: Int): Int = transaction {
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
    
    fun getReadingHistory(userId: Int, limit: Int = 20): List<ReadingHistoryItem> = transaction {
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
    
    private fun toResponse(row: ResultRow) = ReadingProgressResponse(
        id = row[ReadingProgress.id].value,
        bookId = row[ReadingProgress.bookId].value,
        progress = row[ReadingProgress.progress].toDouble(),
        currentPage = row[ReadingProgress.currentPage],
        totalPages = row[ReadingProgress.totalPages],
        cfiLocation = row[ReadingProgress.cfiLocation],
        chapterId = row[ReadingProgress.chapterId],
        deviceId = row[ReadingProgress.deviceId],
        lastReadAt = row[ReadingProgress.lastReadAt]
    )
}
