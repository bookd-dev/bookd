package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.InviteTokens
import com.bookd.data.entity.Sessions
import com.bookd.data.entity.Users
import com.bookd.domain.model.InviteToken
import com.bookd.domain.model.User
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class UserRepository {
    fun findByUsername(username: String): User? = transaction {
        findByUsernameInCurrentTransaction(username)
    }

    suspend fun findByUsernameAsync(username: String): User? = dbQuery {
        findByUsernameInCurrentTransaction(username)
    }
    
    fun findById(userId: Int): User? = transaction {
        findByIdInCurrentTransaction(userId)
    }

    suspend fun findByIdAsync(userId: Int): User? = dbQuery {
        findByIdInCurrentTransaction(userId)
    }
    
    fun findAll(): List<User> = transaction {
        findAllInCurrentTransaction()
    }

    suspend fun findAllAsync(): List<User> = dbQuery {
        findAllInCurrentTransaction()
    }
    
    fun create(username: String, hashedPassword: String, email: String?, role: String = "user"): User = transaction {
        createInCurrentTransaction(username, hashedPassword, email, role)
    }

    suspend fun createAsync(username: String, hashedPassword: String, email: String?, role: String = "user"): User = dbQuery {
        createInCurrentTransaction(username, hashedPassword, email, role)
    }

    fun deleteUser(userId: Int): Boolean = transaction {
        deleteUserInCurrentTransaction(userId)
    }

    suspend fun deleteUserAsync(userId: Int): Boolean = dbQuery {
        deleteUserInCurrentTransaction(userId)
    }

    fun createSession(userId: Int): String = transaction {
        createSessionInCurrentTransaction(userId)
    }

    suspend fun createSessionAsync(userId: Int): String = dbQuery {
        createSessionInCurrentTransaction(userId)
    }

    fun findUserByToken(token: String): User? = transaction {
        findUserByTokenInCurrentTransaction(token)
    }

    suspend fun findUserByTokenAsync(token: String): User? = dbQuery {
        findUserByTokenInCurrentTransaction(token)
    }

    fun deleteSession(token: String): Boolean = transaction {
        deleteSessionInCurrentTransaction(token)
    }

    suspend fun deleteSessionAsync(token: String): Boolean = dbQuery {
        deleteSessionInCurrentTransaction(token)
    }

    fun createInviteToken(createdBy: Int): InviteToken = transaction {
        createInviteTokenInCurrentTransaction(createdBy)
    }

    suspend fun createInviteTokenAsync(createdBy: Int): InviteToken = dbQuery {
        createInviteTokenInCurrentTransaction(createdBy)
    }

    fun findInviteToken(token: String): InviteToken? = transaction {
        findInviteTokenInCurrentTransaction(token)
    }

    suspend fun findInviteTokenAsync(token: String): InviteToken? = dbQuery {
        findInviteTokenInCurrentTransaction(token)
    }

    fun markInviteTokenUsed(token: String, userId: Int): Boolean = transaction {
        markInviteTokenUsedInCurrentTransaction(token, userId)
    }

    suspend fun markInviteTokenUsedAsync(token: String, userId: Int): Boolean = dbQuery {
        markInviteTokenUsedInCurrentTransaction(token, userId)
    }

    fun getInviteTokens(createdBy: Int): List<InviteToken> = transaction {
        getInviteTokensInCurrentTransaction(createdBy)
    }

    suspend fun getInviteTokensAsync(createdBy: Int): List<InviteToken> = dbQuery {
        getInviteTokensInCurrentTransaction(createdBy)
    }

    private fun findByUsernameInCurrentTransaction(username: String): User? {
        return Users.selectAll().where { Users.username eq username }
            .map { toUser(it) }
            .singleOrNull()
    }

    private fun findByIdInCurrentTransaction(userId: Int): User? {
        return Users.selectAll().where { Users.id eq userId }
            .map { toUser(it) }
            .singleOrNull()
    }

    private fun findAllInCurrentTransaction(): List<User> {
        return Users.selectAll().map { toUser(it) }
    }

    private fun createInCurrentTransaction(
        username: String,
        hashedPassword: String,
        email: String?,
        role: String = "user"
    ): User {
        val now = TimeProvider.now()
        val id = Users.insert {
            it[Users.username] = username
            it[password] = hashedPassword
            it[Users.email] = email
            it[Users.role] = role
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        
        return User(id.value, username, hashedPassword, email, role)
    }

    private fun deleteUserInCurrentTransaction(userId: Int): Boolean {
        return Users.deleteWhere { Users.id eq userId } > 0
    }

    private fun createSessionInCurrentTransaction(userId: Int): String {
        val token = UUID.randomUUID().toString()
        val now = TimeProvider.nowInstant()
        val expiresAt = now.plus(30, DateTimeUnit.DAY, TimeProvider.timeZone).toLocalDateTime(TimeProvider.timeZone)
        
        Sessions.insert {
            it[Sessions.token] = token
            it[Sessions.userId] = userId
            it[createdAt] = TimeProvider.now()
            it[Sessions.expiresAt] = expiresAt
        }
        
        return token
    }

    private fun findUserByTokenInCurrentTransaction(token: String): User? {
        val now = TimeProvider.now()
        return Sessions.innerJoin(Users)
            .selectAll()
            .where { (Sessions.token eq token) and (Sessions.expiresAt greater now) }
            .map { toUser(it) }
            .singleOrNull()
    }

    private fun deleteSessionInCurrentTransaction(token: String): Boolean {
        return Sessions.deleteWhere { Sessions.token eq token } > 0
    }

    private fun createInviteTokenInCurrentTransaction(createdBy: Int): InviteToken {
        val token = UUID.randomUUID().toString()
        val now = TimeProvider.now()
        
        val id = InviteTokens.insert {
            it[InviteTokens.token] = token
            it[InviteTokens.createdBy] = createdBy
            it[createdAt] = now
        }[InviteTokens.id]
        
        return InviteToken(id.value, token, createdBy, false)
    }

    private fun findInviteTokenInCurrentTransaction(token: String): InviteToken? {
        return InviteTokens.selectAll().where { (InviteTokens.token eq token) and (InviteTokens.used eq false) }
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

    private fun markInviteTokenUsedInCurrentTransaction(token: String, userId: Int): Boolean {
        val now = TimeProvider.now()
        return InviteTokens.update({ InviteTokens.token eq token }) {
            it[used] = true
            it[usedBy] = userId
            it[usedAt] = now
        } > 0
    }

    private fun getInviteTokensInCurrentTransaction(createdBy: Int): List<InviteToken> {
        return InviteTokens.selectAll().where { InviteTokens.createdBy eq createdBy }
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
