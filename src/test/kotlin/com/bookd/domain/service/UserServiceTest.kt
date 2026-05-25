package com.bookd.domain.service

import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UserServiceTest {

    @Test
    fun `given repeated valid token when validating then repository is queried once within cache ttl`() {
        val userRepository = mockk<UserRepository>()
        val userService = UserService(userRepository)
        val user = User(id = 1, username = "admin", password = "hash", email = null, role = "admin")

        every { userRepository.findUserByToken("token-1") } returns user

        assertEquals(user, userService.validateToken("token-1"))
        assertEquals(user, userService.validateToken("token-1"))

        verify(exactly = 1) { userRepository.findUserByToken("token-1") }
    }

    @Test
    fun `given cached token when logout then token cache is invalidated`() {
        val userRepository = mockk<UserRepository>()
        val userService = UserService(userRepository)
        val user = User(id = 1, username = "admin", password = "hash", email = null, role = "admin")

        every { userRepository.findUserByToken("token-1") } returns user andThen null
        every { userRepository.deleteSession("token-1") } returns true

        assertEquals(user, userService.validateToken("token-1"))
        userService.logout("token-1")
        assertNull(userService.validateToken("token-1"))

        verify(exactly = 2) { userRepository.findUserByToken("token-1") }
        verify(exactly = 1) { userRepository.deleteSession("token-1") }
    }
}
