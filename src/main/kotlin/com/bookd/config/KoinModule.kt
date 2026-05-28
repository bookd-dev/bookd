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
import com.bookd.data.repository.ImageDimensionMigrationRepository
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
import com.bookd.domain.service.BookTaskCoordinator
import com.bookd.domain.service.FileSystemService
import com.bookd.domain.service.parser.TxtParser
import com.bookd.infrastructure.cache.RedisService
import com.bookd.infrastructure.cache.BookCacheService
import com.bookd.infrastructure.lifecycle.BackendLifecycleService
import com.bookd.infrastructure.storage.BookImageStorage
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("KoinModule")

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
    single { ImageDimensionMigrationRepository() }
    single { BookTaskCoordinator() }
    
    // Storage
    single { BookImageStorage() }
    
    // Redis (optional, graceful degradation if not available)
    single<RedisService?> { createRedisServiceOrNull() }
    
    single<BookCacheService> { BookCacheService(getOrNull()) }
    
    // Services
    single { UserService(get(), get()) }
    single { BookService(get(), get(), get(), get()) }
    single { BookSourceService(get()) }
    single { CoverGeneratorService(get()) }
    single { TxtParseRuleService(get()) }
    single { TxtParser(get()) }
    single { BookContentService(get(), get(), get(), get(), get(), getOrNull<BookCacheService>(), get()) }
    single { BookMetadataService(get(), get(), get(), get()) }
    single { BookScanService(get(), get(), get(), get()) }
    single { TagService(get(), get()) }
    single { ReadingService(get(), get(), get()) }
    single { BackgroundParseService(get(), get()) }
    single { BookshelfService(get(), get(), get(), get()) }
    single { BookDetailService(get(), get(), get(), get()) }
    single { ImageDimensionMigrationService(get(), get()) }
    single { FileSystemService() }
    single { BackendLifecycleService(get(), get(), getOrNull()) }
}

internal fun createRedisServiceOrNull(
    redisHost: String = System.getenv("REDIS_HOST") ?: "localhost",
    redisPort: Int = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379,
    redisPassword: String? = System.getenv("REDIS_PASSWORD")?.takeIf { it.isNotBlank() },
    redisDatabase: Int = System.getenv("REDIS_DATABASE")?.toIntOrNull() ?: 0,
    redisEnabled: Boolean = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false,
    redisFactory: (String, Int, String?, Int) -> RedisService = ::RedisService
): RedisService? {
    if (!redisEnabled) {
        logger.info("Redis disabled, running without cache")
        return null
    }

    return try {
        val redis = redisFactory(redisHost, redisPort, redisPassword, redisDatabase)
        val connected = try {
            redis.ping()
        } catch (e: Exception) {
            logger.warn("Redis connection test failed: ${e.message}, running without cache")
            false
        }

        if (connected) {
            logger.info("Redis connected: $redisHost:$redisPort")
            redis
        } else {
            redis.close()
            logger.warn("Redis connection test failed, running without cache")
            null
        }
    } catch (e: Exception) {
        logger.warn("Redis initialization failed: ${e.message}, running without cache")
        null
    }
}
