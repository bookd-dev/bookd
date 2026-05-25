package com.bookd.domain.service

import com.bookd.data.repository.UserRepository
import com.bookd.domain.model.Bookshelf
import com.bookd.domain.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDateTime
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

    @Test
    fun `given first admin is created when bookshelf service exists then default bookshelf is initialized`() {
        val userRepository = mockk<UserRepository>()
        val bookshelfService = mockk<BookshelfService>()
        val userService = UserService(userRepository, bookshelfService)
        val admin = User(id = 1, username = "admin", password = "hash", email = null, role = "admin")

        every { userRepository.findAll() } returns emptyList()
        every { userRepository.create("admin", any(), null, "admin") } returns admin
        every { bookshelfService.initializeUserBookshelves(1) } returns Bookshelf(
            id = 10,
            userId = 1,
            name = BookshelfService.DEFAULT_BOOKSHELF_NAME,
            isSystemDefault = true,
            sortOrder = -1,
            createdAt = LocalDateTime(2026, 5, 25, 10, 0),
            updatedAt = LocalDateTime(2026, 5, 25, 10, 0)
        )

        assertEquals(admin, userService.createFirstAdmin("admin", "password", null))

        verify(exactly = 1) { bookshelfService.initializeUserBookshelves(1) }
    }
}
