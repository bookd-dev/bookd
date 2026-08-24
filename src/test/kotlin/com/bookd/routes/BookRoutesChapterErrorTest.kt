package com.bookd.routes

import com.bookd.domain.service.BookContentService
import com.bookd.domain.service.ChapterListResult
import com.bookd.domain.service.parser.EbookParseFailureReason
import com.bookd.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookRoutesChapterErrorTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `given protected PDF when chapters requested then localized 422 error is returned`() = testApplication {
        val contentService = mockk<BookContentService>()
        startKoin {
            modules(module { single { contentService } })
        }
        coEvery { contentService.getChapterList(26) } returns ChapterListResult.ParseFailed(
            EbookParseFailureReason.PDF_PROTECTED
        )

        application {
            configureSerialization()
            routing { bookRoutes() }
        }

        val response = client.get("/api/books/26/chapters") {
            header(HttpHeaders.AcceptLanguage, "en")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("BOOK_012"))
        assertTrue(body.contains("PDF content extraction is not permitted"))
    }
}
