package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.Books
import com.bookd.domain.model.Book
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BookRepository {
    fun count(): Long = transaction {
        Books.selectAll().count()
    }

    suspend fun countAsync(): Long = dbQuery {
        Books.selectAll().count()
    }
    
    fun countBySourceId(sourceId: Int): Long = transaction {
        Books.selectAll().where { Books.sourceId eq sourceId }.count()
    }

    suspend fun countBySourceIdAsync(sourceId: Int): Long = dbQuery {
        Books.selectAll().where { Books.sourceId eq sourceId }.count()
    }
    
    fun findAll(limit: Int = 100, offset: Long = 0): List<Book> = transaction {
        Books.selectAll()
            .orderBy(Books.title to SortOrder.ASC)
            .limit(limit).offset(offset)
            .map { toBook(it) }
    }

    suspend fun findAllAsync(limit: Int = 100, offset: Long = 0): List<Book> = dbQuery {
        Books.selectAll()
            .orderBy(Books.title to SortOrder.ASC)
            .limit(limit).offset(offset)
            .map { toBook(it) }
    }
    
    fun findById(id: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq id }
            .map { toBook(it) }
            .singleOrNull()
    }

    suspend fun findByIdAsync(id: Int): Book? = dbQuery {
        Books.selectAll().where { Books.id eq id }
            .map { toBook(it) }
            .singleOrNull()
    }

    /**
     * 批量根据 ID 查询书籍
     * 用于避免 N+1 查询问题
     */
    fun findAllById(ids: List<Int>): List<Book> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        
        Books.selectAll()
            .where { Books.id inList ids }
            .map { toBook(it) }
    }

    suspend fun findAllByIdAsync(ids: List<Int>): List<Book> = dbQuery {
        if (ids.isEmpty()) {
            emptyList()
        } else {
            Books.selectAll()
                .where { Books.id inList ids }
                .map { toBook(it) }
        }
    }

    fun findByFilePaths(filePaths: Collection<String>): Map<String, Book> = transaction {
        if (filePaths.isEmpty()) return@transaction emptyMap()

        filePaths.distinct()
            .chunked(500)
            .flatMap { chunk ->
                Books.selectAll()
                    .where { Books.filePath inList chunk }
                    .map { toBook(it) }
            }
            .associateBy { it.filePath }
    }
    
    fun findByFilePath(filePath: String): Book? = transaction {
        Books.selectAll().where { Books.filePath eq filePath }
            .map { toBook(it) }
            .singleOrNull()
    }
    
    fun findBySourceId(sourceId: Int): List<Book> = transaction {
        Books.selectAll()
            .where { Books.sourceId eq sourceId }
            .orderBy(Books.title to SortOrder.ASC)
            .map { toBook(it) }
    }

    suspend fun findBySourceIdAsync(sourceId: Int): List<Book> = dbQuery {
        Books.selectAll()
            .where { Books.sourceId eq sourceId }
            .orderBy(Books.title to SortOrder.ASC)
            .map { toBook(it) }
    }
    
    /**
     * 根据书源 ID 分页获取书籍列表（APP 端专用）
     */
    fun findBySourceIdPaged(sourceId: Int, limit: Int, offset: Long): List<Book> = transaction {
        Books.selectAll()
            .where { Books.sourceId eq sourceId }
            .orderBy(Books.title to SortOrder.ASC)
            .limit(limit).offset(offset)
            .map { toBook(it) }
    }

    suspend fun findBySourceIdPagedAsync(sourceId: Int, limit: Int, offset: Long): List<Book> = dbQuery {
        Books.selectAll()
            .where { Books.sourceId eq sourceId }
            .orderBy(Books.title to SortOrder.ASC)
            .limit(limit).offset(offset)
            .map { toBook(it) }
    }
    
    fun create(
        title: String,
        author: String?,
        format: String,
        filePath: String,
        fileSize: Long,
        sourceId: Int? = null
    ): Book = transaction {
        val now = TimeProvider.now()
        val id = Books.insert {
            it[Books.title] = title
            it[Books.author] = author
            it[Books.format] = format
            it[Books.filePath] = filePath
            it[Books.fileSize] = fileSize
            it[Books.sourceId] = sourceId
            it[createdAt] = now
            it[updatedAt] = now
        }[Books.id]
        
        Book(
            id = id.value,
            title = title,
            author = author,
            format = format,
            filePath = filePath,
            fileSize = fileSize,
            sourceId = sourceId
        )
    }
    
    private fun toBook(row: ResultRow) = Book(
        id = row[Books.id].value,
        title = row[Books.title],
        author = row[Books.author],
        format = row[Books.format],
        filePath = row[Books.filePath],
        coverPath = row[Books.coverPath],
        coverWidth = row[Books.coverWidth],
        coverHeight = row[Books.coverHeight],
        fileSize = row[Books.fileSize],
        isbn = row[Books.isbn],
        publisher = row[Books.publisher],
        description = row[Books.description],
        sourceId = row[Books.sourceId]?.value,
        chaptersParsed = row[Books.chaptersParsed],
        chaptersCount = row[Books.chaptersCount],
        chapterCount = row[Books.tocChapterCount] ?: 0,
        totalWordCount = row[Books.totalWordCount] ?: 0,
        totalImageCount = row[Books.totalImageCount] ?: 0,
        lastParsedAt = row[Books.lastParsedAt],
        parseStatus = row[Books.parseStatus],
        parseProgress = row[Books.parseProgress],
        createdAt = row[Books.createdAt],
        updatedAt = row[Books.updatedAt],
        statsUpdatedAt = row[Books.statsUpdatedAt]
    )
    
    fun deleteBySourceId(sourceId: Int): Int = transaction {
        Books.deleteWhere { Books.sourceId eq sourceId }
    }
    
    fun deleteById(id: Int): Int = transaction {
        Books.deleteWhere { Books.id eq id }
    }
    
    fun deleteByFilePath(filePath: String): Int = transaction {
        Books.deleteWhere { Books.filePath eq filePath }
    }
    
    fun deleteAll(): Int = transaction {
        Books.deleteAll()
    }
    
    fun updateMetadata(
        id: Int,
        title: String? = null,
        author: String? = null,
        coverPath: String? = null,
        isbn: String? = null,
        publisher: String? = null,
        description: String? = null
    ): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            if (title != null) it[Books.title] = title
            if (author != null) it[Books.author] = author
            if (coverPath != null) it[Books.coverPath] = coverPath
            if (isbn != null) it[Books.isbn] = isbn
            if (publisher != null) it[Books.publisher] = publisher
            if (description != null) it[Books.description] = description
            it[updatedAt] = now
        }
    }

    suspend fun updateMetadataAsync(
        id: Int,
        title: String? = null,
        author: String? = null,
        coverPath: String? = null,
        isbn: String? = null,
        publisher: String? = null,
        description: String? = null
    ): Int = dbQuery {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            if (title != null) it[Books.title] = title
            if (author != null) it[Books.author] = author
            if (coverPath != null) it[Books.coverPath] = coverPath
            if (isbn != null) it[Books.isbn] = isbn
            if (publisher != null) it[Books.publisher] = publisher
            if (description != null) it[Books.description] = description
            it[updatedAt] = now
        }
    }
    
    /**
     * 更新书籍封面路径和尺寸
     */
    fun updateCoverPath(bookId: Int, coverPath: String?, width: Int? = null, height: Int? = null): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq bookId }) {
            it[Books.coverPath] = coverPath
            it[coverWidth] = width
            it[coverHeight] = height
            it[updatedAt] = now
        }
    }

    suspend fun updateCoverPathAsync(bookId: Int, coverPath: String?, width: Int? = null, height: Int? = null): Int = dbQuery {
        val now = TimeProvider.now()
        Books.update({ Books.id eq bookId }) {
            it[Books.coverPath] = coverPath
            it[coverWidth] = width
            it[coverHeight] = height
            it[updatedAt] = now
        }
    }
    
    /**
     * 更新章节解析状态
     */
    fun updateChaptersParsed(
        id: Int,
        chaptersCount: Int,
        tocChapterCount: Int? = null,
        totalWordCount: Int? = null,
        totalImageCount: Int? = null
    ): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            it[chaptersParsed] = true
            it[Books.chaptersCount] = chaptersCount
            if (tocChapterCount != null) it[Books.tocChapterCount] = tocChapterCount
            if (totalWordCount != null) it[Books.totalWordCount] = totalWordCount
            if (totalImageCount != null) it[Books.totalImageCount] = totalImageCount
            if (tocChapterCount != null || totalWordCount != null || totalImageCount != null) {
                it[statsUpdatedAt] = now
            }
            it[lastParsedAt] = now
            it[parseStatus] = "completed"
            it[parseProgress] = 100
            it[updatedAt] = now
        }
    }

    fun updateStatistics(
        id: Int,
        tocChapterCount: Int,
        totalWordCount: Int,
        totalImageCount: Int
    ): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            it[Books.tocChapterCount] = tocChapterCount
            it[Books.totalWordCount] = totalWordCount
            it[Books.totalImageCount] = totalImageCount
            it[statsUpdatedAt] = now
            it[updatedAt] = now
        }
    }

    fun backfillMissingStatistics(): Int = transaction {
        val missingIds = Books.select(Books.id)
            .where { Books.statsUpdatedAt.isNull() }
            .map { it[Books.id].value }

        if (missingIds.isEmpty()) return@transaction 0

        val statsByBookId = missingIds
            .chunked(500)
            .flatMap { chunk ->
                com.bookd.data.entity.BookDocuments.select(
                    com.bookd.data.entity.BookDocuments.bookId,
                    com.bookd.data.entity.BookDocuments.inToc,
                    com.bookd.data.entity.BookDocuments.wordCount,
                    com.bookd.data.entity.BookDocuments.imageCount
                )
                    .where { com.bookd.data.entity.BookDocuments.bookId inList chunk }
                    .toList()
            }
            .groupBy { it[com.bookd.data.entity.BookDocuments.bookId].value }
            .mapValues { (_, rows) ->
                BookStatisticsUpdate(
                    tocChapterCount = rows.count { it[com.bookd.data.entity.BookDocuments.inToc] },
                    totalWordCount = rows.sumOf { it[com.bookd.data.entity.BookDocuments.wordCount] },
                    totalImageCount = rows.sumOf { it[com.bookd.data.entity.BookDocuments.imageCount] }
                )
            }

        val now = TimeProvider.now()
        var updatedCount = 0
        missingIds.forEach { bookId ->
            val stats = statsByBookId[bookId] ?: BookStatisticsUpdate(0, 0, 0)
            updatedCount += Books.update({ Books.id eq bookId }) {
                it[Books.tocChapterCount] = stats.tocChapterCount
                it[Books.totalWordCount] = stats.totalWordCount
                it[Books.totalImageCount] = stats.totalImageCount
                it[Books.statsUpdatedAt] = now
            }
        }
        updatedCount
    }
    
    /**
     * 获取未解析章节的书籍
     */
    fun findUnparsedBooks(limit: Int = 10): List<Book> = transaction {
        Books.selectAll()
            .where { 
                (Books.chaptersParsed eq false) and 
                ((Books.parseStatus eq "pending") or (Books.parseStatus.isNull()))
            }
            .limit(limit)
            .map { toBook(it) }
    }
    
    /**
     * 更新书籍解析状态
     */
    fun updateParseStatus(id: Int, status: String, progress: Int = 0): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            it[parseStatus] = status
            it[parseProgress] = progress
            it[updatedAt] = now
        }
    }
    
    /**
     * 重置章节解析状态（用于重新解析）
     */
    fun resetChaptersParsed(id: Int): Int = transaction {
        val now = TimeProvider.now()
        Books.update({ Books.id eq id }) {
            it[chaptersParsed] = false
            it[chaptersCount] = 0
            it[lastParsedAt] = null
            it[parseStatus] = "pending"
            it[parseProgress] = 0
            it[updatedAt] = now
        }
    }
}

private data class BookStatisticsUpdate(
    val tocChapterCount: Int,
    val totalWordCount: Int,
    val totalImageCount: Int
)
