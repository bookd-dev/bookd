package com.bookd

import com.bookd.config.DatabaseConfig
import com.bookd.plugins.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString() 
        ?: "jdbc:postgresql://localhost:5432/bookd"
    val dbDriver = environment.config.propertyOrNull("database.driver")?.getString() 
        ?: "org.postgresql.Driver"
    val dbUser = environment.config.propertyOrNull("database.user")?.getString() 
        ?: "bookd"
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString() 
        ?: "bookd"
    
    DatabaseConfig.init(dbUrl, dbDriver, dbUser, dbPassword)
    
    // Create tables
    transaction {
        SchemaUtils.create(
            com.bookd.data.entity.Users,
            com.bookd.data.entity.Books,
            com.bookd.data.entity.BookSources,
            com.bookd.data.entity.ReadingProgress,
            com.bookd.data.entity.FolderPermissions,
            com.bookd.data.entity.Sessions,
            com.bookd.data.entity.InviteTokens
        )
    }
    
    // Initialize default admin user
    val userService = com.bookd.domain.service.UserService(
        com.bookd.data.repository.UserRepository()
    )
    userService.initializeDefaultAdmin()
    
    // Configure plugins
    configureDependencyInjection()
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
}
