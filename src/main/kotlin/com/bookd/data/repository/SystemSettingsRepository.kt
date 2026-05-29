package com.bookd.data.repository

import com.bookd.data.entity.SystemSettings
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SystemSettingsRepository {
    suspend fun getValue(key: String): String? = dbQuery {
        SystemSettings.selectAll()
            .where { SystemSettings.key eq key }
            .map { it[SystemSettings.value] }
            .singleOrNull()
    }

    suspend fun upsertValue(key: String, value: String): String = dbQuery {
        val now = TimeProvider.now()
        val updated = SystemSettings.update({ SystemSettings.key eq key }) {
            it[SystemSettings.value] = value
            it[updatedAt] = now
        }

        if (updated == 0) {
            SystemSettings.insert {
                it[SystemSettings.key] = key
                it[SystemSettings.value] = value
                it[updatedAt] = now
            }
        }

        value
    }
}
