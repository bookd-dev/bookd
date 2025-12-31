package com.bookd.infrastructure.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * 书籍相关的缓存服务
 */
class BookCacheService(private val redis: RedisService?) {
    private val logger = LoggerFactory.getLogger(BookCacheService::class.java)
    
    companion object {
        private const val KEY_BOOK_PARSED = "book:%d:parsed"
        private const val KEY_BOOK_CHAPTERS = "book:%d:chapters"
        private const val KEY_PARSE_QUEUE = "parse:queue"
        private const val KEY_PARSE_LOCK = "lock:parse:%d"
        
        private const val TTL_CHAPTERS = 3600L // 章节列表缓存1小时
        private const val TTL_PARSE_LOCK = 300L // 解析锁5分钟
    }
    
    @Serializable
    data class CachedChapter(
        val id: Int,
        val bookId: Int,
        val title: String,
        val chapterIndex: Int,
        val wordCount: Int
    )
    
    /**
     * 检查 Redis 是否可用
     */
    private fun isRedisAvailable(): Boolean = redis != null
    
    /**
     * 设置书籍已解析标记
     */
    fun setBookParsed(bookId: Int, parsed: Boolean) {
        if (!isRedisAvailable()) return
        val key = KEY_BOOK_PARSED.format(bookId)
        redis?.set(key, parsed.toString())
    }
    
    /**
     * 检查书籍是否已解析（先查Redis，未命中返回null）
     */
    fun isBookParsed(bookId: Int): Boolean? {
        if (!isRedisAvailable()) return null
        val key = KEY_BOOK_PARSED.format(bookId)
        return redis?.get(key)?.toBoolean()
    }
    
    /**
     * 缓存章节列表
     */
    fun cacheChapters(bookId: Int, chapters: List<CachedChapter>) {
        if (!isRedisAvailable()) return
        try {
            val key = KEY_BOOK_CHAPTERS.format(bookId)
            val json = Json.encodeToString(chapters)
            redis?.set(key, json, TTL_CHAPTERS)
            logger.debug("Cached chapters for book $bookId")
        } catch (e: Exception) {
            logger.error("Failed to cache chapters for book $bookId", e)
        }
    }
    
    /**
     * 获取缓存的章节列表
     */
    fun getCachedChapters(bookId: Int): List<CachedChapter>? {
        if (!isRedisAvailable()) return null
        return try {
            val key = KEY_BOOK_CHAPTERS.format(bookId)
            val json = redis?.get(key) ?: return null
            Json.decodeFromString<List<CachedChapter>>(json)
        } catch (e: Exception) {
            logger.error("Failed to get cached chapters for book $bookId", e)
            null
        }
    }
    
    /**
     * 清除书籍相关的所有缓存
     */
    fun clearBookCache(bookId: Int) {
        if (!isRedisAvailable()) return
        redis?.delete(KEY_BOOK_PARSED.format(bookId))
        redis?.delete(KEY_BOOK_CHAPTERS.format(bookId))
        logger.debug("Cleared cache for book $bookId")
    }
    
    /**
     * 将书籍加入解析队列
     */
    fun addToParseQueue(bookId: Int) {
        if (!isRedisAvailable()) return
        redis?.lpush(KEY_PARSE_QUEUE, bookId.toString())
        logger.debug("Added book $bookId to parse queue")
    }
    
    /**
     * 从解析队列获取下一个书籍ID
     */
    fun getNextFromParseQueue(): Int? {
        if (!isRedisAvailable()) return null
        return redis?.rpop(KEY_PARSE_QUEUE)?.toIntOrNull()
    }
    
    /**
     * 获取解析队列长度
     */
    fun getParseQueueLength(): Long {
        if (!isRedisAvailable()) return 0
        return redis?.llen(KEY_PARSE_QUEUE) ?: 0
    }
    
    /**
     * 尝试获取解析锁
     */
    fun tryAcquireParseLock(bookId: Int): Boolean {
        if (!isRedisAvailable()) return true // 没有Redis时总是返回true
        val key = KEY_PARSE_LOCK.format(bookId)
        return redis?.tryLock(key, TTL_PARSE_LOCK) ?: true
    }
    
    /**
     * 释放解析锁
     */
    fun releaseParseLock(bookId: Int) {
        if (!isRedisAvailable()) return
        val key = KEY_PARSE_LOCK.format(bookId)
        redis?.releaseLock(key)
    }
    
    /**
     * 批量清除所有书籍缓存
     */
    fun clearAllBookCaches() {
        if (!isRedisAvailable()) return
        redis?.deleteByPattern("book:*")
        logger.info("Cleared all book caches")
    }
}
