package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.Book

class BookService(
    private val bookRepository: BookRepository
) {
    fun getAllBooks(limit: Int = 100, offset: Long = 0): List<Book> {
        return bookRepository.findAll(limit, offset)
    }
    
    fun getBookById(id: Int): Book? {
        return bookRepository.findById(id)
    }
    
    fun scanAndImportBook(filePath: String): Book? {
        // Check if book already exists
        val existing = bookRepository.findByFilePath(filePath)
        if (existing != null) return existing
        
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
}
