package com.bookd.data.repository

import com.bookd.data.entity.Bookmarks
import com.bookd.domain.model.BookmarkResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookmarkRepository {
    
    fun findByUserAndBook(userId: Int, bookId: Int): List<BookmarkResponse> = transaction {
        Bookmarks.selectAll()
            .where { (Bookmarks.userId eq userId) and (Bookmarks.bookId eq bookId) }
            .orderBy(Bookmarks.createdAt, SortOrder.DESC)
            .map { toResponse(it) }
    }
    
    fun findByUser(userId: Int, limit: Int = 100, offset: Long = 0): List<BookmarkResponse> = transaction {
        Bookmarks.selectAll()
            .where { Bookmarks.userId eq userId }
            .orderBy(Bookmarks.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { toResponse(it) }
    }
    
    fun findById(id: Int): BookmarkResponse? = transaction {
        Bookmarks.selectAll()
            .where { Bookmarks.id eq id }
            .map { toResponse(it) }
            .singleOrNull()
    }
    
    fun findByIdAndUser(id: Int, userId: Int): BookmarkResponse? = transaction {
        Bookmarks.selectAll()
            .where { (Bookmarks.id eq id) and (Bookmarks.userId eq userId) }
            .map { toResponse(it) }
            .singleOrNull()
    }
    
    fun create(
        userId: Int,
        bookId: Int,
        positionType: String,
        positionValue: String,
        chapterId: String?,
        title: String?,
        note: String?,
        color: String
    ): BookmarkResponse = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        
        val id = Bookmarks.insert {
            it[Bookmarks.userId] = userId
            it[Bookmarks.bookId] = bookId
            it[Bookmarks.positionType] = positionType
            it[Bookmarks.positionValue] = positionValue
            it[Bookmarks.chapterId] = chapterId
            it[Bookmarks.title] = title
            it[Bookmarks.note] = note
            it[Bookmarks.color] = color
            it[createdAt] = now
        }[Bookmarks.id]
        
        findById(id.value)!!
    }
    
    fun update(
        id: Int,
        userId: Int,
        title: String?,
        note: String?,
        color: String?
    ): BookmarkResponse? = transaction {
        val existing = findByIdAndUser(id, userId) ?: return@transaction null
        
        Bookmarks.update({ Bookmarks.id eq id }) {
            if (title != null) it[Bookmarks.title] = title
            if (note != null) it[Bookmarks.note] = note
            if (color != null) it[Bookmarks.color] = color
        }
        
        findById(id)
    }
    
    fun delete(id: Int, userId: Int): Boolean = transaction {
        val deleted = Bookmarks.deleteWhere { 
            (Bookmarks.id eq id) and (Bookmarks.userId eq userId) 
        }
        deleted > 0
    }
    
    private fun toResponse(row: ResultRow) = BookmarkResponse(
        id = row[Bookmarks.id].value,
        bookId = row[Bookmarks.bookId].value,
        positionType = row[Bookmarks.positionType],
        positionValue = row[Bookmarks.positionValue],
        chapterId = row[Bookmarks.chapterId],
        title = row[Bookmarks.title],
        note = row[Bookmarks.note],
        color = row[Bookmarks.color],
        createdAt = row[Bookmarks.createdAt]
    )
}
