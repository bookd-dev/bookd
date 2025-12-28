package com.bookd.domain.service

import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.BookSource

class BookSourceService(
    private val bookSourceRepository: BookSourceRepository
) {
    fun getAllSources(): List<BookSource> {
        return bookSourceRepository.findAll()
    }
    
    fun getSourceById(id: Int): BookSource? {
        return bookSourceRepository.findById(id)
    }
    
    fun createSource(name: String, path: String): BookSource {
        return bookSourceRepository.create(name, path)
    }
    
    fun deleteSource(id: Int): Boolean {
        return bookSourceRepository.delete(id)
    }
    
    fun toggleSource(id: Int): Boolean {
        return bookSourceRepository.toggleEnabled(id)
    }
}
