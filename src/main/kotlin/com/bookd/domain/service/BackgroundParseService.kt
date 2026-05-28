package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timer

/**
 * 后台章节解析服务
 * 定期从数据库获取未解析的书籍，放入队列慢慢解析
 */
class BackgroundParseService(
    private val bookRepository: BookRepository,
    private val contentService: BookContentService,
    private val environment: (String) -> String? = System::getenv
) {
    private val logger = LoggerFactory.getLogger(BackgroundParseService::class.java)
    
    private val isRunning = AtomicBoolean(false)
    private var parseDispatcher: ExecutorCoroutineDispatcher? = null
    private var scope: CoroutineScope? = null
    
    private var scheduledTimer: java.util.Timer? = null
    
    // 配置参数
    private val intervalSeconds = readLongConfig("PARSE_BACKGROUND_INTERVAL", "BACKGROUND_PARSE_INTERVAL", 60L)
    private val batchSize = readIntConfig("PARSE_BACKGROUND_BATCH_SIZE", "BACKGROUND_PARSE_BATCH_SIZE", 5)
    private val enabled = readBooleanConfig("PARSE_BACKGROUND_ENABLED", "BACKGROUND_PARSE_ENABLED", true)
    
    /**
     * 启动后台解析服务
     */
    fun start() {
        if (!enabled) {
            logger.info("Background parse service is disabled")
            return
        }
        
        if (isRunning.get()) {
            logger.warn("Background parse service is already running")
            return
        }
        
        ensureScope()
        isRunning.set(true)
        logger.info("Starting background parse service (interval: ${intervalSeconds}s, batch: $batchSize)")
        
        // 启动定时任务
        scheduledTimer = timer(
            name = "BackgroundParseTimer",
            daemon = true,
            initialDelay = 10000L, // 10秒后开始第一次
            period = intervalSeconds * 1000L
        ) {
            if (isRunning.get()) {
                processUnparsedBooks()
            }
        }
    }
    
    /**
     * 停止后台解析服务
     */
    fun stop() {
        val hadResources = scheduledTimer != null || scope != null || parseDispatcher != null
        val wasRunning = isRunning.getAndSet(false)
        if (!wasRunning && !hadResources) {
            logger.debug("Background parse service is already stopped")
            return
        }

        logger.info("Stopping background parse service...")
        val timer = scheduledTimer
        scheduledTimer = null

        val currentScope = scope
        scope = null

        val dispatcher = parseDispatcher
        parseDispatcher = null

        timer?.cancel()
        currentScope?.cancel()
        dispatcher?.close()
        
        logger.info("Background parse service stopped")
    }
    
    /**
     * 处理未解析的书籍
     */
    private fun processUnparsedBooks() {
        val currentScope = scope ?: ensureScope()
        currentScope.launch {
            try {
                // 从数据库获取未解析的书籍
                val unparsedBooks = bookRepository.findUnparsedBooks(batchSize)
                
                if (unparsedBooks.isEmpty()) {
                    logger.debug("No unparsed books found")
                    return@launch
                }
                
                logger.info("Found ${unparsedBooks.size} unparsed books, starting background parsing...")
                
                unparsedBooks.forEach { book ->
                    if (!isRunning.get()) {
                        logger.info("Background parse service stopped, aborting")
                        return@forEach
                    }
                    
                    try {
                        logger.info("Background parsing book: ${book.title} (ID: ${book.id})")
                        contentService.parseBookContentAsync(book.id, book.filePath)
                        
                        // 间隔一段时间，避免资源占用过高
                        delay(5000)
                    } catch (e: Exception) {
                        logger.error("Failed to parse book in background: ${book.title}", e)
                    }
                }
                
                logger.info("Background parsing batch completed")
            } catch (e: Exception) {
                logger.error("Error in background parse task", e)
            }
        }
    }

    private fun ensureScope(): CoroutineScope {
        val existing = scope
        if (existing != null && existing.isActive) {
            return existing
        }

        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        parseDispatcher = dispatcher
        return CoroutineScope(dispatcher + SupervisorJob()).also { scope = it }
    }

    private fun readLongConfig(primaryName: String, fallbackName: String, defaultValue: Long): Long {
        return environment(primaryName)?.toLongOrNull()
            ?: environment(fallbackName)?.toLongOrNull()
            ?: defaultValue
    }

    private fun readIntConfig(primaryName: String, fallbackName: String, defaultValue: Int): Int {
        return environment(primaryName)?.toIntOrNull()
            ?: environment(fallbackName)?.toIntOrNull()
            ?: defaultValue
    }

    private fun readBooleanConfig(primaryName: String, fallbackName: String, defaultValue: Boolean): Boolean {
        return environment(primaryName)?.parseBoolean()
            ?: environment(fallbackName)?.parseBoolean()
            ?: defaultValue
    }

    private fun String.parseBoolean(): Boolean? = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
    
    /**
     * 获取服务状态
     */
    suspend fun getStatus(): BackgroundParseStatus {
        val unparsedCount = try {
            bookRepository.findUnparsedBooksAsync(1000).size
        } catch (e: Exception) {
            -1
        }
        
        return BackgroundParseStatus(
            running = isRunning.get(),
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            batchSize = batchSize,
            unparsedBooksCount = unparsedCount
        )
    }
    
    @Serializable
    data class BackgroundParseStatus(
        val running: Boolean,
        val enabled: Boolean,
        val intervalSeconds: Long,
        val batchSize: Int,
        val unparsedBooksCount: Int
    )
}
