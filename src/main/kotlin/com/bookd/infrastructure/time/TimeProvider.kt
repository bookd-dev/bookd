package com.bookd.infrastructure.time

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * 统一的时间提供者
 * 从环境变量读取时区配置，默认使用系统时区
 */
object TimeProvider {
    
    private val logger = LoggerFactory.getLogger(TimeProvider::class.java)

    private val timeZoneRef = AtomicReference(resolveDefaultTimeZone())

    val timeZone: TimeZone
        get() = timeZoneRef.get()

    fun setTimeZone(timeZone: TimeZone) {
        timeZoneRef.set(timeZone)
        logger.info("Time zone initialized: ${timeZone.id}")
    }

    fun parseTimeZone(timeZoneId: String): TimeZone? {
        return try {
            TimeZone.of(timeZoneId)
        } catch (e: Exception) {
            null
        }
    }

    fun resolveDefaultTimeZone(environment: (String) -> String? = System::getenv): TimeZone {
        val tzEnv = environment("TZ")
        if (!tzEnv.isNullOrBlank()) {
            val configured = parseTimeZone(tzEnv)
            if (configured != null) {
                return configured
            }
            logger.warn("Invalid TZ environment variable: $tzEnv, falling back to system default")
        }
        return TimeZone.currentSystemDefault()
    }

    internal fun resetForTests(timeZone: TimeZone = resolveDefaultTimeZone()) {
        timeZoneRef.set(timeZone)
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
