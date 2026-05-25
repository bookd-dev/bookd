package com.bookd.domain.service

import com.bookd.data.repository.CoverDimensionCandidate
import com.bookd.data.repository.CoverDimensionUpdate
import com.bookd.data.repository.ImageDimensionMigrationRepository
import com.bookd.data.repository.ResourceDimensionCandidate
import com.bookd.data.repository.ResourceDimensionUpdate
import com.bookd.infrastructure.storage.BookImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageDimensionMigrationServiceTest {

    @Test
    fun `given resource candidates when dimensions extracted then success and failure counts are preserved`() = runBlocking {
        val imageStorage = mockk<BookImageStorage>()
        val repository = mockk<ImageDimensionMigrationRepository>()
        val service = ImageDimensionMigrationService(imageStorage, repository)

        coEvery { repository.findResourcesMissingDimensions() } returns listOf(
            ResourceDimensionCandidate(id = 1, storedPath = "1/ok.jpg"),
            ResourceDimensionCandidate(id = 2, storedPath = "1/unsupported.svg"),
            ResourceDimensionCandidate(id = 3, storedPath = "1/broken.jpg")
        )
        every { imageStorage.extractImageDimensionsFromFile("1/ok.jpg") } returns (640 to 320)
        every { imageStorage.extractImageDimensionsFromFile("1/unsupported.svg") } returns null
        every { imageStorage.extractImageDimensionsFromFile("1/broken.jpg") } throws IllegalStateException("broken")
        coEvery { repository.updateResourceDimensions(any()) } returns Unit

        val result = service.migrateResourceDimensions()

        assertEquals(MigrationResult(total = 3, successCount = 1, failedCount = 2), result)
        coVerify(exactly = 1) {
            repository.updateResourceDimensions(listOf(ResourceDimensionUpdate(id = 1, width = 640, height = 320)))
        }
    }

    @Test
    fun `given cover candidates when book image path is migrated then relative path is preserved`() = runBlocking {
        val imageStorage = mockk<BookImageStorage>()
        val repository = mockk<ImageDimensionMigrationRepository>()
        val service = ImageDimensionMigrationService(imageStorage, repository)

        coEvery { repository.findCoversMissingDimensions() } returns listOf(
            CoverDimensionCandidate(bookId = 7, coverPath = "/book_images/covers/cover.jpg")
        )
        every { imageStorage.extractImageDimensionsFromFile("covers/cover.jpg") } returns (300 to 500)
        coEvery { repository.updateCoverDimensions(any()) } returns Unit

        val result = service.migrateCoverDimensions()

        assertEquals(MigrationResult(total = 1, successCount = 1, failedCount = 0), result)
        coVerify(exactly = 1) {
            repository.updateCoverDimensions(listOf(CoverDimensionUpdate(bookId = 7, width = 300, height = 500)))
        }
    }

    @Test
    fun `given no migration candidates when migrating then empty update batches are written`() = runBlocking {
        val imageStorage = mockk<BookImageStorage>(relaxed = true)
        val repository = mockk<ImageDimensionMigrationRepository>()
        val service = ImageDimensionMigrationService(imageStorage, repository)

        coEvery { repository.findResourcesMissingDimensions() } returns emptyList()
        coEvery { repository.updateResourceDimensions(emptyList()) } returns Unit

        val result = service.migrateResourceDimensions()

        assertEquals(MigrationResult(total = 0, successCount = 0, failedCount = 0), result)
        verify(exactly = 0) { imageStorage.extractImageDimensionsFromFile(any()) }
        coVerify(exactly = 1) { repository.updateResourceDimensions(emptyList()) }
    }
}
