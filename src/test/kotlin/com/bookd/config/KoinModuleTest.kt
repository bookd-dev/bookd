package com.bookd.config

import com.bookd.infrastructure.cache.RedisService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class KoinModuleTest {

    @Test
    fun `given redis ping succeeds when creating service then service is returned`() {
        val redisService = mockk<RedisService>()

        every { redisService.ping() } returns true

        val result = createRedisServiceOrNull(
            redisHost = "redis",
            redisPort = 6379,
            redisPassword = null,
            redisDatabase = 0,
            redisEnabled = true
        ) { _, _, _, _ -> redisService }

        assertSame(redisService, result)
        verify(exactly = 1) { redisService.ping() }
        verify(exactly = 0) { redisService.close() }
    }

    @Test
    fun `given redis ping fails when creating service then service is closed and cache is disabled`() {
        val redisService = mockk<RedisService>()

        every { redisService.ping() } returns false
        every { redisService.close() } returns Unit

        val result = createRedisServiceOrNull(
            redisHost = "redis",
            redisPort = 6379,
            redisPassword = null,
            redisDatabase = 0,
            redisEnabled = true
        ) { _, _, _, _ -> redisService }

        assertNull(result)
        verify(exactly = 1) { redisService.ping() }
        verify(exactly = 1) { redisService.close() }
    }
}
