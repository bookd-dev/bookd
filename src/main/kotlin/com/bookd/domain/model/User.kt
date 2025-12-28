package com.bookd.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val password: String,
    val email: String?,
    val role: String
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String?,
    val role: String
)
