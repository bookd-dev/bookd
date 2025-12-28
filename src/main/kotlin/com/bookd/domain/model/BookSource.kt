package com.bookd.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BookSource(
    val id: Int = 0,
    val name: String,
    val path: String,
    val enabled: Boolean
)

@Serializable
data class CreateBookSourceRequest(
    val name: String,
    val path: String
)
