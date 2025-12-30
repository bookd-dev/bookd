package com.bookd.plugins

import com.bookd.domain.service.UserService
import com.bookd.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val userService by inject<UserService>()
    
    routing {
        // Static files for covers (Docker: /app/covers, Local: ./covers)
        staticFiles("/covers", File("covers"))
        
        healthRoutes()
        authRoutes(userService)
        userManagementRoutes(userService)
        bookRoutes()
        bookSourceRoutes()
        scanRoutes()
        tagRoutes()
        fileSystemRoutes()
        webRoutes()
    }
}
