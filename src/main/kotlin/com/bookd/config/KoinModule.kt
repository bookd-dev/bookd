package com.bookd.config

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.BookSourceRepository
import com.bookd.data.repository.UserRepository
import com.bookd.data.repository.TagRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.data.repository.BookmarkRepository
import com.bookd.data.repository.ReaderSettingsRepository
import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.TxtParseRuleRepository
import com.bookd.data.repository.BookshelfRepository
import com.bookd.data.repository.BookshelfItemRepository
import com.bookd.domain.service.BookMetadataService
import com.bookd.domain.service.BookScanService
import com.bookd.domain.service.BookService
import com.bookd.domain.service.BookSourceService
import com.bookd.domain.service.UserService
import com.bookd.domain.service.CoverGeneratorService
import com.bookd.domain.service.TagService
import com.bookd.domain.service.ReadingService
import com.bookd.domain.service.BookContentService
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.domain.service.BackgroundParseService
import com.bookd.domain.service.BookshelfService
import com.bookd.domain.service.BookDetailService
import com.bookd.domain.service.ImageDimensionMigrationService
import com.bookd.domain.service.parser.TxtParser
import com.bookd.domain.service.metadata.EbookParserClient
import com.bookd.infrastructure.cache.RedisService
import com.bookd.infrastructure.cache.BookCacheService
import com.bookd.infrastructure.storage.BookImageStorage
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
    single { BookDocumentRepository() }
    single { TxtParseRuleRepository() }
    single { BookshelfRepository() }
    single { BookshelfItemRepository() }
    
    // Storage
    single { BookImageStorage() }
    
    // Redis (optional, graceful degradation if not available)
    single<RedisService?> {
        try {
            val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
            val redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
            val redisPassword = System.getenv("REDIS_PASSWORD")?.takeIf { it.isNotBlank() }
            val redisDatabase = System.getenv("REDIS_DATABASE")?.toIntOrNull() ?: 0
            val redisEnabled = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false
            
            if (redisEnabled) {
                val redis = RedisService(redisHost, redisPort, redisPassword, redisDatabase)
                try {
                    redis.ping()
                    println("✅ Redis connected: $redisHost:$redisPort")
                    redis
                } catch (e: Exception) {
                    println("⚠️  Redis connection test failed: ${e.message}, running without cache")
                    null
                }
            } else {
                println("ℹ️  Redis disabled, running without cache")
                null
            }
        } catch (e: Exception) {
            println("⚠️  Redis initialization failed: ${e.message}, running without cache")
            null
        }
    }
    
    single<BookCacheService?> {
        val redis = getOrNull<RedisService>()
        if (redis != null) {
            BookCacheService(redis)
        } else {
            null
        }
    }
    
    // eBook Parser 微服务配置
    single { EbookParserConfig() }
    single<EbookParserClient?> {
        try {
            val config = get<EbookParserConfig>()
            if (config.enabled) {
                val client = EbookParserClient(config)
                println("✅ eBook Parser Service enabled: ${config.serviceUrl}")
                client
            } else {
                println("ℹ️  eBook Parser Service disabled, using local parser")
                null
            }
        } catch (e: Exception) {
            println("⚠️  eBook Parser Service initialization failed: ${e.message}, using local parser")
            null
        }
    }
    
    // Services
    single { UserService(get()) }
    single { BookService(get(), get()) }
    single { BookSourceService(get()) }
    single { CoverGeneratorService(get()) }
    single { TxtParseRuleService(get()) }
    single { TxtParser(get()) }
    single { BookContentService(get(), get(), get(), get(), get(), getOrNull<BookCacheService>()) }
    single { BookMetadataService(get(), get(), get(), getOrNull(), get()) }
    single { BookScanService(get(), get(), get(), get()) }
    single { TagService(get(), get(), getOrNull(), get()) }
    single { ReadingService(get(), get(), get()) }
    single { BackgroundParseService(get(), get()) }
    single { BookshelfService(get(), get(), get(), get()) }
    single { BookDetailService(get(), get(), get(), get()) }
    single { ImageDimensionMigrationService(get()) }
}
