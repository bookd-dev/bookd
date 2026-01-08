package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.ChapterContents
import com.bookd.data.entity.ChapterResources
import com.bookd.domain.model.BookDocument
import com.bookd.domain.model.ContentElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookDocumentRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 获取书籍的所有文档（spine 顺序）
     */
    fun findByBookId(bookId: Int): List<BookDocument> = transaction {
        BookDocuments.selectAll()
            .where { BookDocuments.bookId eq bookId }
            .orderBy(BookDocuments.index to SortOrder.ASC)
            .map { toBookDocument(it) }
    }
    
    /**
     * 获取书籍的目录项（仅 inToc=true）
     */
    fun findTocByBookId(bookId: Int): List<BookDocument> = transaction {
        BookDocuments.selectAll()
            .where { (BookDocuments.bookId eq bookId) and (BookDocuments.inToc eq true) }
            .orderBy(BookDocuments.index to SortOrder.ASC)
            .map { toBookDocument(it) }
    }
    
    fun findByBookIdAndIndex(bookId: Int, index: Int): BookDocument? = transaction {
        BookDocuments.selectAll()
            .where { (BookDocuments.bookId eq bookId) and (BookDocuments.index eq index) }
            .map { toBookDocument(it) }
            .singleOrNull()
    }
    
    fun countByBookId(bookId: Int): Int = transaction {
        BookDocuments.selectAll()
            .where { BookDocuments.bookId eq bookId }
            .count()
            .toInt()
    }
    
    /**
     * 统计目录项数量
     */
    fun countTocByBookId(bookId: Int): Int = transaction {
        BookDocuments.selectAll()
            .where { (BookDocuments.bookId eq bookId) and (BookDocuments.inToc eq true) }
            .count()
            .toInt()
    }
    
    fun create(
        bookId: Int,
        index: Int,
        href: String?,
        inToc: Boolean = false,
        title: String? = null,
        level: Int = 0,
        wordCount: Int = 0,
        imageCount: Int = 0
    ): BookDocument = transaction {
        val now = TimeProvider.now()
        val id = BookDocuments.insert {
            it[BookDocuments.bookId] = bookId
            it[BookDocuments.index] = index
            it[BookDocuments.href] = href
            it[BookDocuments.inToc] = inToc
            it[BookDocuments.title] = title
            it[BookDocuments.level] = level
            it[BookDocuments.wordCount] = wordCount
            it[BookDocuments.imageCount] = imageCount
            it[createdAt] = now
            it[updatedAt] = now
        }[BookDocuments.id]
        
        BookDocument(
            id = id.value,
            bookId = bookId,
            index = index,
            href = href,
            inToc = inToc,
            title = title,
            level = level,
            wordCount = wordCount,
            imageCount = imageCount,
            createdAt = now,
            updatedAt = now
        )
    }
    
    fun updateStats(documentId: Int, wordCount: Int, imageCount: Int): Int = transaction {
        val now = TimeProvider.now()
        BookDocuments.update({ BookDocuments.id eq documentId }) {
            it[BookDocuments.wordCount] = wordCount
            it[BookDocuments.imageCount] = imageCount
            it[updatedAt] = now
        }
    }
    
    fun deleteByBookId(bookId: Int): Int = transaction {
        BookDocuments.deleteWhere { BookDocuments.bookId eq bookId }
    }
    
    // === Document Content Operations ===
    
    fun saveDocumentContent(documentId: Int, elements: List<ContentElement>) = transaction {
        val contentJson = json.encodeToString(elements)
        
        ChapterContents.deleteWhere { ChapterContents.documentId eq documentId }
        ChapterContents.insert {
            it[ChapterContents.documentId] = documentId
            it[content] = contentJson
        }
    }
    
    fun getDocumentContent(documentId: Int): List<ContentElement>? = transaction {
        ChapterContents.selectAll()
            .where { ChapterContents.documentId eq documentId }
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
    
    private fun toBookDocument(row: ResultRow) = BookDocument(
        id = row[BookDocuments.id].value,
        bookId = row[BookDocuments.bookId].value,
        index = row[BookDocuments.index],
        href = row[BookDocuments.href],
        inToc = row[BookDocuments.inToc],
        title = row[BookDocuments.title],
        level = row[BookDocuments.level],
        wordCount = row[BookDocuments.wordCount],
        imageCount = row[BookDocuments.imageCount],
        createdAt = row[BookDocuments.createdAt],
        updatedAt = row[BookDocuments.updatedAt]
    )
}
