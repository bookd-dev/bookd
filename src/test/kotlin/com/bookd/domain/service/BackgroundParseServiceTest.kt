package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundParseServiceTest {

    @Test
    fun `given background service stopped when started again then it reports running`() {
        val bookRepository = mockk<BookRepository>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val service = BackgroundParseService(bookRepository, contentService)

        every { bookRepository.findUnparsedBooks(any()) } returns emptyList()
        coEvery { bookRepository.findUnparsedBooksAsync(any()) } returns emptyList()

        service.start()
        service.stop()
        service.start()

        try {
            assertTrue(runBlocking { service.getStatus().running })
        } finally {
            service.stop()
            service.stop()
        }
    }

    @Test
    fun `given parse background env vars when status is requested then documented names are honored`() = runBlocking {
        val bookRepository = mockk<BookRepository>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val env = mapOf(
            "PARSE_BACKGROUND_ENABLED" to "false",
            "PARSE_BACKGROUND_INTERVAL" to "120",
            "PARSE_BACKGROUND_BATCH_SIZE" to "7"
        )
        val service = BackgroundParseService(bookRepository, contentService, env::get)

        coEvery { bookRepository.findUnparsedBooksAsync(any()) } returns emptyList()

        service.start()
        val status = service.getStatus()

        assertFalse(status.running)
        assertFalse(status.enabled)
        assertEquals(120L, status.intervalSeconds)
        assertEquals(7, status.batchSize)
    }

    @Test
    fun `given legacy background env vars when documented names are absent then legacy names are used`() = runBlocking {
        val bookRepository = mockk<BookRepository>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val env = mapOf(
            "BACKGROUND_PARSE_ENABLED" to "false",
            "BACKGROUND_PARSE_INTERVAL" to "90",
            "BACKGROUND_PARSE_BATCH_SIZE" to "3"
        )
        val service = BackgroundParseService(bookRepository, contentService, env::get)

        coEvery { bookRepository.findUnparsedBooksAsync(any()) } returns emptyList()

        val status = service.getStatus()

        assertFalse(status.running)
        assertFalse(status.enabled)
        assertEquals(90L, status.intervalSeconds)
        assertEquals(3, status.batchSize)
    }
}
