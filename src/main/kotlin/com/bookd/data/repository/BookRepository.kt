package com.bookd.data.repository

import com.bookd.data.entity.Books
import com.bookd.domain.model.Book
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookRepository {
    fun findAll(limit: Int = 100, offset: Long = 0): List<Book> = transaction {
        Books.selectAll()
            .limit(limit, offset)
            .map { toBook(it) }
    }
    
    fun findById(id: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq id }
            .map { toBook(it) }
            .singleOrNull()
    }
    
    fun findByFilePath(filePath: String): Book? = transaction {
        Books.selectAll().where { Books.filePath eq filePath }
            .map { toBook(it) }
            .singleOrNull()
    }
    
    fun create(
        title: String,
        author: String?,
        format: String,
        filePath: String,
        fileSize: Long,
        sourceId: Int? = null
    ): Book = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
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
        fileSize = row[Books.fileSize],
        isbn = row[Books.isbn],
        publisher = row[Books.publisher],
        description = row[Books.description],
        sourceId = row[Books.sourceId]?.value
    )
    
    fun deleteBySourceId(sourceId: Int): Int = transaction {
        Books.deleteWhere { Books.sourceId eq sourceId }
    }
    
    fun deleteAll(): Int = transaction {
        Books.deleteAll()
    }
}
