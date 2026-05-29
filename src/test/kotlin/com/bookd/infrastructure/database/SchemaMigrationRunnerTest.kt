package com.bookd.infrastructure.database

import com.bookd.data.repository.BookRepository
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SchemaMigrationRunnerTest {

    @Test
    fun `given empty database when migrating then baseline schema and migration record are created`() {
        connectDatabase("empty")

        val applied = SchemaMigrationRunner().migrate()

        assertEquals(listOf("1", "2", "3"), applied)
        transaction {
            assertEquals(3, SchemaMigrations.selectAll().count())
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

        assertEquals(listOf("1", "2", "3"), applied)
        assertEquals("Existing Book", repository.findById(existingBook.id)?.title)
        transaction {
            assertEquals(3, SchemaMigrations.selectAll().count())
        }
    }

    @Test
    fun `given migration already recorded when migrating again then no migration is reapplied`() {
        connectDatabase("idempotent")
        val runner = SchemaMigrationRunner()
        assertEquals(listOf("1", "2", "3"), runner.migrate())

        val appliedAgain = runner.migrate()

        assertTrue(appliedAgain.isEmpty())
        transaction {
            assertEquals(3, SchemaMigrations.selectAll().count())
        }
    }

    @Test
    fun `given legacy provider and endpoint capability columns when migrating then unused columns are removed`() {
        connectDatabase("legacy_capability_columns")
        transaction {
            SchemaUtils.create(SchemaMigrations, *BackendSchemaTables.all)
            exec("ALTER TABLE ai_providers ADD COLUMN supports_tts BOOLEAN DEFAULT FALSE")
            exec("ALTER TABLE ai_providers ADD COLUMN supports_llm BOOLEAN DEFAULT FALSE")
            exec("ALTER TABLE ai_provider_endpoints ADD COLUMN supports_tts BOOLEAN DEFAULT FALSE")
            exec("ALTER TABLE ai_provider_endpoints ADD COLUMN supports_llm BOOLEAN DEFAULT FALSE")
            SchemaMigrations.insert {
                it[version] = "1"
                it[description] = "baseline current backend schema"
                it[installedAt] = TimeProvider.now()
            }
            SchemaMigrations.insert {
                it[version] = "2"
                it[description] = "add admin personalization and AI service configuration"
                it[installedAt] = TimeProvider.now()
            }
        }

        val applied = SchemaMigrationRunner().migrate()

        assertEquals(listOf("3"), applied)
        transaction {
            assertFalse(hasColumn("AI_PROVIDERS", "SUPPORTS_TTS"))
            assertFalse(hasColumn("AI_PROVIDERS", "SUPPORTS_LLM"))
            assertFalse(hasColumn("AI_PROVIDER_ENDPOINTS", "SUPPORTS_TTS"))
            assertFalse(hasColumn("AI_PROVIDER_ENDPOINTS", "SUPPORTS_LLM"))
        }
    }

    private fun connectDatabase(name: String) {
        Database.connect(
            url = "jdbc:h2:mem:schema_migration_${name}_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
    }

    private fun hasColumn(tableName: String, columnName: String): Boolean {
        return TransactionManager.current().exec(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = '$tableName' AND COLUMN_NAME = '$columnName'
            """.trimIndent()
        ) { result ->
            result.next()
            result.getInt(1) > 0
        } ?: false
    }
}
