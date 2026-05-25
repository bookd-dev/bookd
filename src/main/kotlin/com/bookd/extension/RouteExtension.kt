package com.bookd.com.bookd.extension

import com.bookd.domain.model.Book
import com.bookd.domain.model.BookWithProgress
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin

fun ApplicationCall.buildBaseUrl(): String {
    val scheme = request.origin.scheme
    val host = request.origin.serverHost
    val port = request.origin.serverPort

    return when {
        (scheme == "http" && port == 80) || (scheme == "https" && port == 443) -> {
            "$scheme://$host"
        }
        else -> "$scheme://$host:$port"
    }
}

fun Book.withPublicCoverUrl(baseUrl: String): Book = copy(
    coverPath = coverPath?.toPublicUrl(baseUrl)
)

fun BookWithProgress.withPublicCoverUrl(baseUrl: String): BookWithProgress = copy(
    book = book.withPublicCoverUrl(baseUrl)
)

private fun String.toPublicUrl(baseUrl: String): String =
    if (startsWith("http")) this else "$baseUrl$this"
