package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.Tags
import com.bookd.data.entity.BookTags
import com.bookd.domain.model.Tag
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TagRepository {
    
    fun findOrCreateTag(tagName: String): Tag = transaction {
        // Try to find existing tag
        Tags.selectAll().where { Tags.name eq tagName }
            .map { rowToTag(it) }
            .firstOrNull()
            ?: run {
                // Create new tag with conflict handling
                try {
                    val now = TimeProvider.now()
                    val id = Tags.insertAndGetId {
                        it[name] = tagName
                        it[createdAt] = now
                    }
                    Tag(id.value, tagName, now)
                } catch (e: Exception) {
                    // If insert fails due to unique constraint (race condition),
                    // try to find the tag again
                    Tags.selectAll().where { Tags.name eq tagName }
                        .map { rowToTag(it) }
                        .firstOrNull()
                        ?: throw e // Re-throw if still not found
                }
            }
    }
    
    fun getAllTags(): List<Tag> = transaction {
        Tags.selectAll()
            .orderBy(Tags.name to SortOrder.ASC)
            .map { rowToTag(it) }
    }
    
    fun getTagsByBookId(bookId: Int): List<Tag> = transaction {
        (Tags innerJoin BookTags)
            .selectAll()
            .where { BookTags.bookId eq bookId }
            .orderBy(Tags.name to SortOrder.ASC)
            .map { rowToTag(it) }
    }
    
    fun getBookIdsByTagId(tagId: Int): List<Int> = transaction {
        BookTags.selectAll()
            .where { BookTags.tagId eq tagId }
            .map { it[BookTags.bookId].value }
    }
    
    fun addTagToBook(bookId: Int, tagId: Int): Boolean = transaction {
        try {
            val now = TimeProvider.now()
            BookTags.insert {
                it[BookTags.bookId] = bookId
                it[BookTags.tagId] = tagId
                it[createdAt] = now
            }
            true
        } catch (e: Exception) {
            // Tag already exists for this book
            false
        }
    }
    
    fun removeTagFromBook(bookId: Int, tagId: Int): Boolean = transaction {
        val deleted = BookTags.deleteWhere {
            (BookTags.bookId eq bookId) and (BookTags.tagId eq tagId)
        }
        deleted > 0
    }
    
    fun clearBookTags(bookId: Int): Int = transaction {
        BookTags.deleteWhere { BookTags.bookId eq bookId }
    }
    
    fun deleteTag(tagId: Int): Boolean = transaction {
        // BookTags will be cascade deleted
        val deleted = Tags.deleteWhere { Tags.id eq tagId }
        deleted > 0
    }
    
    fun getTagStats(): Map<Int, Int> = transaction {
        BookTags
            .select(BookTags.tagId, BookTags.tagId.count())
            .groupBy(BookTags.tagId)
            .associate { it[BookTags.tagId].value to it[BookTags.tagId.count()].toInt() }
    }
    
    private fun rowToTag(row: ResultRow): Tag {
        return Tag(
            id = row[Tags.id].value,
            name = row[Tags.name],
            createdAt = row[Tags.createdAt]
        )
    }
}
