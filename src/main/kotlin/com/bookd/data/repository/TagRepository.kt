package com.bookd.data.repository

import com.bookd.data.entity.BookTags
import com.bookd.data.entity.Tags
import com.bookd.domain.model.Tag
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class TagRepository {

    fun findOrCreateTag(tagName: String): Tag = transaction {
        findOrCreateTagInCurrentTransaction(tagName)
    }

    suspend fun findOrCreateTagAsync(tagName: String): Tag = dbQuery {
        findOrCreateTagInCurrentTransaction(tagName)
    }

    fun findOrCreateTags(tagNames: Collection<String>): Map<String, Tag> = transaction {
        findOrCreateTagsInCurrentTransaction(tagNames)
    }

    suspend fun findOrCreateTagsAsync(tagNames: Collection<String>): Map<String, Tag> = dbQuery {
        findOrCreateTagsInCurrentTransaction(tagNames)
    }

    fun getAllTags(): List<Tag> = transaction {
        getAllTagsInCurrentTransaction()
    }

    suspend fun getAllTagsAsync(): List<Tag> = dbQuery {
        getAllTagsInCurrentTransaction()
    }

    fun getTagsByBookId(bookId: Int): List<Tag> = transaction {
        getTagsByBookIdInCurrentTransaction(bookId)
    }

    suspend fun getTagsByBookIdAsync(bookId: Int): List<Tag> = dbQuery {
        getTagsByBookIdInCurrentTransaction(bookId)
    }

    fun getBookIdsByTagId(tagId: Int): List<Int> = transaction {
        getBookIdsByTagIdInCurrentTransaction(tagId)
    }

    suspend fun getBookIdsByTagIdAsync(tagId: Int): List<Int> = dbQuery {
        getBookIdsByTagIdInCurrentTransaction(tagId)
    }

    fun addTagToBook(bookId: Int, tagId: Int): Boolean = transaction {
        addTagToBookInCurrentTransaction(bookId, tagId)
    }

    suspend fun addTagToBookAsync(bookId: Int, tagId: Int): Boolean = dbQuery {
        addTagToBookInCurrentTransaction(bookId, tagId)
    }

    fun addTagsToBook(bookId: Int, tagIds: Collection<Int>): Set<Int> = transaction {
        addTagsToBookInCurrentTransaction(bookId, tagIds)
    }

    suspend fun addTagsToBookAsync(bookId: Int, tagIds: Collection<Int>): Set<Int> = dbQuery {
        addTagsToBookInCurrentTransaction(bookId, tagIds)
    }

    fun addTagToBooks(bookIds: Collection<Int>, tagId: Int): Int = transaction {
        addTagToBooksInCurrentTransaction(bookIds, tagId)
    }

    suspend fun addTagToBooksAsync(bookIds: Collection<Int>, tagId: Int): Int = dbQuery {
        addTagToBooksInCurrentTransaction(bookIds, tagId)
    }

    fun removeTagFromBook(bookId: Int, tagId: Int): Boolean = transaction {
        removeTagFromBookInCurrentTransaction(bookId, tagId)
    }

    suspend fun removeTagFromBookAsync(bookId: Int, tagId: Int): Boolean = dbQuery {
        removeTagFromBookInCurrentTransaction(bookId, tagId)
    }

    fun clearBookTags(bookId: Int): Int = transaction {
        clearBookTagsInCurrentTransaction(bookId)
    }

    suspend fun clearBookTagsAsync(bookId: Int): Int = dbQuery {
        clearBookTagsInCurrentTransaction(bookId)
    }

    fun deleteTag(tagId: Int): Boolean = transaction {
        deleteTagInCurrentTransaction(tagId)
    }

    suspend fun deleteTagAsync(tagId: Int): Boolean = dbQuery {
        deleteTagInCurrentTransaction(tagId)
    }

    fun getTagStats(): Map<Int, Int> = transaction {
        getTagStatsInCurrentTransaction()
    }

    suspend fun getTagStatsAsync(): Map<Int, Int> = dbQuery {
        getTagStatsInCurrentTransaction()
    }

    private fun findOrCreateTagInCurrentTransaction(tagName: String): Tag {
        return Tags.selectAll().where { Tags.name eq tagName }
            .map { rowToTag(it) }
            .firstOrNull()
            ?: createTagWithConflictRetry(tagName)
    }

    private fun findOrCreateTagsInCurrentTransaction(tagNames: Collection<String>): Map<String, Tag> {
        val distinctNames = tagNames.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (distinctNames.isEmpty()) return emptyMap()

        val tagsByName = Tags.selectAll()
            .where { Tags.name inList distinctNames }
            .map { rowToTag(it) }
            .associateBy { it.name }
            .toMutableMap()

        distinctNames.filterNot { it in tagsByName }.forEach { tagName ->
            val tag = createTagWithConflictRetry(tagName)
            tagsByName[tag.name] = tag
        }

        return tagsByName
    }

    private fun createTagWithConflictRetry(tagName: String): Tag {
        return try {
            val now = TimeProvider.now()
            val id = Tags.insertAndGetId {
                it[name] = tagName
                it[createdAt] = now
            }
            Tag(id.value, tagName, now)
        } catch (e: Exception) {
            Tags.selectAll().where { Tags.name eq tagName }
                .map { rowToTag(it) }
                .firstOrNull()
                ?: throw e
        }
    }

    private fun getAllTagsInCurrentTransaction(): List<Tag> {
        return Tags.selectAll()
            .orderBy(Tags.name to SortOrder.ASC)
            .map { rowToTag(it) }
    }

    private fun getTagsByBookIdInCurrentTransaction(bookId: Int): List<Tag> {
        return (Tags innerJoin BookTags)
            .selectAll()
            .where { BookTags.bookId eq bookId }
            .orderBy(Tags.name to SortOrder.ASC)
            .map { rowToTag(it) }
    }

    private fun getBookIdsByTagIdInCurrentTransaction(tagId: Int): List<Int> {
        return BookTags.selectAll()
            .where { BookTags.tagId eq tagId }
            .map { it[BookTags.bookId].value }
    }

    private fun addTagToBookInCurrentTransaction(bookId: Int, tagId: Int): Boolean {
        return try {
            val now = TimeProvider.now()
            BookTags.insert {
                it[BookTags.bookId] = bookId
                it[BookTags.tagId] = tagId
                it[createdAt] = now
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addTagsToBookInCurrentTransaction(bookId: Int, tagIds: Collection<Int>): Set<Int> {
        val distinctTagIds = tagIds.distinct()
        if (distinctTagIds.isEmpty()) return emptySet()

        val existingTagIds = BookTags.select(BookTags.tagId)
            .where { (BookTags.bookId eq bookId) and (BookTags.tagId inList distinctTagIds) }
            .map { it[BookTags.tagId].value }
            .toSet()

        val addedTagIds = mutableSetOf<Int>()
        val now = TimeProvider.now()
        distinctTagIds.filterNot { it in existingTagIds }.forEach { tagId ->
            try {
                BookTags.insert {
                    it[BookTags.bookId] = bookId
                    it[BookTags.tagId] = tagId
                    it[createdAt] = now
                }
                addedTagIds.add(tagId)
            } catch (e: Exception) {
                // Existing association created concurrently; preserve idempotent behavior.
            }
        }

        return addedTagIds
    }

    private fun addTagToBooksInCurrentTransaction(bookIds: Collection<Int>, tagId: Int): Int {
        val distinctBookIds = bookIds.distinct()
        if (distinctBookIds.isEmpty()) return 0

        val existingBookIds = BookTags.select(BookTags.bookId)
            .where { (BookTags.bookId inList distinctBookIds) and (BookTags.tagId eq tagId) }
            .map { it[BookTags.bookId].value }
            .toSet()

        var added = 0
        val now = TimeProvider.now()
        distinctBookIds.filterNot { it in existingBookIds }.forEach { bookId ->
            try {
                BookTags.insert {
                    it[BookTags.bookId] = bookId
                    it[BookTags.tagId] = tagId
                    it[createdAt] = now
                }
                added++
            } catch (e: Exception) {
                // Existing association created concurrently; preserve idempotent behavior.
            }
        }

        return added
    }

    private fun removeTagFromBookInCurrentTransaction(bookId: Int, tagId: Int): Boolean {
        val deleted = BookTags.deleteWhere {
            (BookTags.bookId eq bookId) and (BookTags.tagId eq tagId)
        }
        return deleted > 0
    }

    private fun clearBookTagsInCurrentTransaction(bookId: Int): Int {
        return BookTags.deleteWhere { BookTags.bookId eq bookId }
    }

    private fun deleteTagInCurrentTransaction(tagId: Int): Boolean {
        val deleted = Tags.deleteWhere { Tags.id eq tagId }
        return deleted > 0
    }

    private fun getTagStatsInCurrentTransaction(): Map<Int, Int> {
        val tagCount = BookTags.tagId.count()
        return BookTags
            .select(BookTags.tagId, tagCount)
            .groupBy(BookTags.tagId)
            .associate { it[BookTags.tagId].value to it[tagCount].toInt() }
    }

    private fun rowToTag(row: ResultRow): Tag {
        return Tag(
            id = row[Tags.id].value,
            name = row[Tags.name],
            createdAt = row[Tags.createdAt]
        )
    }
}
