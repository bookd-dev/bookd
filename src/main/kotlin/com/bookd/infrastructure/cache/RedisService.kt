package com.bookd.infrastructure.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration

class RedisService(
    host: String = "localhost",
    port: Int = 6379,
    password: String? = null,
    database: Int = 0
) {
    private val logger = LoggerFactory.getLogger(RedisService::class.java)
    
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCommands<String, String>
    
    init {
        try {
            val uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .apply { if (password != null) withPassword(password.toCharArray()) }
                .withTimeout(Duration.ofSeconds(10))
                .build()
            
            redisClient = RedisClient.create(uri)
            connection = redisClient.connect()
            commands = connection.sync()
            
            logger.info("Redis connected successfully: $host:$port")
        } catch (e: Exception) {
            logger.error("Failed to connect to Redis: ${e.message}")
            throw e
        }
    }
    
    /**
     * 设置键值对
     */
    fun set(key: String, value: String, ttlSeconds: Long? = null): Boolean {
        return try {
            if (ttlSeconds != null) {
                commands.setex(key, ttlSeconds, value)
            } else {
                commands.set(key, value)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to set key: $key", e)
            false
        }
    }
    
    /**
     * 获取值
     */
    fun get(key: String): String? {
        return try {
            commands.get(key)
        } catch (e: Exception) {
            logger.error("Failed to get key: $key", e)
            null
        }
    }
    
    /**
     * 删除键
     */
    fun delete(key: String): Boolean {
        return try {
            commands.del(key) > 0
        } catch (e: Exception) {
            logger.error("Failed to delete key: $key", e)
            false
        }
    }
    
    /**
     * 检查键是否存在
     */
    fun exists(key: String): Boolean {
        return try {
            commands.exists(key) > 0
        } catch (e: Exception) {
            logger.error("Failed to check key existence: $key", e)
            false
        }
    }
    
    /**
     * 设置过期时间
     */
    fun expire(key: String, seconds: Long): Boolean {
        return try {
            commands.expire(key, seconds)
        } catch (e: Exception) {
            logger.error("Failed to set expire for key: $key", e)
            false
        }
    }
    
    /**
     * 分布式锁：尝试获取锁
     */
    fun tryLock(lockKey: String, ttlSeconds: Long = 300): Boolean {
        return try {
            val result = commands.set(lockKey, "locked", io.lettuce.core.SetArgs().nx().ex(ttlSeconds))
            result == "OK"
        } catch (e: Exception) {
            logger.error("Failed to acquire lock: $lockKey", e)
            false
        }
    }
    
    /**
     * 分布式锁：释放锁
     */
    fun releaseLock(lockKey: String): Boolean {
        return delete(lockKey)
    }
    
    /**
     * List 操作：左推
     */
    fun lpush(key: String, value: String): Long? {
        return try {
            commands.lpush(key, value)
        } catch (e: Exception) {
            logger.error("Failed to lpush: $key", e)
            null
        }
    }
    
    /**
     * List 操作：右弹
     */
    fun rpop(key: String): String? {
        return try {
            commands.rpop(key)
        } catch (e: Exception) {
            logger.error("Failed to rpop: $key", e)
            null
        }
    }
    
    /**
     * List 操作：获取长度
     */
    fun llen(key: String): Long? {
        return try {
            commands.llen(key)
        } catch (e: Exception) {
            logger.error("Failed to get list length: $key", e)
            null
        }
    }
    
    /**
     * 批量删除（按模式）
     */
    fun deleteByPattern(pattern: String): Long {
        return try {
            val keys = commands.keys(pattern)
            if (keys.isEmpty()) {
                0
            } else {
                commands.del(*keys.toTypedArray())
            }
        } catch (e: Exception) {
            logger.error("Failed to delete by pattern: $pattern", e)
            0
        }
    }
    
    /**
     * 健康检查
     */
    fun ping(): Boolean {
        return try {
            commands.ping() == "PONG"
        } catch (e: Exception) {
            logger.error("Redis ping failed", e)
            false
        }
    }
    
    /**
     * 关闭连接
     */
    fun close() {
        try {
            connection.close()
            redisClient.shutdown()
            logger.info("Redis connection closed")
        } catch (e: Exception) {
            logger.error("Failed to close Redis connection", e)
        }
    }
}
