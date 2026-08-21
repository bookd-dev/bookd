package com.bookd.domain.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookTaskCoordinatorTest {

    @Test
    fun `given same book parse is already running when queued again then duplicate is skipped and marker is cleared`() = runBlocking {
        val coordinator = BookTaskCoordinator(contentDelayMillis = 0, metadataDelayMillis = 0)
        val release = CompletableDeferred<Unit>()

        try {
            assertTrue(coordinator.launchContentParse(7) { release.await() })
            assertTrue(coordinator.isContentParseInProgress(7))
            assertFalse(coordinator.launchContentParse(7) { })

            release.complete(Unit)
            withTimeout(1_000) {
                while (coordinator.isContentParseInProgress(7)) {
                    delay(10)
                }
            }

            assertFalse(coordinator.isContentParseInProgress(7))
            assertTrue(coordinator.launchContentParse(7) { })
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `given coordinator is closed when launching tasks then new work is rejected`() {
        val coordinator = BookTaskCoordinator(contentDelayMillis = 0, metadataDelayMillis = 0)

        coordinator.close()
        coordinator.close()

        assertFalse(coordinator.launchContentParse(9) { })
        assertFalse(coordinator.launchMetadataExtraction { })
    }
}
