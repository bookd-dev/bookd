package com.bookd.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.atomic.AtomicReference

object DatabaseConfig : AutoCloseable {
    private val currentDataSource = AtomicReference<HikariDataSource?>(null)

    fun init(
        jdbcUrl: String,
        driver: String,
        username: String,
        password: String
    ) {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.driverClassName = driver
            this.username = username
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        try {
            Database.connect(dataSource)
            currentDataSource.getAndSet(dataSource)?.close()
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }
    }

    override fun close() {
        currentDataSource.getAndSet(null)?.close()
    }
}
