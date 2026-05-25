package com.bookd.infrastructure.lifecycle

import com.bookd.domain.service.BackgroundParseService
import com.bookd.domain.service.BookTaskCoordinator
import com.bookd.infrastructure.cache.RedisService
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class BackendLifecycleService(
    private val backgroundParseService: BackgroundParseService,
    private val taskCoordinator: BookTaskCoordinator,
    private val redisService: RedisService? = null
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(BackendLifecycleService::class.java)
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        closeResource("background parse service") {
            backgroundParseService.stop()
        }
        closeResource("book task coordinator") {
            taskCoordinator.close()
        }
        closeResource("Redis service") {
            redisService?.close()
        }
    }

    private fun closeResource(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.warn("Failed to close $name", e)
        }
    }
}
