package com.bookd.plugins

import com.bookd.routes.*
import com.bookd.domain.service.UserService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val userService by inject<UserService>()
    
    routing {
        healthRoutes()
        authRoutes(userService)
        userManagementRoutes(userService)
        bookRoutes()
        bookSourceRoutes()
        scanRoutes()
        fileSystemRoutes()
        webRoutes()
    }
}
