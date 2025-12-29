package com.bookd.routes

import com.bookd.domain.model.*
import com.bookd.domain.service.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.pipeline.*

fun Route.authRoutes(userService: UserService) {
    route("/api/auth") {
        // Check if admin exists (for first-time setup)
        get("/has-admin") {
            call.respond(HttpStatusCode.OK, mapOf("hasAdmin" to userService.hasAdmin()))
        }
        
        // First-time setup - create admin
        post("/setup") {
            if (userService.hasAdmin()) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Admin already exists"))
                return@post
            }
            
            val request = call.receive<RegisterRequest>()
            
            try {
                val admin = userService.createFirstAdmin(request.username, request.password, request.email)
                call.respond(HttpStatusCode.Created, UserResponse(admin.id, admin.username, admin.email, admin.role))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Setup failed")))
            }
        }
        
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = userService.login(request.username, request.password)
            
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            }
        }
        
        post("/logout") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token != null) {
                userService.logout(token)
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }
        
        post("/register/guest") {
            val request = call.receive<RegisterRequest>()
            
            try {
                val user = userService.registerGuest(request.username, request.password, request.email)
                call.respond(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username already exists"))
            }
        }
        
        post("/register/user") {
            val request = call.receive<RegisterRequest>()
            
            if (request.inviteToken == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invite token required"))
                return@post
            }
            
            val user = userService.registerUser(request.username, request.password, request.email, request.inviteToken)
            
            if (user != null) {
                call.respond(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid invite token or username already exists"))
            }
        }
        
        get("/me") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No token provided"))
                return@get
            }
            
            val user = userService.validateToken(token)
            if (user != null) {
                call.respond(HttpStatusCode.OK, UserResponse(user.id, user.username, user.email, user.role))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
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
            call.respond(users.map { UserResponse(it.id, it.username, it.email, it.role) })
        }
        
        delete("/{id}") {
            if (!checkAdmin(call, userService)) return@delete
            
            val userId = call.parameters["id"]?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))
                return@delete
            }
            
            val success = userService.deleteUser(userId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            }
        }
        
        post("/invite-tokens") {
            if (!checkAdmin(call, userService)) return@post
            
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            val user = token?.let { userService.validateToken(it) }
            
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@post
            }
            
            val inviteToken = userService.createInviteToken(user.id)
            if (inviteToken != null) {
                call.respond(HttpStatusCode.Created, inviteToken)
            } else {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
            }
        }
        
        get("/invite-tokens") {
            if (!checkAdmin(call, userService)) return@get
            
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            val user = token?.let { userService.validateToken(it) }
            
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                return@get
            }
            
            val tokens = userService.getInviteTokens(user.id)
            call.respond(tokens)
        }
    }
}

private suspend fun checkAdmin(call: ApplicationCall, userService: UserService): Boolean {
    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
    if (token == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No token provided"))
        return false
    }
    
    val user = userService.validateToken(token)
    if (user == null || user.role != UserRole.ADMIN.value) {
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
        return false
    }
    
    return true
}
