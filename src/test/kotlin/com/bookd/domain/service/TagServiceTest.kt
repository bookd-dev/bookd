package com.bookd.domain.service

import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.TagRepository
import com.bookd.domain.model.Book
import com.bookd.domain.model.Tag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TagServiceTest {

    @Test
    fun `given filename tags when auto tagging from filename then tag links are batched`() {
        val tagRepository = mockk<TagRepository>()
        val bookRepository = mockk<BookRepository>()
        val service = TagService(tagRepository, bookRepository)
        val firstTag = Tag(id = 1, name = "科幻")
        val secondTag = Tag(id = 2, name = "冒险")

        every { bookRepository.findById(5) } returns Book(
            id = 5,
            title = "Book",
            author = null,
            format = "epub",
            filePath = "/library/Book-123-Author-科幻,冒险.epub",
            fileSize = 100
        )
        every { tagRepository.findOrCreateTags(listOf("科幻", "冒险")) } returns mapOf(
            "科幻" to firstTag,
            "冒险" to secondTag
        )
        every { tagRepository.addTagsToBook(5, match { it.toSet() == setOf(1, 2) }) } returns setOf(1, 2)

        val result = service.autoTagBookFromFilename(5)

        assertEquals(listOf(firstTag, secondTag), result)
        verify(exactly = 1) { tagRepository.findOrCreateTags(listOf("科幻", "冒险")) }
        verify(exactly = 1) { tagRepository.addTagsToBook(5, match { it.toSet() == setOf(1, 2) }) }
        verify(exactly = 0) { tagRepository.addTagToBook(any(), any()) }
    }
}
