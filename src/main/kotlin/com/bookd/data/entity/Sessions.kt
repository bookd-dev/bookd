package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.datetime

object Sessions : IntIdTable("sessions") {
    val token = varchar("token", 255).uniqueIndex()
    val userId = reference("user_id", Users)
    val createdAt = datetime("created_at")
    val expiresAt = datetime("expires_at")
}
