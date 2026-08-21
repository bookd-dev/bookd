package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.datetime

object TxtParseRules : IntIdTable("txt_parse_rules") {
    val name = varchar("name", 100)
    val rule = text("rule")
    val example = text("example").nullable()
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0) // 优先级，数字越小越优先
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
