package com.bookd.config

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.data.repository.UserRepository
import com.bookd.domain.service.BookScanService
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.domain.service.UserService
import org.koin.dsl.module

val appModule = module {
    // Repositories
    single { UserRepository() }
    single { BookRepository() }
    single { BookSourceRepository() }
    
    // Services
    single { UserService(get()) }
    single { BookService(get()) }
    single { BookSourceService(get()) }
    single { BookScanService(get(), get()) }
}
