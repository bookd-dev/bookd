package com.bookd.data.repository

import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Books
import com.bookd.data.entity.Bookshelves
import com.bookd.domain.model.Book
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.BookshelfItem
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 书架项目(书架-书籍关联)数据仓库
 * 
 * 负责管理书架与书籍的关联关系
 */
class BookshelfItemRepository {
    
    /**
     * 添加书籍到书架
     * @return 创建的关联项,如果已存在则返回 null
     */
    fun addBook(bookshelfId: Int, bookId: Int): BookshelfItem? = transaction {
        // 检查是否已存在
        val existing = BookshelfItems.selectAll()
            .where { (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) }
            .count()
        
        if (existing > 0) {
            // 已存在,返回 null
            null
        } else {
            val now = TimeProvider.now()
            val id = BookshelfItems.insert {
                it[BookshelfItems.bookshelfId] = bookshelfId
                it[BookshelfItems.bookId] = bookId
                it[BookshelfItems.addedAt] = now
            }[BookshelfItems.id]
            
            BookshelfItem(
                id = id.value,
                bookshelfId = bookshelfId,
                bookId = bookId,
                addedAt = now
            )
        }
    }
    
    /**
     * 从书架移除书籍
     * @return 删除的记录数
     */
    fun removeBook(bookshelfId: Int, bookId: Int): Int = transaction {
        BookshelfItems.deleteWhere { 
            (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) 
        }
    }
    
    /**
     * 从用户的所有书架中移除书籍
     * 用于从"全部"书架移除时的级联删除
     * @return 删除的记录数
     */
    fun removeBookFromAllUserBookshelves(userId: Int, bookId: Int): Int = transaction {
        // 先获取用户的所有书架 ID
        val userBookshelfIds = Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .map { it[Bookshelves.id].value }
        
        if (userBookshelfIds.isEmpty()) {
            return@transaction 0
        }
        
        // 删除这些书架中该书籍的所有关联
        BookshelfItems.deleteWhere { 
            (BookshelfItems.bookshelfId inList userBookshelfIds) and (BookshelfItems.bookId eq bookId) 
        }
    }
    
    /**
     * 查询书架中的所有书籍(分页)
     */
    fun findByBookshelf(bookshelfId: Int, limit: Int = 20, offset: Long = 0): List<Book> = transaction {
        (BookshelfItems innerJoin Books)
            .selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .orderBy(BookshelfItems.addedAt to SortOrder.DESC)
            .limit(limit, offset)
            .map { toBook(it) }
    }
    
    /**
     * 查询书籍所在的所有书架
     */
    fun findBookshelvesForBook(bookId: Int): List<Bookshelf> = transaction {
        (BookshelfItems innerJoin Bookshelves)
            .selectAll()
            .where { BookshelfItems.bookId eq bookId }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }
    
    /**
     * 查询用户书籍所在的所有书架
     */
    fun findUserBookshelvesForBook(userId: Int, bookId: Int): List<Bookshelf> = transaction {
        (BookshelfItems innerJoin Bookshelves)
            .selectAll()
            .where { 
                (BookshelfItems.bookId eq bookId) and (Bookshelves.userId eq userId)
            }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }
    
    /**
     * 检查书籍是否在书架中
     */
    fun isBookInBookshelf(bookshelfId: Int, bookId: Int): Boolean = transaction {
        BookshelfItems.selectAll()
            .where { (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) }
            .count() > 0
    }
    
    /**
     * 统计书架中的书籍数量
     */
    fun countByBookshelf(bookshelfId: Int): Long = transaction {
        BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .count()
    }
    
    /**
     * 获取书架关联项
     */
    fun findByBookshelfAndBook(bookshelfId: Int, bookId: Int): BookshelfItem? = transaction {
        BookshelfItems.selectAll()
            .where { (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) }
            .map { toBookshelfItem(it) }
            .singleOrNull()
    }
    
    private fun toBook(row: ResultRow) = Book(
        id = row[Books.id].value,
        title = row[Books.title],
        author = row[Books.author],
        format = row[Books.format],
        filePath = row[Books.filePath],
        coverPath = row[Books.coverPath],
        fileSize = row[Books.fileSize],
        isbn = row[Books.isbn],
        publisher = row[Books.publisher],
        description = row[Books.description],
        sourceId = row[Books.sourceId]?.value,
        chaptersParsed = row[Books.chaptersParsed],
        chaptersCount = row[Books.chaptersCount],
        lastParsedAt = row[Books.lastParsedAt],
        parseStatus = row[Books.parseStatus],
        parseProgress = row[Books.parseProgress],
        createdAt = row[Books.createdAt],
        updatedAt = row[Books.updatedAt]
    )
    
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
    
    private fun toBookshelfItem(row: ResultRow) = BookshelfItem(
        id = row[BookshelfItems.id].value,
        bookshelfId = row[BookshelfItems.bookshelfId].value,
        bookId = row[BookshelfItems.bookId].value,
        addedAt = row[BookshelfItems.addedAt]
    )
}
