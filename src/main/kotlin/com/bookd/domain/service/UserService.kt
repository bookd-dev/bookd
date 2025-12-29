package com.bookd.domain.service

import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.*
import org.mindrot.jbcrypt.BCrypt

class UserService(
    private val userRepository: UserRepository
) {
    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }
    
    fun findById(userId: Int): User? {
        return userRepository.findById(userId)
    }
    
    fun findAll(): List<User> {
        return userRepository.findAll()
    }
    
    fun login(username: String, password: String): LoginResponse? {
        val user = userRepository.findByUsername(username) ?: return null
        
        if (!BCrypt.checkpw(password, user.password)) {
            return null
        }
        
        val token = userRepository.createSession(user.id)
        return LoginResponse(
            token = token,
            user = UserResponse(user.id, user.username, user.email, user.role)
        )
    }
    
    fun logout(token: String): Boolean {
        return userRepository.deleteSession(token)
    }
    
    fun validateToken(token: String): User? {
        return userRepository.findUserByToken(token)
    }
    
    fun registerGuest(username: String, password: String, email: String?): User {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        return userRepository.create(username, hashedPassword, email, UserRole.GUEST.value)
    }
    
    fun registerUser(username: String, password: String, email: String?, inviteToken: String): User? {
        val token = userRepository.findInviteToken(inviteToken) ?: return null
        
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.create(username, hashedPassword, email, UserRole.USER.value)
        
        userRepository.markInviteTokenUsed(inviteToken, user.id)
        
        return user
    }
    
    fun createInviteToken(adminId: Int): InviteToken? {
        val admin = userRepository.findById(adminId) ?: return null
        if (admin.role != UserRole.ADMIN.value) return null
        
        return userRepository.createInviteToken(adminId)
    }
    
    fun getInviteTokens(adminId: Int): List<InviteToken> {
        return userRepository.getInviteTokens(adminId)
    }
    
    fun deleteUser(userId: Int): Boolean {
        return userRepository.deleteUser(userId)
    }
    
    fun hasAdmin(): Boolean {
        val users = userRepository.findAll()
        return users.any { it.role == UserRole.ADMIN.value }
    }
    
    fun createFirstAdmin(username: String, password: String, email: String?): User {
        // Check if admin already exists
        if (hasAdmin()) {
            throw IllegalStateException("Admin user already exists")
        }
        
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        return userRepository.create(username, hashedPassword, email, UserRole.ADMIN.value)
    }
}
