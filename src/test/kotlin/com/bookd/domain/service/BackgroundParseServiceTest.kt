package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundParseServiceTest {

    @Test
    fun `given background service stopped when started again then it reports running`() {
        val bookRepository = mockk<BookRepository>()
        val contentService = mockk<BookContentService>(relaxed = true)
        val service = BackgroundParseService(bookRepository, contentService)

        every { bookRepository.findUnparsedBooks(any()) } returns emptyList()

        service.start()
        service.stop()
        service.start()

        try {
            assertTrue(service.getStatus().running)
        } finally {
            service.stop()
        }
    }
}
