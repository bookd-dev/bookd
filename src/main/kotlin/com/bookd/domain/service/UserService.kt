package com.bookd.domain.service

import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.User

class UserService(
    private val userRepository: UserRepository
) {
    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }
    
    fun createUser(username: String, password: String, email: String?): User {
        // TODO: Hash password before storing
        return userRepository.create(username, password, email)
    }
}
