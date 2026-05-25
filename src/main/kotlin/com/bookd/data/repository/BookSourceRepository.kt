package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.BookSources
import com.bookd.domain.model.BookSource
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BookSourceRepository {
    fun findAll(): List<BookSource> = transaction {
        BookSources.selectAll().map { toBookSource(it) }
    }

    suspend fun findAllAsync(): List<BookSource> = dbQuery {
        BookSources.selectAll().map { toBookSource(it) }
    }
    
    fun findById(id: Int): BookSource? = transaction {
        BookSources.selectAll().where { BookSources.id eq id }
            .map { toBookSource(it) }
            .singleOrNull()
    }

    suspend fun findByIdAsync(id: Int): BookSource? = dbQuery {
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

    suspend fun createAsync(name: String, path: String): BookSource = dbQuery {
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

    suspend fun deleteAsync(id: Int): Boolean = dbQuery {
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

    suspend fun toggleEnabledAsync(id: Int): Boolean = dbQuery {
        val current = BookSources.selectAll().where { BookSources.id eq id }
            .map { toBookSource(it) }
            .singleOrNull()

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
