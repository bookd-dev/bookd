package com.bookd.domain.service

import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.*
import com.bookd.infrastructure.time.TimeProvider
import org.mindrot.jbcrypt.BCrypt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class UserService(
    private val userRepository: UserRepository,
    private val bookshelfService: BookshelfService? = null,
    private val nowMillis: () -> Long = { TimeProvider.nowInstant().toEpochMilliseconds() },
    private val tokenCacheTtlMillis: Long = 30_000L,
    private val tokenCachePruneIntervalMillis: Long = tokenCacheTtlMillis
) {
    private data class CachedUser(
        val user: User,
        val expiresAtMillis: Long
    )

    private val tokenCache = ConcurrentHashMap<String, CachedUser>()
    private val lastTokenCachePruneMillis = AtomicLong(0L)

    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    suspend fun findByUsernameAsync(username: String): User? {
        return userRepository.findByUsernameAsync(username)
    }
    
    fun findById(userId: Int): User? {
        return userRepository.findById(userId)
    }

    suspend fun findByIdAsync(userId: Int): User? {
        return userRepository.findByIdAsync(userId)
    }
    
    fun findAll(): List<User> {
        return userRepository.findAll()
    }

    suspend fun findAllAsync(): List<User> {
        return userRepository.findAllAsync()
    }
    
    fun login(username: String, password: String): LoginResponse? {
        val user = userRepository.findByUsername(username) ?: return null

        if (!passwordMatches(password, user)) return null

        val token = userRepository.createSession(user.id)
        return user.toLoginResponse(token)
    }

    suspend fun loginAsync(username: String, password: String): LoginResponse? {
        val user = userRepository.findByUsernameAsync(username) ?: return null

        if (!passwordMatches(password, user)) return null

        val token = userRepository.createSessionAsync(user.id)
        return user.toLoginResponse(token)
    }
    
    fun logout(token: String): Boolean {
        tokenCache.remove(token)
        return userRepository.deleteSession(token)
    }

    suspend fun logoutAsync(token: String): Boolean {
        tokenCache.remove(token)
        return userRepository.deleteSessionAsync(token)
    }
    
    fun validateToken(token: String): User? {
        val now = nowMillis()
        pruneExpiredTokenCache(now)
        getCachedUser(token, now)?.let { return it }

        val user = userRepository.findUserByToken(token) ?: return null
        cacheUser(token, user, now)
        return user
    }

    suspend fun validateTokenAsync(token: String): User? {
        val now = nowMillis()
        pruneExpiredTokenCache(now)
        getCachedUser(token, now)?.let { return it }

        val user = userRepository.findUserByTokenAsync(token) ?: return null
        cacheUser(token, user, now)
        return user
    }
    
    fun registerGuest(username: String, password: String, email: String?): User {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.create(username, hashedPassword, email, UserRole.GUEST.value)
        initializeUserBookshelves(user.id, failOnError = false)
        return user
    }

    suspend fun registerGuestAsync(username: String, password: String, email: String?): User {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.createAsync(username, hashedPassword, email, UserRole.GUEST.value)
        initializeUserBookshelvesAsync(user.id, failOnError = false)
        return user
    }
    
    fun registerUser(username: String, password: String, email: String?, inviteToken: String): User? {
        val token = userRepository.findInviteToken(inviteToken) ?: return null
        
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.create(username, hashedPassword, email, UserRole.USER.value)
        
        userRepository.markInviteTokenUsed(inviteToken, user.id)
        initializeUserBookshelves(user.id, failOnError = false)
        
        return user
    }

    suspend fun registerUserAsync(username: String, password: String, email: String?, inviteToken: String): User? {
        val token = userRepository.findInviteTokenAsync(inviteToken) ?: return null

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.createAsync(username, hashedPassword, email, UserRole.USER.value)

        userRepository.markInviteTokenUsedAsync(inviteToken, user.id)
        initializeUserBookshelvesAsync(user.id, failOnError = false)

        return user
    }
    
    fun createInviteToken(adminId: Int): InviteToken? {
        val admin = userRepository.findById(adminId) ?: return null
        if (admin.role != UserRole.ADMIN.value) return null
        
        return userRepository.createInviteToken(adminId)
    }

    suspend fun createInviteTokenAsync(adminId: Int): InviteToken? {
        val admin = userRepository.findByIdAsync(adminId) ?: return null
        if (admin.role != UserRole.ADMIN.value) return null

        return userRepository.createInviteTokenAsync(adminId)
    }
    
    fun getInviteTokens(adminId: Int): List<InviteToken> {
        return userRepository.getInviteTokens(adminId)
    }

    suspend fun getInviteTokensAsync(adminId: Int): List<InviteToken> {
        return userRepository.getInviteTokensAsync(adminId)
    }
    
    fun deleteUser(userId: Int): Boolean {
        invalidateUserTokens(userId)
        return userRepository.deleteUser(userId)
    }

    suspend fun deleteUserAsync(userId: Int): Boolean {
        invalidateUserTokens(userId)
        return userRepository.deleteUserAsync(userId)
    }
    
    fun hasAdmin(): Boolean {
        val users = userRepository.findAll()
        return hasAdmin(users)
    }

    suspend fun hasAdminAsync(): Boolean {
        val users = userRepository.findAllAsync()
        return hasAdmin(users)
    }
    
    fun createFirstAdmin(username: String, password: String, email: String?): User {
        // Check if admin already exists
        if (hasAdmin()) {
            throw IllegalStateException("Admin user already exists")
        }
        
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val admin = userRepository.create(username, hashedPassword, email, UserRole.ADMIN.value)
        initializeUserBookshelves(admin.id, failOnError = true)
        return admin
    }

    suspend fun createFirstAdminAsync(username: String, password: String, email: String?): User {
        if (hasAdminAsync()) {
            throw IllegalStateException("Admin user already exists")
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val admin = userRepository.createAsync(username, hashedPassword, email, UserRole.ADMIN.value)
        initializeUserBookshelvesAsync(admin.id, failOnError = true)
        return admin
    }

    private fun passwordMatches(password: String, user: User): Boolean {
        return BCrypt.checkpw(password, user.password)
    }

    private fun User.toLoginResponse(token: String): LoginResponse {
        return LoginResponse(
            token = token,
            user = UserResponse(id, username, email, role)
        )
    }

    private fun getCachedUser(token: String, nowMillis: Long): User? {
        val cached = tokenCache[token] ?: return null
        if (cached.expiresAtMillis > nowMillis) {
            return cached.user
        }
        tokenCache.remove(token, cached)
        return null
    }

    private fun cacheUser(token: String, user: User, nowMillis: Long) {
        tokenCache[token] = CachedUser(user, nowMillis + tokenCacheTtlMillis)
    }

    private fun pruneExpiredTokenCache(nowMillis: Long) {
        if (tokenCache.isEmpty()) return
        if (tokenCachePruneIntervalMillis <= 0) {
            removeExpiredTokenCacheEntries(nowMillis)
            lastTokenCachePruneMillis.set(nowMillis)
            return
        }

        val lastPrunedAt = lastTokenCachePruneMillis.get()
        if (nowMillis - lastPrunedAt < tokenCachePruneIntervalMillis) return
        if (lastTokenCachePruneMillis.compareAndSet(lastPrunedAt, nowMillis)) {
            removeExpiredTokenCacheEntries(nowMillis)
        }
    }

    private fun removeExpiredTokenCacheEntries(nowMillis: Long) {
        tokenCache.entries.removeIf { it.value.expiresAtMillis <= nowMillis }
    }

    private fun invalidateUserTokens(userId: Int) {
        tokenCache.entries.removeIf { it.value.user.id == userId }
    }

    internal fun cachedTokenCountForTesting(): Int = tokenCache.size

    private fun hasAdmin(users: List<User>): Boolean {
        return users.any { it.role == UserRole.ADMIN.value }
    }

    private fun initializeUserBookshelves(userId: Int, failOnError: Boolean) {
        val service = bookshelfService ?: return
        try {
            service.initializeUserBookshelves(userId)
        } catch (e: Exception) {
            if (failOnError) {
                throw e
            }
        }
    }

    private suspend fun initializeUserBookshelvesAsync(userId: Int, failOnError: Boolean) {
        val service = bookshelfService ?: return
        try {
            service.initializeUserBookshelvesAsync(userId)
        } catch (e: Exception) {
            if (failOnError) {
                throw e
            }
        }
    }
}
