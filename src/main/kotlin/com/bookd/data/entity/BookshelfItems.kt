package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

/**
 * 书架-书籍关联表
 * 
 * 用于存储书架与书籍的多对多关系。
 * 一本书可以同时存在于多个书架中。
 * 
 * 级联删除规则:
 * - 删除书架时,自动删除该书架中的所有关联记录
 * - 删除书籍时,自动删除该书籍在所有书架中的关联记录
 */
object BookshelfItems : IntIdTable("bookshelf_items") {
    val bookshelfId = reference("bookshelf_id", Bookshelves, onDelete = ReferenceOption.CASCADE)
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val addedAt = datetime("added_at")
    
    init {
        // 确保同一本书在同一个书架中只能出现一次
        uniqueIndex(bookshelfId, bookId)
    }
}
