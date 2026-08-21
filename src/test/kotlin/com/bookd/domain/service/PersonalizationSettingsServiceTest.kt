package com.bookd.domain.service

import com.bookd.data.entity.SystemSettings
import com.bookd.data.repository.SystemSettingsRepository
import com.bookd.infrastructure.time.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class PersonalizationSettingsServiceTest {

    @AfterEach
    fun tearDown() {
        TimeProvider.resetForTests(TimeZone.UTC)
    }

    @Test
    fun `given no saved time zone when initialized then TZ fallback is persisted and activated`() = runBlocking {
        connectDatabase("tz_fallback")
        transaction { SchemaUtils.create(SystemSettings) }
        val repository = SystemSettingsRepository()
        val service = PersonalizationSettingsService(repository) { name ->
            if (name == "TZ") "Asia/Tokyo" else null
        }

        val settings = service.initializeTimeZone()

        assertEquals("Asia/Tokyo", settings.timeZone)
        assertEquals("Asia/Tokyo", TimeProvider.timeZone.id)
        assertEquals("Asia/Tokyo", repository.getValue(PersonalizationSettingsService.TIME_ZONE_KEY))
    }

    @Test
    fun `given valid update when saving time zone then persisted value and runtime provider are updated`() = runBlocking {
        connectDatabase("tz_update")
        transaction { SchemaUtils.create(SystemSettings) }
        val repository = SystemSettingsRepository()
        val service = PersonalizationSettingsService(repository) { null }
        service.initializeTimeZone()

        val updated = service.updateTimeZone(com.bookd.domain.model.TimeZoneUpdateRequest("UTC"))

        assertEquals("UTC", updated.timeZone)
        assertEquals("UTC", TimeProvider.timeZone.id)
        assertEquals("UTC", repository.getValue(PersonalizationSettingsService.TIME_ZONE_KEY))
    }

    @Test
    fun `given invalid update when saving time zone then active time zone remains unchanged`() = runBlocking {
        connectDatabase("tz_invalid")
        transaction { SchemaUtils.create(SystemSettings) }
        val repository = SystemSettingsRepository()
        val service = PersonalizationSettingsService(repository) { "UTC" }
        service.initializeTimeZone()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.updateTimeZone(com.bookd.domain.model.TimeZoneUpdateRequest("Invalid/Zone"))
            }
        }

        assertEquals("UTC", TimeProvider.timeZone.id)
        assertEquals("UTC", repository.getValue(PersonalizationSettingsService.TIME_ZONE_KEY))
    }

    private fun connectDatabase(name: String) {
        Database.connect(
            url = "jdbc:h2:mem:personalization_settings_${name}_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
    }
}
