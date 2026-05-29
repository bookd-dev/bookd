package com.bookd.infrastructure.database

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.AiModels
import com.bookd.data.entity.AiProviderEndpoints
import com.bookd.data.entity.AiProviders
import com.bookd.data.entity.SystemSettings
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

class SchemaMigrationRunner(
    private val migrations: List<SchemaMigration> = defaultMigrations
) {
    private val logger = LoggerFactory.getLogger(SchemaMigrationRunner::class.java)

    fun migrate(): List<String> = transaction {
        SchemaUtils.create(SchemaMigrations)
        val appliedVersions = SchemaMigrations
            .selectAll()
            .map { it[SchemaMigrations.version] }
            .toSet()

        migrations.filterNot { it.version in appliedVersions }.map { migration ->
            logger.info("Applying schema migration ${migration.version}: ${migration.description}")
            migration.apply()
            SchemaMigrations.insert {
                it[version] = migration.version
                it[description] = migration.description
                it[installedAt] = TimeProvider.now()
            }
            migration.version
        }
    }

    companion object {
        val defaultMigrations: List<SchemaMigration> = listOf(
            SchemaMigration(
                version = "1",
                description = "baseline current backend schema"
            ) {
                SchemaUtils.create(*BackendSchemaTables.all)
            },
            SchemaMigration(
                version = "2",
                description = "add admin personalization and AI service configuration"
            ) {
                SchemaUtils.create(
                    SystemSettings,
                    AiProviders,
                    AiProviderEndpoints,
                    AiModels
                )
            },
            SchemaMigration(
                version = "3",
                description = "move AI capability flags to model configuration only"
            ) {
                TransactionManager.current().exec("ALTER TABLE ai_providers DROP COLUMN IF EXISTS supports_tts")
                TransactionManager.current().exec("ALTER TABLE ai_providers DROP COLUMN IF EXISTS supports_llm")
                TransactionManager.current().exec("ALTER TABLE ai_provider_endpoints DROP COLUMN IF EXISTS supports_tts")
                TransactionManager.current().exec("ALTER TABLE ai_provider_endpoints DROP COLUMN IF EXISTS supports_llm")
            }
        )
    }
}

data class SchemaMigration(
    val version: String,
    val description: String,
    val apply: () -> Unit
)

object SchemaMigrations : Table("schema_migrations") {
    val version = varchar("version", 50)
    val description = varchar("description", 255)
    val installedAt = datetime("installed_at")

    override val primaryKey = PrimaryKey(version)
}
