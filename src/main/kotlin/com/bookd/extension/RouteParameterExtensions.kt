package com.bookd.extension

import com.bookd.domain.model.ErrorCode
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.requiredIntParameter(name: String, errorCode: ErrorCode): Int? {
    val value = parameters[name]?.toIntOrNull()
    if (value == null) {
        respondError(errorCode)
    }
    return value
}

fun ApplicationCall.optionalIntQueryParameter(name: String): Int? =
    request.queryParameters[name]?.toIntOrNull()

fun ApplicationCall.intQueryParameter(name: String, default: Int): Int =
    request.queryParameters[name]?.toIntOrNull() ?: default

fun ApplicationCall.longQueryParameter(name: String, default: Long): Long =
    request.queryParameters[name]?.toLongOrNull() ?: default

fun ApplicationCall.booleanQueryParameter(name: String, default: Boolean): Boolean =
    request.queryParameters[name]?.toBoolean() ?: default
