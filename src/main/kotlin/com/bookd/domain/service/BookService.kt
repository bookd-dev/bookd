package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookDocumentStats
import com.bookd.domain.model.Book

class BookService(
    private val bookRepository: BookRepository,
    private val documentRepository: BookDocumentRepository
) {
    suspend fun getAllBooks(limit: Int = 100, offset: Long = 0): List<Book> {
        return enrichBooksWithStats(bookRepository.findAllAsync(limit, offset))
    }
    
    suspend fun getBookById(id: Int): Book? {
        val book = bookRepository.findByIdAsync(id) ?: return null
        return enrichBooksWithStats(listOf(book)).single()
    }
    
    suspend fun getBooksBySourceId(sourceId: Int): List<Book> {
        return enrichBooksWithStats(bookRepository.findBySourceIdAsync(sourceId))
    }

    suspend fun getBooksByIds(ids: List<Int>): List<Book> {
        if (ids.isEmpty()) return emptyList()
        val booksById = enrichBooksWithStats(bookRepository.findAllByIdAsync(ids)).associateBy { it.id }
        return ids.mapNotNull { booksById[it] }
    }
    
    /**
     * 根据书源 ID 分页获取书籍列表（APP 端专用）
     */
    suspend fun getBooksBySourceIdPaged(sourceId: Int, limit: Int, offset: Long): List<Book> {
        return enrichBooksWithStats(bookRepository.findBySourceIdPagedAsync(sourceId, limit, offset))
    }
    
    suspend fun getTotalCount(): Long {
        return bookRepository.countAsync()
    }
    
    suspend fun getCountBySourceId(sourceId: Int): Long {
        return bookRepository.countBySourceIdAsync(sourceId)
    }

    suspend fun getRawBookById(id: Int): Book? {
        return bookRepository.findByIdAsync(id)
    }

    suspend fun updateMetadata(
        id: Int,
        title: String? = null,
        author: String? = null,
        coverPath: String? = null,
        isbn: String? = null,
        publisher: String? = null,
        description: String? = null
    ): Book? {
        val updated = bookRepository.updateMetadataAsync(
            id = id,
            title = title?.trim(),
            author = author,
            coverPath = coverPath,
            isbn = isbn,
            publisher = publisher,
            description = description
        )
        return if (updated > 0) bookRepository.findByIdAsync(id) else null
    }

    suspend fun updateCoverPath(bookId: Int, coverPath: String?, width: Int? = null, height: Int? = null): Boolean {
        return bookRepository.updateCoverPathAsync(bookId, coverPath, width, height) > 0
    }
    
    fun scanAndImportBook(filePath: String): Book? {
        // Check if book already exists
        val existing = bookRepository.findByFilePath(filePath)
        if (existing != null) return enrichBookWithCurrentStats(existing)
        
        // TODO: Parse book metadata using Apache Tika
        // For now, create a basic entry
        val fileName = filePath.substringAfterLast("/")
        val format = fileName.substringAfterLast(".")
        
        return bookRepository.create(
            title = fileName.substringBeforeLast("."),
            author = null,
            format = format,
            filePath = filePath,
            fileSize = 0L // TODO: Get actual file size
        )
    }
    
    /**
     * 用文档统计信息丰富 Book 对象
     */
    private suspend fun enrichBooksWithStats(books: List<Book>): List<Book> {
        if (books.isEmpty()) return emptyList()
        val booksMissingStats = books.filter { it.statsUpdatedAt == null }
        val statsByBookId = if (booksMissingStats.isEmpty()) {
            emptyMap()
        } else {
            documentRepository.findStatsByBookIds(booksMissingStats.map { it.id })
        }
        return books.map { book -> enrichBookWithStats(book, statsByBookId[book.id]) }
    }

    private fun enrichBookWithStats(book: Book, stats: BookDocumentStats?): Book {
        // 计算封面宽高比
        val coverAspectRatio = if (book.coverWidth != null && book.coverHeight != null && book.coverHeight > 0) {
            book.coverWidth.toDouble() / book.coverHeight
        } else {
            null
        }
        
        return book.copy(
            chapterCount = stats?.chapterCount ?: book.chapterCount,
            totalWordCount = stats?.totalWordCount ?: book.totalWordCount,
            totalImageCount = stats?.totalImageCount ?: book.totalImageCount,
            coverAspectRatio = coverAspectRatio
        )
    }

    private fun enrichBookWithCurrentStats(book: Book): Book {
        val documents = documentRepository.findByBookId(book.id)
        val stats = BookDocumentStats(
            chapterCount = documents.count { it.inToc },
            totalWordCount = documents.sumOf { it.wordCount },
            totalImageCount = documents.sumOf { it.imageCount }
        )
        return enrichBookWithStats(book, stats)
    }
}
