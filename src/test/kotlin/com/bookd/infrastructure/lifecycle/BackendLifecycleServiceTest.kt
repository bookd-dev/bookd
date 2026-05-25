package com.bookd.infrastructure.lifecycle

import com.bookd.domain.service.BackgroundParseService
import com.bookd.domain.service.BookTaskCoordinator
import com.bookd.infrastructure.cache.RedisService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class BackendLifecycleServiceTest {

    @Test
    fun `given lifecycle service when closed twice then resources are closed once`() {
        val backgroundParseService = mockk<BackgroundParseService>()
        val taskCoordinator = mockk<BookTaskCoordinator>()
        val redisService = mockk<RedisService>()

        every { backgroundParseService.stop() } returns Unit
        every { taskCoordinator.close() } returns Unit
        every { redisService.close() } returns Unit

        val service = BackendLifecycleService(backgroundParseService, taskCoordinator, redisService)

        service.close()
        service.close()

        verify(exactly = 1) { backgroundParseService.stop() }
        verify(exactly = 1) { taskCoordinator.close() }
        verify(exactly = 1) { redisService.close() }
    }
}
