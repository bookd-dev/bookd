package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object InviteTokens : IntIdTable("invite_tokens") {
    val token = varchar("token", 255).uniqueIndex()
    val createdBy = reference("created_by", Users)
    val used = bool("used").default(false)
    val usedBy = reference("used_by", Users).nullable()
    val createdAt = datetime("created_at")
    val usedAt = datetime("used_at").nullable()
}
