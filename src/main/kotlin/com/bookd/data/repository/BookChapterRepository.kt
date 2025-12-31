package com.bookd.data.repository

import com.bookd.data.entity.BookChapters
import com.bookd.data.entity.ChapterContents
import com.bookd.data.entity.ChapterResources
import com.bookd.domain.model.BookChapter
import com.bookd.domain.model.ContentElement
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookChapterRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    fun findByBookId(bookId: Int): List<BookChapter> = transaction {
        BookChapters.selectAll()
            .where { BookChapters.bookId eq bookId }
            .orderBy(BookChapters.index to SortOrder.ASC)
            .map { toBookChapter(it) }
    }
    
    fun findByBookIdAndIndex(bookId: Int, index: Int): BookChapter? = transaction {
        BookChapters.selectAll()
            .where { (BookChapters.bookId eq bookId) and (BookChapters.index eq index) }
            .map { toBookChapter(it) }
            .singleOrNull()
    }
    
    fun countByBookId(bookId: Int): Int = transaction {
        BookChapters.selectAll()
            .where { BookChapters.bookId eq bookId }
            .count()
            .toInt()
    }
    
    fun create(
        bookId: Int,
        index: Int,
        title: String?,
        level: Int = 0,
        wordCount: Int = 0,
        imageCount: Int = 0,
        href: String? = null
    ): BookChapter = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val id = BookChapters.insert {
            it[BookChapters.bookId] = bookId
            it[BookChapters.index] = index
            it[BookChapters.title] = title
            it[BookChapters.level] = level
            it[BookChapters.wordCount] = wordCount
            it[BookChapters.imageCount] = imageCount
            it[BookChapters.href] = href
            it[createdAt] = now
            it[updatedAt] = now
        }[BookChapters.id]
        
        BookChapter(
            id = id.value,
            bookId = bookId,
            index = index,
            title = title,
            level = level,
            wordCount = wordCount,
            imageCount = imageCount,
            href = href,
            createdAt = now,
            updatedAt = now
        )
    }
    
    fun updateStats(chapterId: Int, wordCount: Int, imageCount: Int): Int = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        BookChapters.update({ BookChapters.id eq chapterId }) {
            it[BookChapters.wordCount] = wordCount
            it[BookChapters.imageCount] = imageCount
            it[updatedAt] = now
        }
    }
    
    fun deleteByBookId(bookId: Int): Int = transaction {
        BookChapters.deleteWhere { BookChapters.bookId eq bookId }
    }
    
    // === Chapter Content Operations ===
    
    fun saveChapterContent(chapterId: Int, elements: List<ContentElement>) = transaction {
        val contentJson = json.encodeToString(elements)
        
        ChapterContents.deleteWhere { ChapterContents.chapterId eq chapterId }
        ChapterContents.insert {
            it[ChapterContents.chapterId] = chapterId
            it[content] = contentJson
        }
    }
    
    fun getChapterContent(chapterId: Int): List<ContentElement>? = transaction {
        ChapterContents.selectAll()
            .where { ChapterContents.chapterId eq chapterId }
            .map { json.decodeFromString<List<ContentElement>>(it[ChapterContents.content]) }
            .singleOrNull()
    }
    
    // === Resource Operations ===
    
    fun saveResource(
        bookId: Int,
        path: String,
        storedPath: String,
        mediaType: String,
        size: Long
    ) = transaction {
        ChapterResources.insert {
            it[ChapterResources.bookId] = bookId
            it[ChapterResources.path] = path
            it[ChapterResources.storedPath] = storedPath
            it[ChapterResources.mediaType] = mediaType
            it[ChapterResources.size] = size
        }
    }
    
    fun findResource(bookId: Int, path: String): Pair<String, String>? = transaction {
        ChapterResources.selectAll()
            .where { (ChapterResources.bookId eq bookId) and (ChapterResources.path eq path) }
            .map { it[ChapterResources.storedPath] to it[ChapterResources.mediaType] }
            .singleOrNull()
    }
    
    fun deleteResourcesByBookId(bookId: Int): Int = transaction {
        ChapterResources.deleteWhere { ChapterResources.bookId eq bookId }
    }
    
    private fun toBookChapter(row: ResultRow) = BookChapter(
        id = row[BookChapters.id].value,
        bookId = row[BookChapters.bookId].value,
        index = row[BookChapters.index],
        title = row[BookChapters.title],
        level = row[BookChapters.level],
        wordCount = row[BookChapters.wordCount],
        imageCount = row[BookChapters.imageCount],
        href = row[BookChapters.href],
        createdAt = row[BookChapters.createdAt],
        updatedAt = row[BookChapters.updatedAt]
    )
}
