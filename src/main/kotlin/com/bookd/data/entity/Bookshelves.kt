package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * 用户书架表
 * 
 * 每个用户可以创建多个书架来分类管理书籍。
 * 系统会自动为每个用户创建一个"全部"书架(isSystemDefault=true),
 * 该书架不可删除、不可重命名,包含用户所有已加入书架的书籍。
 */
object Bookshelves : IntIdTable("bookshelves") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val description = varchar("description", 500).nullable()
    val sortOrder = integer("sort_order").default(0)
    val isSystemDefault = bool("is_system_default").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    
    init {
        // 确保同一用户的书架名称唯一
        uniqueIndex(userId, name)
    }
}
