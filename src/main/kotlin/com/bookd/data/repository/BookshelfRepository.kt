package com.bookd.data.repository

import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Bookshelves
import com.bookd.domain.model.Bookshelf
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 书架数据仓库
 * 
 * 负责书架的 CRUD 操作
 */
class BookshelfRepository {
    
    /**
     * 创建书架
     */
    fun create(
        userId: Int,
        name: String,
        description: String? = null,
        isSystemDefault: Boolean = false,
        sortOrder: Int = 0
    ): Bookshelf = transaction {
        val now = TimeProvider.now()
        val id = Bookshelves.insert {
            it[Bookshelves.userId] = userId
            it[Bookshelves.name] = name
            it[Bookshelves.description] = description
            it[Bookshelves.isSystemDefault] = isSystemDefault
            it[Bookshelves.sortOrder] = sortOrder
            it[Bookshelves.createdAt] = now
            it[Bookshelves.updatedAt] = now
        }[Bookshelves.id]
        
        Bookshelf(
            id = id.value,
            userId = userId,
            name = name,
            description = description,
            sortOrder = sortOrder,
            bookCount = 0,
            isSystemDefault = isSystemDefault,
            createdAt = now,
            updatedAt = now
        )
    }
    
    /**
     * 根据 ID 查询书架
     */
    fun findById(id: Int): Bookshelf? = transaction {
        Bookshelves.selectAll()
            .where { Bookshelves.id eq id }
            .map { toBookshelf(it) }
            .singleOrNull()
    }
    
    /**
     * 获取用户的所有书架
     * 按 sortOrder 排序,系统默认书架(全部)会排在最前
     */
    fun findByUserId(userId: Int): List<Bookshelf> = transaction {
        Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }
    
    /**
     * 获取用户的默认书架("全部"书架)
     */
    fun getDefaultBookshelf(userId: Int): Bookshelf? = transaction {
        Bookshelves.selectAll()
            .where { (Bookshelves.userId eq userId) and (Bookshelves.isSystemDefault eq true) }
            .map { toBookshelf(it) }
            .singleOrNull()
    }
    
    /**
     * 根据用户ID和书架名称查找书架
     */
    fun findByUserIdAndName(userId: Int, name: String): Bookshelf? = transaction {
        Bookshelves.selectAll()
            .where { (Bookshelves.userId eq userId) and (Bookshelves.name eq name) }
            .map { toBookshelf(it) }
            .singleOrNull()
    }
    
    /**
     * 判断书架是否为系统默认书架
     */
    fun isSystemDefault(bookshelfId: Int): Boolean = transaction {
        Bookshelves.selectAll()
            .where { Bookshelves.id eq bookshelfId }
            .map { it[Bookshelves.isSystemDefault] }
            .singleOrNull() ?: false
    }
    
    /**
     * 更新书架信息
     */
    fun update(id: Int, name: String?, description: String?): Bookshelf? = transaction {
        val now = TimeProvider.now()
        val updated = Bookshelves.update({ Bookshelves.id eq id }) {
            if (name != null) it[Bookshelves.name] = name
            if (description != null) it[Bookshelves.description] = description
            it[Bookshelves.updatedAt] = now
        }
        
        if (updated > 0) {
            findById(id)
        } else {
            null
        }
    }
    
    /**
     * 删除书架
     */
    fun delete(id: Int): Int = transaction {
        Bookshelves.deleteWhere { Bookshelves.id eq id }
    }
    
    /**
     * 更新书架排序顺序
     */
    fun updateSortOrder(id: Int, sortOrder: Int): Int = transaction {
        val now = TimeProvider.now()
        Bookshelves.update({ Bookshelves.id eq id }) {
            it[Bookshelves.sortOrder] = sortOrder
            it[Bookshelves.updatedAt] = now
        }
    }
    
    /**
     * 批量更新书架排序顺序
     * @param bookshelfIds 按新顺序排列的书架ID列表
     */
    fun reorderBookshelves(userId: Int, bookshelfIds: List<Int>): Int = transaction {
        val now = TimeProvider.now()
        var updatedCount = 0
        
        bookshelfIds.forEachIndexed { index, bookshelfId ->
            val updated = Bookshelves.update({ 
                (Bookshelves.id eq bookshelfId) and (Bookshelves.userId eq userId) 
            }) {
                it[sortOrder] = index
                it[updatedAt] = now
            }
            if (updated > 0) updatedCount++
        }
        
        updatedCount
    }
    
    /**
     * 获取书架中的书籍数量
     */
    fun countBooks(bookshelfId: Int): Int = transaction {
        BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .count()
            .toInt()
    }
    
    /**
     * 获取用户书架的最大排序值
     */
    fun getMaxSortOrder(userId: Int): Int = transaction {
        Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .maxOfOrNull { it[Bookshelves.sortOrder] } ?: 0
    }
    
    private fun toBookshelf(row: ResultRow) = Bookshelf(
        id = row[Bookshelves.id].value,
        userId = row[Bookshelves.userId].value,
        name = row[Bookshelves.name],
        description = row[Bookshelves.description],
        sortOrder = row[Bookshelves.sortOrder],
        bookCount = 0, // 需要单独查询或在 Service 层填充
        isSystemDefault = row[Bookshelves.isSystemDefault],
        createdAt = row[Bookshelves.createdAt],
        updatedAt = row[Bookshelves.updatedAt]
    )
}
