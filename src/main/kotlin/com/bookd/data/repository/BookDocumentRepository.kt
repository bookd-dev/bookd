package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import com.bookd.domain.model.BookDocument
import com.bookd.domain.model.ContentElement
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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

    suspend fun findByBookIdAsync(bookId: Int): List<BookDocument> = dbQuery {
        BookDocuments.selectAll()
            .where { BookDocuments.bookId eq bookId }
            .orderBy(BookDocuments.index to SortOrder.ASC)
            .map { toBookDocument(it) }
    }

    suspend fun findStatsByBookIds(bookIds: List<Int>): Map<Int, BookDocumentStats> = dbQuery {
        if (bookIds.isEmpty()) {
            emptyMap()
        } else {
            BookDocuments.select(
                BookDocuments.bookId,
                BookDocuments.inToc,
                BookDocuments.wordCount,
                BookDocuments.imageCount
            )
                .where { BookDocuments.bookId inList bookIds }
                .groupBy { it[BookDocuments.bookId].value }
                .mapValues { (_, rows) ->
                    BookDocumentStats(
                        chapterCount = rows.count { it[BookDocuments.inToc] },
                        totalWordCount = rows.sumOf { it[BookDocuments.wordCount] },
                        totalImageCount = rows.sumOf { it[BookDocuments.imageCount] }
                    )
                }
        }
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

    suspend fun findTocByBookIdAsync(bookId: Int): List<BookDocument> = dbQuery {
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

    suspend fun findByBookIdAndIndexAsync(bookId: Int, index: Int): BookDocument? = dbQuery {
        BookDocuments.selectAll()
            .where { (BookDocuments.bookId eq bookId) and (BookDocuments.index eq index) }
            .map { toBookDocument(it) }
            .singleOrNull()
    }

    suspend fun findAdjacentIndexes(bookId: Int, index: Int): AdjacentDocumentIndexes = dbQuery {
        val indexes = BookDocuments.select(BookDocuments.index)
            .where { BookDocuments.bookId eq bookId }
            .orderBy(BookDocuments.index to SortOrder.ASC)
            .map { it[BookDocuments.index] }
        val currentPosition = indexes.indexOf(index)
        AdjacentDocumentIndexes(
            prevIndex = if (currentPosition > 0) indexes[currentPosition - 1] else null,
            nextIndex = if (currentPosition >= 0 && currentPosition < indexes.size - 1) indexes[currentPosition + 1] else null
        )
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

    suspend fun getDocumentContentAsync(documentId: Int): List<ContentElement>? = dbQuery {
        DocumentContents.selectAll()
            .where { DocumentContents.documentId eq documentId }
            .map { json.decodeFromString<List<ContentElement>>(it[DocumentContents.content]) }
            .singleOrNull()
    }
    
    // === Resource Operations ===
    
    /**
     * 保存书籍资源（图片），包含宽高信息
     */
    fun saveResource(
        bookId: Int,
        path: String,
        storedPath: String,
        mediaType: String,
        size: Long,
        width: Int? = null,
        height: Int? = null
    ) = transaction {
        DocumentResources.insert {
            it[DocumentResources.bookId] = bookId
            it[DocumentResources.path] = path
            it[DocumentResources.storedPath] = storedPath
            it[DocumentResources.mediaType] = mediaType
            it[DocumentResources.size] = size
            it[DocumentResources.width] = width
            it[DocumentResources.height] = height
        }
    }
    
    /**
     * 查找资源，返回包含宽高的信息
     * @return ResourceInfo 或 null
     */
    fun findResource(bookId: Int, path: String): ResourceInfo? = transaction {
        DocumentResources.selectAll()
            .where { (DocumentResources.bookId eq bookId) and (DocumentResources.path eq path) }
            .map { 
                ResourceInfo(
                    storedPath = it[DocumentResources.storedPath],
                    mediaType = it[DocumentResources.mediaType],
                    width = it[DocumentResources.width],
                    height = it[DocumentResources.height]
                )
            }
            .singleOrNull()
    }

    suspend fun findResourcesByBookIdAndPaths(bookId: Int, paths: Set<String>): Map<String, ResourceInfo> = dbQuery {
        if (paths.isEmpty()) {
            emptyMap()
        } else {
            DocumentResources.selectAll()
                .where { (DocumentResources.bookId eq bookId) and (DocumentResources.path inList paths.toList()) }
                .associate {
                    it[DocumentResources.path] to ResourceInfo(
                        storedPath = it[DocumentResources.storedPath],
                        mediaType = it[DocumentResources.mediaType],
                        width = it[DocumentResources.width],
                        height = it[DocumentResources.height]
                    )
                }
        }
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

/**
 * 资源信息数据类
 */
data class ResourceInfo(
    val storedPath: String,
    val mediaType: String,
    val width: Int?,
    val height: Int?
)

data class BookDocumentStats(
    val chapterCount: Int,
    val totalWordCount: Int,
    val totalImageCount: Int
)

data class AdjacentDocumentIndexes(
    val prevIndex: Int?,
    val nextIndex: Int?
)
