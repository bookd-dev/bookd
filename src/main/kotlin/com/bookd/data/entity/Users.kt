package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Users : IntIdTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255)
    val email = varchar("email", 100).nullable()
    val role = varchar("role", 20).default("user")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
