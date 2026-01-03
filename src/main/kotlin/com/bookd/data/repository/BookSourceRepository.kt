package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.BookSources
import com.bookd.domain.model.BookSource
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookSourceRepository {
    fun findAll(): List<BookSource> = transaction {
        BookSources.selectAll().map { toBookSource(it) }
    }
    
    fun findById(id: Int): BookSource? = transaction {
        BookSources.selectAll().where { BookSources.id eq id }
            .map { toBookSource(it) }
            .singleOrNull()
    }
    
    fun create(name: String, path: String): BookSource = transaction {
        val now = TimeProvider.now()
        val id = BookSources.insert {
            it[BookSources.name] = name
            it[BookSources.path] = path
            it[enabled] = true
            it[createdAt] = now
            it[updatedAt] = now
        }[BookSources.id]
        
        BookSource(id.value, name, path, true)
    }
    
    fun delete(id: Int): Boolean = transaction {
        BookSources.deleteWhere { BookSources.id.eq(id) } > 0
    }
    
    fun toggleEnabled(id: Int): Boolean = transaction {
        val current = findById(id)
        if (current != null) {
            BookSources.update({ BookSources.id eq id }) {
                it[enabled] = !current.enabled
                it[updatedAt] = TimeProvider.now()
            }
            true
        } else {
            false
        }
    }
    
    private fun toBookSource(row: ResultRow) = BookSource(
        id = row[BookSources.id].value,
        name = row[BookSources.name],
        path = row[BookSources.path],
        enabled = row[BookSources.enabled]
    )
}
