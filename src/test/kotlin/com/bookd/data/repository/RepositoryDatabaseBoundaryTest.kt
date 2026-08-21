package com.bookd.data.repository

import com.bookd.data.entity.BookSources
import com.bookd.data.entity.Bookmarks
import com.bookd.data.entity.Books
import com.bookd.data.entity.ReaderSettings
import com.bookd.data.entity.ReadingProgress
import com.bookd.data.entity.TxtParseRules
import com.bookd.data.entity.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RepositoryDatabaseBoundaryTest {

    @Test
    fun `given txt rule repository when using suspend methods then rules are persisted`() = runBlocking {
        connectDatabase("txt_rules")
        transaction { SchemaUtils.create(TxtParseRules) }
        val repository = TxtParseRuleRepository()

        val created = repository.createRule(
            TxtParseRuleRepository.CreateTxtParseRuleDTO(
                name = "Chapter",
                rule = "^Chapter \\d+",
                example = "Chapter 1",
                enabled = true,
                priority = 1
            )
        )

        assertEquals("Chapter", repository.getRuleById(created.id)?.name)
        assertEquals(1, repository.getEnabledRules().size)
        assertTrue(repository.toggleRuleEnabled(created.id))
        assertEquals(0, repository.getEnabledRules().size)
        assertTrue(repository.updatePriorities(mapOf(created.id to 10)))
        assertEquals(10, repository.getRuleById(created.id)?.priority)
    }

    @Test
    fun `given source repository async methods when mutating source then behavior is preserved`() = runBlocking {
        connectDatabase("sources")
        transaction { SchemaUtils.create(BookSources) }
        val repository = BookSourceRepository()

        val source = repository.createAsync("Main", "/books")

        assertEquals(listOf(source), repository.findAllAsync())
        assertEquals(source, repository.findByIdAsync(source.id))
        assertTrue(repository.toggleEnabledAsync(source.id))
        assertFalse(repository.findByIdAsync(source.id)?.enabled ?: true)
        assertTrue(repository.deleteAsync(source.id))
        assertEquals(null, repository.findByIdAsync(source.id))
    }

    @Test
    fun `given reading repositories when using suspend methods then data is persisted`() = runBlocking {
        connectDatabase("reading")
        transaction {
            SchemaUtils.create(Users, BookSources, Books, ReadingProgress, Bookmarks, ReaderSettings)
        }
        val user = UserRepository().create("reader", "hash", null)
        val book = BookRepository().create(
            title = "Reading Book",
            author = null,
            format = "epub",
            filePath = "/tmp/reading-book.epub",
            fileSize = 1024L
        )
        val progressRepository = ReadingProgressRepository()
        val bookmarkRepository = BookmarkRepository()
        val settingsRepository = ReaderSettingsRepository()

        val progress = progressRepository.upsert(
            userId = user.id,
            bookId = book.id,
            progress = 0.25,
            currentPage = 4,
            totalPages = 20,
            cfiLocation = null,
            documentId = null,
            deviceId = null,
            anchorId = "epub:chapter#p4",
            paragraphIndex = 7,
            scrollOffset = 12
        )
        val bookmark = bookmarkRepository.create(
            userId = user.id,
            bookId = book.id,
            positionType = "anchor",
            positionValue = "anchor:4:epub:chapter#p4:7:12",
            documentId = null,
            chapterIndex = 4,
            anchorId = "epub:chapter#p4",
            paragraphIndex = 7,
            scrollOffset = 12,
            title = "Page 4",
            note = null,
            color = "#FFD700"
        )
        val settings = settingsRepository.upsert(
            userId = user.id,
            fontFamily = "serif",
            fontSize = 18,
            fontWeight = null,
            lineHeight = null,
            letterSpacing = null,
            paragraphSpacing = null,
            textAlign = null,
            pageMode = null,
            brightness = null,
            marginHorizontal = null,
            marginVertical = null,
            firstLineIndent = null,
            pageAnimationType = null
        )

        val savedProgress = progressRepository.findByUserAndBook(user.id, book.id)
        assertEquals(progress.id, savedProgress?.id)
        assertEquals(4, savedProgress?.chapterIndex)
        assertEquals("epub:chapter#p4", savedProgress?.anchorId)
        assertEquals(7, savedProgress?.paragraphIndex)
        assertEquals(12, savedProgress?.scrollOffset)

        progressRepository.upsert(
            userId = user.id,
            bookId = book.id,
            progress = 0.3,
            currentPage = 5,
            totalPages = 20,
            cfiLocation = null,
            documentId = null,
            deviceId = null,
            anchorId = null,
            paragraphIndex = null,
            scrollOffset = null,
            chapterPageIndex = null,
            chapterTotalPages = null,
            chapterScrollPercent = null
        )

        val chapterTopProgress = progressRepository.findByUserAndBook(user.id, book.id)
        assertEquals(5, chapterTopProgress?.chapterIndex)
        assertEquals(null, chapterTopProgress?.anchorId)
        assertEquals(null, chapterTopProgress?.paragraphIndex)
        assertEquals(null, chapterTopProgress?.scrollOffset)
        assertEquals(null, chapterTopProgress?.chapterPageIndex)
        assertEquals(null, chapterTopProgress?.chapterTotalPages)
        assertEquals(null, chapterTopProgress?.chapterScrollPercent)
        assertEquals(1, progressRepository.getReadingHistory(user.id).size)
        val savedBookmark = bookmarkRepository.findByUserAndBook(user.id, book.id).single()
        assertEquals(bookmark.id, savedBookmark.id)
        assertEquals(4, savedBookmark.chapterIndex)
        assertEquals("epub:chapter#p4", savedBookmark.anchorId)
        assertEquals(7, savedBookmark.paragraphIndex)
        assertEquals(12, savedBookmark.scrollOffset)
        assertNotNull(bookmarkRepository.update(bookmark.id, user.id, title = "Updated", note = null, color = null))
        assertEquals(settings.id, settingsRepository.findByUser(user.id)?.id)
        assertTrue(bookmarkRepository.delete(bookmark.id, user.id))
    }

    private fun connectDatabase(name: String) {
        Database.connect(
            url = "jdbc:h2:mem:repo_boundary_${name}_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
    }
}
