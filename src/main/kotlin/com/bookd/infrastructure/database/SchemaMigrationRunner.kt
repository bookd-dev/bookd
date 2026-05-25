package com.bookd.infrastructure.database

import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
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
