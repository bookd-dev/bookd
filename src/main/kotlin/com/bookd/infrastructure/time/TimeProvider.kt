package com.bookd.infrastructure.time

import kotlinx.datetime.*
import org.slf4j.LoggerFactory

/**
 * 统一的时间提供者
 * 从环境变量读取时区配置，默认使用系统时区
 */
object TimeProvider {
    
    private val logger = LoggerFactory.getLogger(TimeProvider::class.java)
    
    /**
     * 应用时区，从环境变量 TZ 读取，默认使用系统时区
     */
    val timeZone: TimeZone by lazy {
        val tzEnv = System.getenv("TZ")
        
        val tz = if (tzEnv.isNullOrBlank()) {
            // 使用系统时区
            TimeZone.currentSystemDefault()
        } else {
            try {
                TimeZone.of(tzEnv)
            } catch (e: Exception) {
                logger.warn("Invalid TZ environment variable: $tzEnv, falling back to system default")
                TimeZone.currentSystemDefault()
            }
        }
        
        logger.info("Time zone initialized: ${tz.id}")
        tz
    }
    
    /**
     * 获取当前时间（应用时区）
     */
    fun now(): LocalDateTime {
        return Clock.System.now().toLocalDateTime(timeZone)
    }
    
    /**
     * 获取当前时间戳（Instant）
     */
    fun nowInstant(): Instant {
        return Clock.System.now()
    }
    
    /**
     * 将 Instant 转换为应用时区的 LocalDateTime
     */
    fun Instant.toAppLocalDateTime(): LocalDateTime {
        return this.toLocalDateTime(timeZone)
    }
    
    /**
     * 将 LocalDateTime 转换为 Instant（假设是应用时区）
     */
    fun LocalDateTime.toInstant(): Instant {
        return this.toInstant(timeZone)
    }
}
