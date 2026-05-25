package com.bookd.infrastructure.database

import com.bookd.data.repository.BookRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SchemaMigrationRunnerTest {

    @Test
    fun `given empty database when migrating then baseline schema and migration record are created`() {
        connectDatabase("empty")

        val applied = SchemaMigrationRunner().migrate()

        assertEquals(listOf("1"), applied)
        transaction {
            assertEquals(1, SchemaMigrations.selectAll().count())
        }
        val book = BookRepository().create(
            title = "Migrated Book",
            author = null,
            format = "epub",
            filePath = "/tmp/migrated-book.epub",
            fileSize = 128L
        )
        assertEquals("Migrated Book", BookRepository().findById(book.id)?.title)
    }

    @Test
    fun `given existing current schema when migrating then existing data is preserved and baseline is recorded`() {
        connectDatabase("existing")
        transaction {
            SchemaUtils.create(*BackendSchemaTables.all)
        }
        val repository = BookRepository()
        val existingBook = repository.create(
            title = "Existing Book",
            author = null,
            format = "epub",
            filePath = "/tmp/existing-book.epub",
            fileSize = 256L
        )

        val applied = SchemaMigrationRunner().migrate()

        assertEquals(listOf("1"), applied)
        assertEquals("Existing Book", repository.findById(existingBook.id)?.title)
        transaction {
            assertEquals(1, SchemaMigrations.selectAll().count())
        }
    }

    @Test
    fun `given migration already recorded when migrating again then no migration is reapplied`() {
        connectDatabase("idempotent")
        val runner = SchemaMigrationRunner()
        assertEquals(listOf("1"), runner.migrate())

        val appliedAgain = runner.migrate()

        assertTrue(appliedAgain.isEmpty())
        transaction {
            assertEquals(1, SchemaMigrations.selectAll().count())
        }
    }

    private fun connectDatabase(name: String) {
        Database.connect(
            url = "jdbc:h2:mem:schema_migration_${name}_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
    }
}
