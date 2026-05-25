package com.bookd.data.repository

import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Bookshelves
import com.bookd.domain.model.Bookshelf
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
        createInCurrentTransaction(userId, name, description, isSystemDefault, sortOrder)
    }

    suspend fun createAsync(
        userId: Int,
        name: String,
        description: String? = null,
        isSystemDefault: Boolean = false,
        sortOrder: Int = 0
    ): Bookshelf = dbQuery {
        createInCurrentTransaction(userId, name, description, isSystemDefault, sortOrder)
    }

    /**
     * 根据 ID 查询书架
     */
    fun findById(id: Int): Bookshelf? = transaction {
        findByIdInCurrentTransaction(id)
    }

    suspend fun findByIdAsync(id: Int): Bookshelf? = dbQuery {
        findByIdInCurrentTransaction(id)
    }

    /**
     * 获取用户的所有书架
     * 按 sortOrder 排序,系统默认书架(全部)会排在最前
     */
    fun findByUserId(userId: Int): List<Bookshelf> = transaction {
        findByUserIdInCurrentTransaction(userId)
    }

    suspend fun findByUserIdAsync(userId: Int): List<Bookshelf> = dbQuery {
        findByUserIdInCurrentTransaction(userId)
    }

    /**
     * 获取用户的默认书架("全部"书架)
     */
    fun getDefaultBookshelf(userId: Int): Bookshelf? = transaction {
        getDefaultBookshelfInCurrentTransaction(userId)
    }

    suspend fun getDefaultBookshelfAsync(userId: Int): Bookshelf? = dbQuery {
        getDefaultBookshelfInCurrentTransaction(userId)
    }

    /**
     * 根据用户ID和书架名称查找书架
     */
    fun findByUserIdAndName(userId: Int, name: String): Bookshelf? = transaction {
        findByUserIdAndNameInCurrentTransaction(userId, name)
    }

    suspend fun findByUserIdAndNameAsync(userId: Int, name: String): Bookshelf? = dbQuery {
        findByUserIdAndNameInCurrentTransaction(userId, name)
    }

    /**
     * 判断书架是否为系统默认书架
     */
    fun isSystemDefault(bookshelfId: Int): Boolean = transaction {
        isSystemDefaultInCurrentTransaction(bookshelfId)
    }

    suspend fun isSystemDefaultAsync(bookshelfId: Int): Boolean = dbQuery {
        isSystemDefaultInCurrentTransaction(bookshelfId)
    }

    /**
     * 更新书架信息
     */
    fun update(id: Int, name: String?, description: String?): Bookshelf? = transaction {
        updateInCurrentTransaction(id, name, description)
    }

    suspend fun updateAsync(id: Int, name: String?, description: String?): Bookshelf? = dbQuery {
        updateInCurrentTransaction(id, name, description)
    }

    /**
     * 删除书架
     */
    fun delete(id: Int): Int = transaction {
        deleteInCurrentTransaction(id)
    }

    suspend fun deleteAsync(id: Int): Int = dbQuery {
        deleteInCurrentTransaction(id)
    }

    /**
     * 更新书架排序顺序
     */
    fun updateSortOrder(id: Int, sortOrder: Int): Int = transaction {
        updateSortOrderInCurrentTransaction(id, sortOrder)
    }

    suspend fun updateSortOrderAsync(id: Int, sortOrder: Int): Int = dbQuery {
        updateSortOrderInCurrentTransaction(id, sortOrder)
    }

    /**
     * 批量更新书架排序顺序
     * @param bookshelfIds 按新顺序排列的书架ID列表
     */
    fun reorderBookshelves(userId: Int, bookshelfIds: List<Int>): Int = transaction {
        reorderBookshelvesInCurrentTransaction(userId, bookshelfIds)
    }

    suspend fun reorderBookshelvesAsync(userId: Int, bookshelfIds: List<Int>): Int = dbQuery {
        reorderBookshelvesInCurrentTransaction(userId, bookshelfIds)
    }

    /**
     * 获取书架中的书籍数量
     */
    fun countBooks(bookshelfId: Int): Int = transaction {
        countBooksInCurrentTransaction(bookshelfId)
    }

    suspend fun countBooksAsync(bookshelfId: Int): Int = dbQuery {
        countBooksInCurrentTransaction(bookshelfId)
    }

    /**
     * 获取用户书架的最大排序值
     */
    fun getMaxSortOrder(userId: Int): Int = transaction {
        getMaxSortOrderInCurrentTransaction(userId)
    }

    suspend fun getMaxSortOrderAsync(userId: Int): Int = dbQuery {
        getMaxSortOrderInCurrentTransaction(userId)
    }

    private fun createInCurrentTransaction(
        userId: Int,
        name: String,
        description: String?,
        isSystemDefault: Boolean,
        sortOrder: Int
    ): Bookshelf {
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

        return Bookshelf(
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

    private fun findByIdInCurrentTransaction(id: Int): Bookshelf? {
        return Bookshelves.selectAll()
            .where { Bookshelves.id eq id }
            .map { toBookshelf(it) }
            .singleOrNull()
    }

    private fun findByUserIdInCurrentTransaction(userId: Int): List<Bookshelf> {
        return Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }

    private fun getDefaultBookshelfInCurrentTransaction(userId: Int): Bookshelf? {
        return Bookshelves.selectAll()
            .where { (Bookshelves.userId eq userId) and (Bookshelves.isSystemDefault eq true) }
            .map { toBookshelf(it) }
            .singleOrNull()
    }

    private fun findByUserIdAndNameInCurrentTransaction(userId: Int, name: String): Bookshelf? {
        return Bookshelves.selectAll()
            .where { (Bookshelves.userId eq userId) and (Bookshelves.name eq name) }
            .map { toBookshelf(it) }
            .singleOrNull()
    }

    private fun isSystemDefaultInCurrentTransaction(bookshelfId: Int): Boolean {
        return Bookshelves.selectAll()
            .where { Bookshelves.id eq bookshelfId }
            .map { it[Bookshelves.isSystemDefault] }
            .singleOrNull() ?: false
    }

    private fun updateInCurrentTransaction(id: Int, name: String?, description: String?): Bookshelf? {
        val now = TimeProvider.now()
        val updated = Bookshelves.update({ Bookshelves.id eq id }) {
            if (name != null) it[Bookshelves.name] = name
            if (description != null) it[Bookshelves.description] = description
            it[Bookshelves.updatedAt] = now
        }

        return if (updated > 0) {
            findByIdInCurrentTransaction(id)
        } else {
            null
        }
    }

    private fun deleteInCurrentTransaction(id: Int): Int {
        return Bookshelves.deleteWhere { Bookshelves.id eq id }
    }

    private fun updateSortOrderInCurrentTransaction(id: Int, sortOrder: Int): Int {
        val now = TimeProvider.now()
        return Bookshelves.update({ Bookshelves.id eq id }) {
            it[Bookshelves.sortOrder] = sortOrder
            it[Bookshelves.updatedAt] = now
        }
    }

    private fun reorderBookshelvesInCurrentTransaction(userId: Int, bookshelfIds: List<Int>): Int {
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

        return updatedCount
    }

    private fun countBooksInCurrentTransaction(bookshelfId: Int): Int {
        return BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .count()
            .toInt()
    }

    private fun getMaxSortOrderInCurrentTransaction(userId: Int): Int {
        return Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .maxOfOrNull { it[Bookshelves.sortOrder] } ?: 0
    }

    private fun toBookshelf(row: ResultRow) = Bookshelf(
        id = row[Bookshelves.id].value,
        userId = row[Bookshelves.userId].value,
        name = row[Bookshelves.name],
        description = row[Bookshelves.description],
        sortOrder = row[Bookshelves.sortOrder],
        bookCount = 0,
        isSystemDefault = row[Bookshelves.isSystemDefault],
        createdAt = row[Bookshelves.createdAt],
        updatedAt = row[Bookshelves.updatedAt]
    )
}
