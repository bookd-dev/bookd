package com.bookd.routes

import com.bookd.domain.model.*
import com.bookd.domain.service.UserService
import com.bookd.domain.service.BookshelfService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(userService: UserService) {
    route("/api/auth") {
        // Check if admin exists (for first-time setup)
        get("/has-admin") {
            call.respondSuccess(mapOf("hasAdmin" to userService.hasAdmin()))
        }

        // First-time setup - create admin
        post("/setup") {
            if (userService.hasAdmin()) {
                call.respondError(ErrorCode.AUTH_ADMIN_EXISTS)
                return@post
            }

            val request = call.receive<RegisterRequest>()

            try {
                val admin = userService.createFirstAdmin(request.username, request.password, request.email)
                
                // 初始化用户书架
                try {
                    val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
                    bookshelfService.initializeUserBookshelves(admin.id)
                } catch (e: Exception) {
                    logger.warn("初始化管理员书架失败: ${e.message}")
                }
                
                call.respondSuccess(HttpStatusCode.Created, UserResponse(admin.id, admin.username, admin.email, admin.role))
            } catch (e: Exception) {
                call.respondError(ErrorCode.AUTH_SETUP_FAILED, e.message)
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = userService.login(request.username, request.password)

            if (response != null) {
                call.respondSuccess(response)
            } else {
                call.respondError(ErrorCode.AUTH_INVALID_CREDENTIALS)
            }
        }

        post("/logout") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token != null) {
                userService.logout(token)
            }
            call.respondSuccessMessage(MessageBundle.Success.LOGGED_OUT)
        }

        post("/register/guest") {
            val request = call.receive<RegisterRequest>()

            try {
                val user = userService.registerGuest(request.username, request.password, request.email)
                
                // 初始化用户书架
                try {
                    val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
                    bookshelfService.initializeUserBookshelves(user.id)
                } catch (e: Exception) {
                    logger.warn("初始化访客书架失败: ${e.message}")
                }
                
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

            val user = userService.registerUser(request.username, request.password, request.email, request.inviteToken)

            if (user != null) {
                // 初始化用户书架
                try {
                    val bookshelfService = get<BookshelfService>(BookshelfService::class.java)
                    bookshelfService.initializeUserBookshelves(user.id)
                } catch (e: Exception) {
                    logger.warn("初始化用户书架失败: ${e.message}")
                }
                
                call.respondSuccess(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role))
            } else {
                call.respondError(ErrorCode.AUTH_INVALID_INVITE)
            }
        }

        get("/me") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                call.respondError(ErrorCode.AUTH_NO_TOKEN)
                return@get
            }

            val user = userService.validateToken(token)
            if (user != null) {
                call.respondSuccess(UserResponse(user.id, user.username, user.email, user.role))
            } else {
                call.respondError(ErrorCode.AUTH_INVALID_TOKEN)
            }
        }
    }
}

fun Route.userManagementRoutes(userService: UserService) {
    route("/api/users") {
        // Require admin authentication
        get {
            if (!checkAdmin(call, userService)) return@get

            val users = userService.findAll()
            call.respondSuccess(users.map { UserResponse(it.id, it.username, it.email, it.role) })
        }

        delete("/{id}") {
            if (!checkAdmin(call, userService)) return@delete

            val userId = call.parameters["id"]?.toIntOrNull()
            if (userId == null) {
                call.respondError(ErrorCode.USER_INVALID_ID)
                return@delete
            }

            val success = userService.deleteUser(userId)
            if (success) {
                call.respondSuccessMessage(MessageBundle.Success.USER_DELETED)
            } else {
                call.respondError(ErrorCode.USER_NOT_FOUND)
            }
        }

        post("/invite-tokens") {
            if (!checkAdmin(call, userService)) return@post

            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            val user = token?.let { userService.validateToken(it) }

            if (user == null) {
                call.respondError(ErrorCode.AUTH_INVALID_TOKEN)
                return@post
            }

            val inviteToken = userService.createInviteToken(user.id)
            if (inviteToken != null) {
                call.respondSuccess(HttpStatusCode.Created, inviteToken)
            } else {
                call.respondError(ErrorCode.AUTH_NOT_AUTHORIZED)
            }
        }

        get("/invite-tokens") {
            if (!checkAdmin(call, userService)) return@get

            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            val user = token?.let { userService.validateToken(it) }

            if (user == null) {
                call.respondError(ErrorCode.AUTH_INVALID_TOKEN)
                return@get
            }

            val tokens = userService.getInviteTokens(user.id)
            call.respondSuccess(tokens)
        }
    }
}

private suspend fun checkAdmin(call: ApplicationCall, userService: UserService): Boolean {
    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
    if (token == null) {
        call.respondError(ErrorCode.AUTH_NO_TOKEN)
        return false
    }

    val user = userService.validateToken(token)
    if (user == null || user.role != UserRole.ADMIN.value) {
        call.respondError(ErrorCode.AUTH_ADMIN_REQUIRED)
        return false
    }

    return true
}
