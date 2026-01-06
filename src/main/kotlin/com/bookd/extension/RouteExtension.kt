package com.bookd.com.bookd.extension

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