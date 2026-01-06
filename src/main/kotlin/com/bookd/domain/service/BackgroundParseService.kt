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
    private val contentService: BookContentService
) {
    private val logger = LoggerFactory.getLogger(BackgroundParseService::class.java)
    
    private val isRunning = AtomicBoolean(false)
    private val parseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(parseDispatcher + SupervisorJob())
    
    private var scheduledTimer: java.util.Timer? = null
    
    // 配置参数
    private val intervalSeconds = System.getenv("BACKGROUND_PARSE_INTERVAL")?.toLongOrNull() ?: 60L
    private val batchSize = System.getenv("BACKGROUND_PARSE_BATCH_SIZE")?.toIntOrNull() ?: 5
    private val enabled = System.getenv("BACKGROUND_PARSE_ENABLED")?.toBoolean() ?: true
    
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
        if (!isRunning.get()) {
            logger.warn("Background parse service is not running")
            return
        }
        
        logger.info("Stopping background parse service...")
        isRunning.set(false)
        scheduledTimer?.cancel()
        scheduledTimer = null
        
        // 取消所有正在进行的任务
        scope.cancel()
        
        logger.info("Background parse service stopped")
    }
    
    /**
     * 处理未解析的书籍
     */
    private fun processUnparsedBooks() {
        scope.launch {
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
    
    /**
     * 获取服务状态
     */
    fun getStatus(): BackgroundParseStatus {
        val unparsedCount = try {
            bookRepository.findUnparsedBooks(1000).size
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
