package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import com.bookd.domain.model.BookDocument
import com.bookd.domain.model.ContentElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookDocumentRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // 旧类型名称到新类型名称的映射
    private val typeNameMigrations = mapOf(
        "com.bookd.domain.model.ContentElement.Paragraph" to "paragraph",
        "com.bookd.domain.model.ContentElement.Heading" to "heading",
        "com.bookd.domain.model.ContentElement.Image" to "image",
        "com.bookd.domain.model.ContentElement.Quote" to "quote",
        "com.bookd.domain.model.ContentElement.Code" to "code",
        "com.bookd.domain.model.ContentElement.ListBlock" to "listBlock",
        "com.bookd.domain.model.ContentElement.Divider" to "divider",
        "com.bookd.domain.model.ContentElement.Footnote" to "footnote"
    )
    
    /**
     * 迁移旧的 ContentElement type 格式
     * 将完整类名替换为简短名称
     * @return 迁移的记录数
     */
    fun migrateContentElementTypes(): Int = transaction {
        var migratedCount = 0
        
        DocumentContents.selectAll().forEach { row ->
            val documentId = row[DocumentContents.documentId]
            var content = row[DocumentContents.content]
            var needsUpdate = false
            
            // 检查并替换所有旧类型名称
            for ((oldName, newName) in typeNameMigrations) {
                if (content.contains(oldName)) {
                    content = content.replace("\"type\":\"$oldName\"", "\"type\":\"$newName\"")
                    needsUpdate = true
                }
            }
            
            if (needsUpdate) {
                DocumentContents.update({ DocumentContents.documentId eq documentId }) {
                    it[DocumentContents.content] = content
                }
                migratedCount++
            }
        }
        
        migratedCount
    }
    
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
        
        DocumentContents.deleteWhere { DocumentContents.documentId eq documentId }
        DocumentContents.insert {
            it[DocumentContents.documentId] = documentId
            it[content] = contentJson
        }
    }
    
    fun getDocumentContent(documentId: Int): List<ContentElement>? = transaction {
        DocumentContents.selectAll()
            .where { DocumentContents.documentId eq documentId }
            .map { json.decodeFromString<List<ContentElement>>(it[DocumentContents.content]) }
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
        DocumentResources.insert {
            it[DocumentResources.bookId] = bookId
            it[DocumentResources.path] = path
            it[DocumentResources.storedPath] = storedPath
            it[DocumentResources.mediaType] = mediaType
            it[DocumentResources.size] = size
        }
    }
    
    fun findResource(bookId: Int, path: String): Pair<String, String>? = transaction {
        DocumentResources.selectAll()
            .where { (DocumentResources.bookId eq bookId) and (DocumentResources.path eq path) }
            .map { it[DocumentResources.storedPath] to it[DocumentResources.mediaType] }
            .singleOrNull()
    }
    
    fun deleteResourcesByBookId(bookId: Int): Int = transaction {
        DocumentResources.deleteWhere { DocumentResources.bookId eq bookId }
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
