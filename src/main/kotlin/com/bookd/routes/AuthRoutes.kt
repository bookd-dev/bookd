package com.bookd.routes

import com.bookd.domain.model.*
import com.bookd.domain.service.UserService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(userService: UserService) {
    route("/api/auth") {
        // Check if admin exists (for first-time setup)
        get("/has-admin") {
            call.respondSuccess(mapOf("hasAdmin" to userService.hasAdminAsync()))
        }

        // First-time setup - create admin
        post("/setup") {
            if (userService.hasAdminAsync()) {
                call.respondError(ErrorCode.AUTH_ADMIN_EXISTS)
                return@post
            }

            val request = call.receive<RegisterRequest>()

            try {
                val admin = userService.createFirstAdminAsync(request.username, request.password, request.email)
                call.respondSuccess(HttpStatusCode.Created, UserResponse(admin.id, admin.username, admin.email, admin.role))
            } catch (e: Exception) {
                logger.error("初次管理员设置失败", e)
                call.respondError(ErrorCode.AUTH_SETUP_FAILED, e.message)
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = userService.loginAsync(request.username, request.password)

            if (response != null) {
                call.respondSuccess(response)
            } else {
                call.respondError(ErrorCode.AUTH_INVALID_CREDENTIALS)
            }
        }

        post("/logout") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token != null) {
                userService.logoutAsync(token)
            }
            call.respondSuccessMessage(MessageBundle.Success.LOGGED_OUT)
        }

        post("/register/guest") {
            val request = call.receive<RegisterRequest>()

            try {
                val user = userService.registerGuestAsync(request.username, request.password, request.email)
                call.respondSuccess(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role))
            } catch (e: Exception) {
                call.respondError(ErrorCode.AUTH_USERNAME_EXISTS)
            }
        }

        post("/register/user") {
            val request = call.receive<RegisterRequest>()

            if (request.inviteToken == null) {
                call.respondError(ErrorCode.AUTH_INVITE_REQUIRED)
                return@post
            }

            val user = userService.registerUserAsync(request.username, request.password, request.email, request.inviteToken)

            if (user != null) {
                call.respondSuccess(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role))
            } else {
                call.respondError(ErrorCode.AUTH_INVALID_INVITE)
            }
        }

        get("/me") {
            val user = call.getAuthenticatedUser(userService) ?: return@get
            call.respondSuccess(UserResponse(user.id, user.username, user.email, user.role))
        }
    }
}

fun Route.userManagementRoutes(userService: UserService) {
    route("/api/users") {
        // Require admin authentication
        get {
            call.requireAdminUser(userService) ?: return@get

            val users = userService.findAllAsync()
            call.respondSuccess(users.map { UserResponse(it.id, it.username, it.email, it.role) })
        }

        delete("/{id}") {
            call.requireAdminUser(userService) ?: return@delete

            val userId = call.requiredIntParameter("id", ErrorCode.USER_INVALID_ID) ?: return@delete

            val success = userService.deleteUserAsync(userId)
            if (success) {
                call.respondSuccessMessage(MessageBundle.Success.USER_DELETED)
            } else {
                call.respondError(ErrorCode.USER_NOT_FOUND)
            }
        }

        post("/invite-tokens") {
            val user = call.requireAdminUser(userService) ?: return@post

            val inviteToken = userService.createInviteTokenAsync(user.id)
            if (inviteToken != null) {
                call.respondSuccess(HttpStatusCode.Created, inviteToken)
            } else {
                call.respondError(ErrorCode.AUTH_NOT_AUTHORIZED)
            }
        }

        get("/invite-tokens") {
            val user = call.requireAdminUser(userService) ?: return@get

            val tokens = userService.getInviteTokensAsync(user.id)
            call.respondSuccess(tokens)
        }
    }
}
