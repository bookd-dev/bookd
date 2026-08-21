package com.bookd.domain.service

import com.bookd.data.repository.BookSourceRepository
import com.bookd.domain.model.BookSource

class BookSourceService(
    private val bookSourceRepository: BookSourceRepository
) {
    suspend fun getAllSources(): List<BookSource> {
        return bookSourceRepository.findAllAsync()
    }
    
    suspend fun getSourceById(id: Int): BookSource? {
        return bookSourceRepository.findByIdAsync(id)
    }
    
    suspend fun createSource(name: String, path: String): BookSource {
        return bookSourceRepository.createAsync(name, path)
    }
    
    suspend fun deleteSource(id: Int): Boolean {
        return bookSourceRepository.deleteAsync(id)
    }
    
    suspend fun toggleSource(id: Int): Boolean {
        return bookSourceRepository.toggleEnabledAsync(id)
    }
}
