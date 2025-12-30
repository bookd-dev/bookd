package com.bookd.config

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.data.repository.UserRepository
import com.bookd.data.repository.TagRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.data.repository.BookmarkRepository
import com.bookd.data.repository.ReaderSettingsRepository
import com.bookd.domain.service.BookMetadataService
import com.bookd.domain.service.BookScanService
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.domain.service.UserService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.domain.service.TagService
import com.bookd.domain.service.ReadingService
import org.koin.dsl.module

val appModule = module {
    // Repositories
    single { UserRepository() }
    single { BookRepository() }
    single { BookSourceRepository() }
    single { TagRepository() }
    single { ReadingProgressRepository() }
    single { BookmarkRepository() }
    single { ReaderSettingsRepository() }
    
    // Services
    single { UserService(get()) }
    single { BookService(get()) }
    single { BookSourceService(get()) }
    single { CoverGeneratorService() }
    single { BookMetadataService(get(), get()) }
    single { BookScanService(get(), get(), get()) }
    single { TagService(get(), get()) }
    single { ReadingService(get(), get(), get()) }
}
