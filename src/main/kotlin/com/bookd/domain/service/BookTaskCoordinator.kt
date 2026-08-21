package com.bookd.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared lifecycle boundary for backend background book tasks.
 */
class BookTaskCoordinator(
    private val contentDelayMillis: Long = 1000L,
    private val metadataDelayMillis: Long = 500L,
    private val contentDispatcher: ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(2).asCoroutineDispatcher(),
    private val metadataDispatcher: ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(2).asCoroutineDispatcher()
) : AutoCloseable {
    private val contentScope = CoroutineScope(contentDispatcher + SupervisorJob())
    private val metadataScope = CoroutineScope(metadataDispatcher + SupervisorJob())
    private val parsingBooks = ConcurrentHashMap.newKeySet<Int>()
    private val closed = AtomicBoolean(false)

    fun launchContentParse(bookId: Int, block: suspend () -> Unit): Boolean {
        if (closed.get()) return false
        if (!parsingBooks.add(bookId)) return false

        contentScope.launch {
            try {
                if (contentDelayMillis > 0) delay(contentDelayMillis)
                block()
            } finally {
                parsingBooks.remove(bookId)
            }
        }

        return true
    }

    fun isContentParseInProgress(bookId: Int): Boolean = parsingBooks.contains(bookId)

    fun launchMetadataExtraction(block: suspend () -> Unit): Boolean {
        if (closed.get()) return false

        metadataScope.launch {
            if (metadataDelayMillis > 0) delay(metadataDelayMillis)
            block()
        }

        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        contentScope.cancel()
        metadataScope.cancel()
        contentDispatcher.close()
        metadataDispatcher.close()
    }
}
