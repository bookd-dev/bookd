package com.bookd.domain.model

import kotlinx.serialization.Serializable

enum class UserRole(val value: String) {
    ADMIN("admin"),
    USER("user"),
    GUEST("guest");
    
    companion object {
        fun fromString(value: String): UserRole = entries.find { it.value == value } ?: GUEST
    }
}

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

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    val inviteToken: String? = null
)

@Serializable
data class InviteToken(
    val id: Int,
    val token: String,
    val createdBy: Int,
    val used: Boolean
)
