package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.InviteTokens
import com.bookd.data.entity.Sessions
import com.bookd.data.entity.Users
import com.bookd.domain.model.InviteToken
import com.bookd.domain.model.User
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class UserRepository {
    fun findByUsername(username: String): User? = transaction {
        Users.selectAll().where { Users.username eq username }
            .map { toUser(it) }
            .singleOrNull()
    }
    
    fun findById(userId: Int): User? = transaction {
        Users.selectAll().where { Users.id eq userId }
            .map { toUser(it) }
            .singleOrNull()
    }
    
    fun findAll(): List<User> = transaction {
        Users.selectAll().map { toUser(it) }
    }
    
    fun create(username: String, hashedPassword: String, email: String?, role: String = "user"): User = transaction {
        val now = TimeProvider.now()
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
    
    fun deleteUser(userId: Int): Boolean = transaction {
        Users.deleteWhere { Users.id eq userId } > 0
    }
    
    fun createSession(userId: Int): String = transaction {
        val token = UUID.randomUUID().toString()
        val now = TimeProvider.nowInstant()
        val expiresAt = now.plus(30, DateTimeUnit.DAY, TimeProvider.timeZone).toLocalDateTime(TimeProvider.timeZone)
        
        Sessions.insert {
            it[Sessions.token] = token
            it[Sessions.userId] = userId
            it[createdAt] = TimeProvider.now()
            it[Sessions.expiresAt] = expiresAt
        }
        
        token
    }
    
    fun findUserByToken(token: String): User? = transaction {
        val now = TimeProvider.now()
        Sessions.innerJoin(Users)
            .selectAll()
            .where { (Sessions.token eq token) and (Sessions.expiresAt greater now) }
            .map { toUser(it) }
            .singleOrNull()
    }
    
    fun deleteSession(token: String): Boolean = transaction {
        Sessions.deleteWhere { Sessions.token eq token } > 0
    }
    
    fun createInviteToken(createdBy: Int): InviteToken = transaction {
        val token = UUID.randomUUID().toString()
        val now = TimeProvider.now()
        
        val id = InviteTokens.insert {
            it[InviteTokens.token] = token
            it[InviteTokens.createdBy] = createdBy
            it[createdAt] = now
        }[InviteTokens.id]
        
        InviteToken(id.value, token, createdBy, false)
    }
    
    fun findInviteToken(token: String): InviteToken? = transaction {
        InviteTokens.selectAll().where { (InviteTokens.token eq token) and (InviteTokens.used eq false) }
            .map { 
                InviteToken(
                    it[InviteTokens.id].value,
                    it[InviteTokens.token],
                    it[InviteTokens.createdBy].value,
                    it[InviteTokens.used]
                )
            }
            .singleOrNull()
    }
    
    fun markInviteTokenUsed(token: String, userId: Int): Boolean = transaction {
        val now = TimeProvider.now()
        InviteTokens.update({ InviteTokens.token eq token }) {
            it[used] = true
            it[usedBy] = userId
            it[usedAt] = now
        } > 0
    }
    
    fun getInviteTokens(createdBy: Int): List<InviteToken> = transaction {
        InviteTokens.selectAll().where { InviteTokens.createdBy eq createdBy }
            .map { 
                InviteToken(
                    it[InviteTokens.id].value,
                    it[InviteTokens.token],
                    it[InviteTokens.createdBy].value,
                    it[InviteTokens.used]
                )
            }
    }
    
    private fun toUser(row: ResultRow) = User(
        id = row[Users.id].value,
        username = row[Users.username],
        password = row[Users.password],
        email = row[Users.email],
        role = row[Users.role]
    )
}
