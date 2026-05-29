package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object SystemSettings : Table("system_settings") {
    val key = varchar("key", 100)
    val value = text("value")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(key)
}
