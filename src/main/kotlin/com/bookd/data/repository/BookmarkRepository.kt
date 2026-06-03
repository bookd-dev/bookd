package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.Bookmarks
import com.bookd.domain.model.BookmarkResponse
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq

class BookmarkRepository {
    
    suspend fun findByUserAndBook(userId: Int, bookId: Int): List<BookmarkResponse> = dbQuery {
        Bookmarks.selectAll()
            .where { (Bookmarks.userId eq userId) and (Bookmarks.bookId eq bookId) }
            .orderBy(Bookmarks.createdAt, SortOrder.DESC)
            .map { toResponse(it) }
    }
    
    suspend fun findByUser(userId: Int, limit: Int = 100, offset: Long = 0): List<BookmarkResponse> = dbQuery {
        Bookmarks.selectAll()
            .where { Bookmarks.userId eq userId }
            .orderBy(Bookmarks.createdAt, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { toResponse(it) }
    }
    
    suspend fun findById(id: Int): BookmarkResponse? = dbQuery {
        findByIdInCurrentTransaction(id)
    }
    
    suspend fun findByIdAndUser(id: Int, userId: Int): BookmarkResponse? = dbQuery {
        findByIdAndUserInCurrentTransaction(id, userId)
    }
    
    suspend fun create(
        userId: Int,
        bookId: Int,
        positionType: String,
        positionValue: String,
        documentId: String?,
        chapterIndex: Int?,
        anchorId: String?,
        paragraphIndex: Int?,
        scrollOffset: Int?,
        title: String?,
        note: String?,
        color: String
    ): BookmarkResponse = dbQuery {
        val now = TimeProvider.now()
        
        val id = Bookmarks.insert {
            it[Bookmarks.userId] = userId
            it[Bookmarks.bookId] = bookId
            it[Bookmarks.positionType] = positionType
            it[Bookmarks.positionValue] = positionValue
            it[Bookmarks.documentId] = documentId
            it[Bookmarks.chapterIndex] = chapterIndex
            it[Bookmarks.anchorId] = anchorId
            it[Bookmarks.paragraphIndex] = paragraphIndex
            it[Bookmarks.scrollOffset] = scrollOffset
            it[Bookmarks.title] = title
            it[Bookmarks.note] = note
            it[Bookmarks.color] = color
            it[createdAt] = now
        }[Bookmarks.id]
        
        findByIdInCurrentTransaction(id.value)!!
    }
    
    suspend fun update(
        id: Int,
        userId: Int,
        title: String?,
        note: String?,
        color: String?
    ): BookmarkResponse? = dbQuery {
        val existing = findByIdAndUserInCurrentTransaction(id, userId) ?: return@dbQuery null
        
        Bookmarks.update({ Bookmarks.id eq id }) {
            if (title != null) it[Bookmarks.title] = title
            if (note != null) it[Bookmarks.note] = note
            if (color != null) it[Bookmarks.color] = color
        }
        
        findByIdInCurrentTransaction(id)
    }
    
    suspend fun delete(id: Int, userId: Int): Boolean = dbQuery {
        val deleted = Bookmarks.deleteWhere { 
            (Bookmarks.id eq id) and (Bookmarks.userId eq userId) 
        }
        deleted > 0
    }

    private fun findByIdInCurrentTransaction(id: Int): BookmarkResponse? {
        return Bookmarks.selectAll()
            .where { Bookmarks.id eq id }
            .map { toResponse(it) }
            .singleOrNull()
    }

    private fun findByIdAndUserInCurrentTransaction(id: Int, userId: Int): BookmarkResponse? {
        return Bookmarks.selectAll()
            .where { (Bookmarks.id eq id) and (Bookmarks.userId eq userId) }
            .map { toResponse(it) }
            .singleOrNull()
    }
    
    private fun toResponse(row: ResultRow) = BookmarkResponse(
        id = row[Bookmarks.id].value,
        bookId = row[Bookmarks.bookId].value,
        positionType = row[Bookmarks.positionType],
        positionValue = row[Bookmarks.positionValue],
        documentId = row[Bookmarks.documentId],
        chapterIndex = row[Bookmarks.chapterIndex] ?: parseLegacyChapterIndex(row[Bookmarks.positionValue]),
        anchorId = row[Bookmarks.anchorId],
        paragraphIndex = row[Bookmarks.paragraphIndex] ?: parseLegacyParagraphIndex(row[Bookmarks.positionValue]),
        scrollOffset = row[Bookmarks.scrollOffset],
        title = row[Bookmarks.title],
        note = row[Bookmarks.note],
        color = row[Bookmarks.color],
        createdAt = row[Bookmarks.createdAt]
    )

    private fun parseLegacyChapterIndex(positionValue: String): Int? {
        val parts = positionValue.split(':')
        return when (parts.firstOrNull()) {
            "anchor", "chapter" -> parts.getOrNull(1)?.toIntOrNull()
            else -> null
        }
    }

    private fun parseLegacyParagraphIndex(positionValue: String): Int? {
        val parts = positionValue.split(':')
        return when (parts.firstOrNull()) {
            "anchor" -> parts.getOrNull(3)?.toIntOrNull()
            "chapter" -> parts.getOrNull(2)?.toIntOrNull()
            else -> null
        }
    }
}
