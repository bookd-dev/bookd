package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookDocumentRepository
import com.bookd.domain.model.Book

class BookService(
    private val bookRepository: BookRepository,
    private val documentRepository: BookDocumentRepository
) {
    fun getAllBooks(limit: Int = 100, offset: Long = 0): List<Book> {
        return bookRepository.findAll(limit, offset).map { enrichBookWithStats(it) }
    }
    
    fun getBookById(id: Int): Book? {
        val book = bookRepository.findById(id) ?: return null
        return enrichBookWithStats(book)
    }
    
    fun getBooksBySourceId(sourceId: Int): List<Book> {
        return bookRepository.findBySourceId(sourceId).map { enrichBookWithStats(it) }
    }
    
    fun getTotalCount(): Long {
        return bookRepository.count()
    }
    
    fun getCountBySourceId(sourceId: Int): Long {
        return bookRepository.countBySourceId(sourceId)
    }
    
    fun scanAndImportBook(filePath: String): Book? {
        // Check if book already exists
        val existing = bookRepository.findByFilePath(filePath)
        if (existing != null) return enrichBookWithStats(existing)
        
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
    private fun enrichBookWithStats(book: Book): Book {
        val documents = documentRepository.findByBookId(book.id)
        
        if (documents.isEmpty()) {
            return book
        }
        
        val totalWordCount = documents.sumOf { it.wordCount }
        val totalImageCount = documents.sumOf { it.imageCount }
        
        return book.copy(
            chapterCount = documents.count { it.inToc }, // 只统计目录项
            totalWordCount = totalWordCount,
            totalImageCount = totalImageCount
        )
    }
}
