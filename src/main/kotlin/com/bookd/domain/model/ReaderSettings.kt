package com.bookd.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ReaderSettingsDTO(
    val fontFamily: String = "system-ui",
    val fontSize: Int = 16,
    val fontWeight: Int = 400,
    val lineHeight: Double = 1.6,
    val letterSpacing: Double = 0.0,
    val paragraphSpacing: Int = 16,
    val textAlign: String = "justify",
    val theme: String = "light",
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#333333",
    val pageMode: String = "scroll",
    val brightness: Int = 100,
    val marginHorizontal: Int = 20,
    val marginVertical: Int = 40
)

@Serializable
data class ReaderSettingsResponse(
    val id: Int,
    val userId: Int,
    val fontFamily: String,
    val fontSize: Int,
    val fontWeight: Int,
    val lineHeight: Double,
    val letterSpacing: Double,
    val paragraphSpacing: Int,
    val textAlign: String,
    val theme: String,
    val backgroundColor: String,
    val textColor: String,
    val pageMode: String,
    val brightness: Int,
    val marginHorizontal: Int,
    val marginVertical: Int,
    val updatedAt: LocalDateTime
)
