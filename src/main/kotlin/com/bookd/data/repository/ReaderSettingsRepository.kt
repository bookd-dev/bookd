package com.bookd.data.repository

import com.bookd.data.entity.ReaderSettings
import com.bookd.domain.model.ReaderSettingsResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

class ReaderSettingsRepository {
    
    fun findByUser(userId: Int): ReaderSettingsResponse? = transaction {
        ReaderSettings.selectAll()
            .where { ReaderSettings.userId eq userId }
            .map { toResponse(it, userId) }
            .singleOrNull()
    }
    
    fun upsert(
        userId: Int,
        fontFamily: String?,
        fontSize: Int?,
        fontWeight: Int?,
        lineHeight: Double?,
        letterSpacing: Double?,
        paragraphSpacing: Int?,
        textAlign: String?,
        theme: String?,
        backgroundColor: String?,
        textColor: String?,
        pageMode: String?,
        brightness: Int?,
        marginHorizontal: Int?,
        marginVertical: Int?
    ): ReaderSettingsResponse = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        
        val existing = ReaderSettings.selectAll()
            .where { ReaderSettings.userId eq userId }
            .singleOrNull()
        
        if (existing != null) {
            ReaderSettings.update({ ReaderSettings.userId eq userId }) {
                if (fontFamily != null) it[ReaderSettings.fontFamily] = fontFamily
                if (fontSize != null) it[ReaderSettings.fontSize] = fontSize
                if (fontWeight != null) it[ReaderSettings.fontWeight] = fontWeight
                if (lineHeight != null) it[ReaderSettings.lineHeight] = BigDecimal.valueOf(lineHeight)
                if (letterSpacing != null) it[ReaderSettings.letterSpacing] = BigDecimal.valueOf(letterSpacing)
                if (paragraphSpacing != null) it[ReaderSettings.paragraphSpacing] = paragraphSpacing
                if (textAlign != null) it[ReaderSettings.textAlign] = textAlign
                if (theme != null) it[ReaderSettings.theme] = theme
                if (backgroundColor != null) it[ReaderSettings.backgroundColor] = backgroundColor
                if (textColor != null) it[ReaderSettings.textColor] = textColor
                if (pageMode != null) it[ReaderSettings.pageMode] = pageMode
                if (brightness != null) it[ReaderSettings.brightness] = brightness
                if (marginHorizontal != null) it[ReaderSettings.marginHorizontal] = marginHorizontal
                if (marginVertical != null) it[ReaderSettings.marginVertical] = marginVertical
                it[updatedAt] = now
            }
        } else {
            ReaderSettings.insert {
                it[ReaderSettings.userId] = userId
                it[ReaderSettings.fontFamily] = fontFamily ?: "system-ui"
                it[ReaderSettings.fontSize] = fontSize ?: 16
                it[ReaderSettings.fontWeight] = fontWeight ?: 400
                it[ReaderSettings.lineHeight] = BigDecimal.valueOf(lineHeight ?: 1.6)
                it[ReaderSettings.letterSpacing] = BigDecimal.valueOf(letterSpacing ?: 0.0)
                it[ReaderSettings.paragraphSpacing] = paragraphSpacing ?: 16
                it[ReaderSettings.textAlign] = textAlign ?: "justify"
                it[ReaderSettings.theme] = theme ?: "light"
                it[ReaderSettings.backgroundColor] = backgroundColor ?: "#FFFFFF"
                it[ReaderSettings.textColor] = textColor ?: "#333333"
                it[ReaderSettings.pageMode] = pageMode ?: "scroll"
                it[ReaderSettings.brightness] = brightness ?: 100
                it[ReaderSettings.marginHorizontal] = marginHorizontal ?: 20
                it[ReaderSettings.marginVertical] = marginVertical ?: 40
                it[updatedAt] = now
            }
        }
        
        findByUser(userId)!!
    }
    
    private fun toResponse(row: ResultRow, userId: Int) = ReaderSettingsResponse(
        id = row[ReaderSettings.id].value,
        userId = userId,
        fontFamily = row[ReaderSettings.fontFamily],
        fontSize = row[ReaderSettings.fontSize],
        fontWeight = row[ReaderSettings.fontWeight],
        lineHeight = row[ReaderSettings.lineHeight].toDouble(),
        letterSpacing = row[ReaderSettings.letterSpacing].toDouble(),
        paragraphSpacing = row[ReaderSettings.paragraphSpacing],
        textAlign = row[ReaderSettings.textAlign],
        theme = row[ReaderSettings.theme],
        backgroundColor = row[ReaderSettings.backgroundColor],
        textColor = row[ReaderSettings.textColor],
        pageMode = row[ReaderSettings.pageMode],
        brightness = row[ReaderSettings.brightness],
        marginHorizontal = row[ReaderSettings.marginHorizontal],
        marginVertical = row[ReaderSettings.marginVertical],
        updatedAt = row[ReaderSettings.updatedAt]
    )
}
