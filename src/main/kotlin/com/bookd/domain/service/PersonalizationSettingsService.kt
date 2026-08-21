package com.bookd.domain.service

import com.bookd.data.repository.SystemSettingsRepository
import com.bookd.domain.model.PersonalizationSettingsResponse
import com.bookd.domain.model.TimeZoneUpdateRequest
import com.bookd.infrastructure.time.TimeProvider

class PersonalizationSettingsService(
    private val settingsRepository: SystemSettingsRepository,
    private val environment: (String) -> String? = System::getenv
) {
    suspend fun initializeTimeZone(): PersonalizationSettingsResponse {
        val saved = settingsRepository.getValue(TIME_ZONE_KEY)
        val resolved = saved
            ?.takeIf { it.isNotBlank() }
            ?.let { TimeProvider.parseTimeZone(it) }
            ?: TimeProvider.resolveDefaultTimeZone(environment)

        if (saved.isNullOrBlank()) {
            settingsRepository.upsertValue(TIME_ZONE_KEY, resolved.id)
        }

        TimeProvider.setTimeZone(resolved)
        return PersonalizationSettingsResponse(timeZone = resolved.id)
    }

    suspend fun getSettings(): PersonalizationSettingsResponse {
        val saved = settingsRepository.getValue(TIME_ZONE_KEY)
        if (!saved.isNullOrBlank()) {
            return PersonalizationSettingsResponse(timeZone = saved)
        }
        return initializeTimeZone()
    }

    suspend fun updateTimeZone(request: TimeZoneUpdateRequest): PersonalizationSettingsResponse {
        val timeZoneId = request.timeZone.trim()
        require(timeZoneId.isNotBlank()) { "Time zone is required" }
        val timeZone = TimeProvider.parseTimeZone(timeZoneId)
            ?: throw IllegalArgumentException("Invalid time zone: $timeZoneId")

        settingsRepository.upsertValue(TIME_ZONE_KEY, timeZone.id)
        TimeProvider.setTimeZone(timeZone)
        return PersonalizationSettingsResponse(timeZone = timeZone.id)
    }

    companion object {
        const val TIME_ZONE_KEY = "time_zone"
    }
}
