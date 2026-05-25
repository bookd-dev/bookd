package com.bookd.data.repository

import com.bookd.data.entity.Books
import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Bookshelves
import com.bookd.data.entity.ReadingProgress
import com.bookd.domain.model.Book
import com.bookd.domain.model.BookWithProgress
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.BookshelfItem
import com.bookd.domain.model.BookshelfMembershipSummary
import com.bookd.domain.model.ReadingProgressResponse
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
        addBookInCurrentTransaction(bookshelfId, bookId)
    }

    suspend fun addBookAsync(bookshelfId: Int, bookId: Int): BookshelfItem? = dbQuery {
        addBookInCurrentTransaction(bookshelfId, bookId)
    }

    /**
     * 从书架移除书籍
     * @return 删除的记录数
     */
    fun removeBook(bookshelfId: Int, bookId: Int): Int = transaction {
        removeBookInCurrentTransaction(bookshelfId, bookId)
    }

    suspend fun removeBookAsync(bookshelfId: Int, bookId: Int): Int = dbQuery {
        removeBookInCurrentTransaction(bookshelfId, bookId)
    }

    /**
     * 从用户的所有书架中移除书籍
     * 用于从"全部"书架移除时的级联删除
     * @return 删除的记录数
     */
    fun removeBookFromAllUserBookshelves(userId: Int, bookId: Int): Int = transaction {
        removeBookFromAllUserBookshelvesInCurrentTransaction(userId, bookId)
    }

    suspend fun removeBookFromAllUserBookshelvesAsync(userId: Int, bookId: Int): Int = dbQuery {
        removeBookFromAllUserBookshelvesInCurrentTransaction(userId, bookId)
    }

    /**
     * 查询书架中的所有书籍(分页)
     */
    fun findByBookshelf(bookshelfId: Int, limit: Int = 20, offset: Long = 0): List<Book> = transaction {
        findByBookshelfInCurrentTransaction(bookshelfId, limit, offset)
    }

    suspend fun findByBookshelfAsync(bookshelfId: Int, limit: Int = 20, offset: Long = 0): List<Book> = dbQuery {
        findByBookshelfInCurrentTransaction(bookshelfId, limit, offset)
    }

    /**
     * 获取书架中的书籍ID列表（按添加时间排序）
     * 用于后续按阅读进度排序
     *
     * @param bookshelfId 书架ID
     * @param limit 分页大小，如果需要获取所有ID可设置为 Int.MAX_VALUE
     * @param offset 偏移量
     * @return 书籍ID列表
     */
    fun findBookIdsByBookshelf(bookshelfId: Int, limit: Int = 20, offset: Long = 0): List<Int> = transaction {
        findBookIdsByBookshelfInCurrentTransaction(bookshelfId, limit, offset)
    }

    suspend fun findBookIdsByBookshelfAsync(bookshelfId: Int, limit: Int = 20, offset: Long = 0): List<Int> = dbQuery {
        findBookIdsByBookshelfInCurrentTransaction(bookshelfId, limit, offset)
    }

    fun findBooksWithProgressByBookshelf(
        userId: Int,
        bookshelfId: Int,
        limit: Int = 20,
        offset: Long = 0
    ): BookshelfBooksPage = transaction {
        findBooksWithProgressByBookshelfInCurrentTransaction(userId, bookshelfId, limit, offset)
    }

    suspend fun findBooksWithProgressByBookshelfAsync(
        userId: Int,
        bookshelfId: Int,
        limit: Int = 20,
        offset: Long = 0
    ): BookshelfBooksPage = dbQuery {
        findBooksWithProgressByBookshelfInCurrentTransaction(userId, bookshelfId, limit, offset)
    }

    /**
     * 查询书籍所在的所有书架
     */
    fun findBookshelvesForBook(bookId: Int): List<Bookshelf> = transaction {
        findBookshelvesForBookInCurrentTransaction(bookId)
    }

    suspend fun findBookshelvesForBookAsync(bookId: Int): List<Bookshelf> = dbQuery {
        findBookshelvesForBookInCurrentTransaction(bookId)
    }

    /**
     * 查询用户书籍所在的所有书架
     */
    fun findUserBookshelvesForBook(userId: Int, bookId: Int): List<Bookshelf> = transaction {
        findUserBookshelvesForBookInCurrentTransaction(userId, bookId)
    }

    suspend fun findUserBookshelvesForBookAsync(userId: Int, bookId: Int): List<Bookshelf> = dbQuery {
        findUserBookshelvesForBookInCurrentTransaction(userId, bookId)
    }

    fun findUserBookshelfMembershipSummary(userId: Int, bookId: Int): BookshelfMembershipSummary = transaction {
        findUserBookshelfMembershipSummaryInCurrentTransaction(userId, bookId)
    }

    suspend fun findUserBookshelfMembershipSummaryAsync(userId: Int, bookId: Int): BookshelfMembershipSummary = dbQuery {
        findUserBookshelfMembershipSummaryInCurrentTransaction(userId, bookId)
    }

    /**
     * 检查书籍是否在书架中
     */
    fun isBookInBookshelf(bookshelfId: Int, bookId: Int): Boolean = transaction {
        isBookInBookshelfInCurrentTransaction(bookshelfId, bookId)
    }

    suspend fun isBookInBookshelfAsync(bookshelfId: Int, bookId: Int): Boolean = dbQuery {
        isBookInBookshelfInCurrentTransaction(bookshelfId, bookId)
    }

    /**
     * 统计书架中的书籍数量
     */
    fun countByBookshelf(bookshelfId: Int): Long = transaction {
        countByBookshelfInCurrentTransaction(bookshelfId)
    }

    suspend fun countByBookshelfAsync(bookshelfId: Int): Long = dbQuery {
        countByBookshelfInCurrentTransaction(bookshelfId)
    }

    fun countByBookshelfIds(bookshelfIds: List<Int>): Map<Int, Long> = transaction {
        countByBookshelfIdsInCurrentTransaction(bookshelfIds)
    }

    suspend fun countByBookshelfIdsAsync(bookshelfIds: List<Int>): Map<Int, Long> = dbQuery {
        countByBookshelfIdsInCurrentTransaction(bookshelfIds)
    }

    /**
     * 获取书架关联项
     */
    fun findByBookshelfAndBook(bookshelfId: Int, bookId: Int): BookshelfItem? = transaction {
        findByBookshelfAndBookInCurrentTransaction(bookshelfId, bookId)
    }

    suspend fun findByBookshelfAndBookAsync(bookshelfId: Int, bookId: Int): BookshelfItem? = dbQuery {
        findByBookshelfAndBookInCurrentTransaction(bookshelfId, bookId)
    }

    private fun addBookInCurrentTransaction(bookshelfId: Int, bookId: Int): BookshelfItem? {
        val existing = BookshelfItems.selectAll()
            .where { (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) }
            .count()

        if (existing > 0) {
            return null
        }

        val now = TimeProvider.now()
        val id = BookshelfItems.insert {
            it[BookshelfItems.bookshelfId] = bookshelfId
            it[BookshelfItems.bookId] = bookId
            it[BookshelfItems.addedAt] = now
        }[BookshelfItems.id]

        return BookshelfItem(
            id = id.value,
            bookshelfId = bookshelfId,
            bookId = bookId,
            addedAt = now
        )
    }

    private fun removeBookInCurrentTransaction(bookshelfId: Int, bookId: Int): Int {
        return BookshelfItems.deleteWhere {
            (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId)
        }
    }

    private fun removeBookFromAllUserBookshelvesInCurrentTransaction(userId: Int, bookId: Int): Int {
        val userBookshelfIds = Bookshelves.selectAll()
            .where { Bookshelves.userId eq userId }
            .map { it[Bookshelves.id].value }

        if (userBookshelfIds.isEmpty()) {
            return 0
        }

        return BookshelfItems.deleteWhere {
            (BookshelfItems.bookshelfId inList userBookshelfIds) and (BookshelfItems.bookId eq bookId)
        }
    }

    private fun findByBookshelfInCurrentTransaction(bookshelfId: Int, limit: Int, offset: Long): List<Book> {
        return (BookshelfItems innerJoin Books)
            .selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .orderBy(BookshelfItems.addedAt to SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { toBook(it) }
    }

    private fun findBookIdsByBookshelfInCurrentTransaction(bookshelfId: Int, limit: Int, offset: Long): List<Int> {
        return BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .orderBy(BookshelfItems.addedAt to SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it[BookshelfItems.bookId].value }
    }

    private fun findBooksWithProgressByBookshelfInCurrentTransaction(
        userId: Int,
        bookshelfId: Int,
        limit: Int,
        offset: Long
    ): BookshelfBooksPage {
        val total = BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .count()
            .toInt()

        if (total == 0) {
            return BookshelfBooksPage(emptyList(), total)
        }

        val joined = (BookshelfItems innerJoin Books).join(
            ReadingProgress,
            JoinType.LEFT,
            BookshelfItems.bookId,
            ReadingProgress.bookId
        ) {
            ReadingProgress.userId eq userId
        }

        val books = joined
            .selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .orderBy(
                ReadingProgress.lastReadAt to SortOrder.DESC_NULLS_LAST,
                BookshelfItems.addedAt to SortOrder.DESC
            )
            .limit(limit)
            .offset(offset)
            .map { row ->
                BookWithProgress(
                    book = toBook(row),
                    progress = toProgressOrNull(row)
                )
            }

        return BookshelfBooksPage(books, total)
    }

    private fun findBookshelvesForBookInCurrentTransaction(bookId: Int): List<Bookshelf> {
        return (BookshelfItems innerJoin Bookshelves)
            .selectAll()
            .where { BookshelfItems.bookId eq bookId }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }

    private fun findUserBookshelvesForBookInCurrentTransaction(userId: Int, bookId: Int): List<Bookshelf> {
        return (BookshelfItems innerJoin Bookshelves)
            .selectAll()
            .where {
                (BookshelfItems.bookId eq bookId) and (Bookshelves.userId eq userId)
            }
            .orderBy(Bookshelves.sortOrder to SortOrder.ASC)
            .map { toBookshelf(it) }
    }

    private fun findUserBookshelfMembershipSummaryInCurrentTransaction(
        userId: Int,
        bookId: Int
    ): BookshelfMembershipSummary {
        val bookshelves = findUserBookshelvesForBookInCurrentTransaction(userId, bookId)

        if (bookshelves.isEmpty()) {
            return BookshelfMembershipSummary(emptyList(), inDefaultBookshelf = false)
        }

        val countsByBookshelfId = countByBookshelfIdsInCurrentTransaction(bookshelves.map { it.id })
        return BookshelfMembershipSummary(
            bookshelves = bookshelves.map { bookshelf ->
                bookshelf.copy(bookCount = countsByBookshelfId[bookshelf.id]?.toInt() ?: 0)
            },
            inDefaultBookshelf = bookshelves.any { it.isSystemDefault }
        )
    }

    private fun isBookInBookshelfInCurrentTransaction(bookshelfId: Int, bookId: Int): Boolean {
        return BookshelfItems.selectAll()
            .where { (BookshelfItems.bookshelfId eq bookshelfId) and (BookshelfItems.bookId eq bookId) }
            .count() > 0
    }

    private fun countByBookshelfInCurrentTransaction(bookshelfId: Int): Long {
        return BookshelfItems.selectAll()
            .where { BookshelfItems.bookshelfId eq bookshelfId }
            .count()
    }

    private fun findByBookshelfAndBookInCurrentTransaction(bookshelfId: Int, bookId: Int): BookshelfItem? {
        return BookshelfItems.selectAll()
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
        coverWidth = row[Books.coverWidth],
        coverHeight = row[Books.coverHeight],
        fileSize = row[Books.fileSize],
        isbn = row[Books.isbn],
        publisher = row[Books.publisher],
        description = row[Books.description],
        sourceId = row[Books.sourceId]?.value,
        chapterCount = row[Books.tocChapterCount] ?: 0,
        totalWordCount = row[Books.totalWordCount] ?: 0,
        totalImageCount = row[Books.totalImageCount] ?: 0,
        chaptersParsed = row[Books.chaptersParsed],
        chaptersCount = row[Books.chaptersCount],
        lastParsedAt = row[Books.lastParsedAt],
        parseStatus = row[Books.parseStatus],
        parseProgress = row[Books.parseProgress],
        createdAt = row[Books.createdAt],
        updatedAt = row[Books.updatedAt],
        statsUpdatedAt = row[Books.statsUpdatedAt]
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

    private fun countByBookshelfIdsInCurrentTransaction(bookshelfIds: List<Int>): Map<Int, Long> {
        if (bookshelfIds.isEmpty()) return emptyMap()

        val countExpression = BookshelfItems.id.count()
        return BookshelfItems
            .select(BookshelfItems.bookshelfId, countExpression)
            .where { BookshelfItems.bookshelfId inList bookshelfIds }
            .groupBy(BookshelfItems.bookshelfId)
            .associate { row ->
                row[BookshelfItems.bookshelfId].value to row[countExpression]
            }
    }

    private fun toProgressOrNull(row: ResultRow): ReadingProgressResponse? {
        row.getOrNull(ReadingProgress.id) ?: return null
        return ReadingProgressResponse(
            id = row[ReadingProgress.id].value,
            bookId = row[ReadingProgress.bookId].value,
            progress = row[ReadingProgress.progress].toDouble(),
            currentPage = row[ReadingProgress.currentPage],
            totalPages = row[ReadingProgress.totalPages],
            cfiLocation = row[ReadingProgress.cfiLocation],
            documentId = row[ReadingProgress.documentId],
            deviceId = row[ReadingProgress.deviceId],
            lastReadAt = row[ReadingProgress.lastReadAt],
            chapterPageIndex = row[ReadingProgress.chapterPageIndex],
            chapterTotalPages = row[ReadingProgress.chapterTotalPages],
            chapterScrollPercent = row[ReadingProgress.chapterScrollPercent]?.toDouble()
        )
    }
}

data class BookshelfBooksPage(
    val books: List<BookWithProgress>,
    val total: Int
)
