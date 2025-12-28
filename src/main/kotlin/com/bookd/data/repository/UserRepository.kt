package com.bookd.data.repository

import com.bookd.data.entity.Users
import com.bookd.domain.model.User
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository {
    fun findByUsername(username: String): User? = transaction {
        Users.selectAll().where { Users.username eq username }
            .map { toUser(it) }
            .singleOrNull()
    }
    
    fun create(username: String, hashedPassword: String, email: String?, role: String = "user"): User = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val id = Users.insert {
            it[Users.username] = username
            it[password] = hashedPassword
            it[Users.email] = email
            it[Users.role] = role
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        
        User(id.value, username, hashedPassword, email, role)
    }
    
    private fun toUser(row: ResultRow) = User(
        id = row[Users.id].value,
        username = row[Users.username],
        password = row[Users.password],
        email = row[Users.email],
        role = row[Users.role]
    )
}
