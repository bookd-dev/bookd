package com.bookd.domain.service

import com.bookd.data.repository.BookmarkRepository
import com.bookd.data.repository.ReaderSettingsRepository
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.domain.model.*

class ReadingService(
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readerSettingsRepository: ReaderSettingsRepository
) {
    // ============ 阅读进度 ============
    
    fun getProgress(userId: Int, bookId: Int): ReadingProgressResponse? {
        return readingProgressRepository.findByUserAndBook(userId, bookId)
    }
    
    fun updateProgress(userId: Int, bookId: Int, dto: ReadingProgressDTO): ReadingProgressResponse {
        return readingProgressRepository.upsert(
            userId = userId,
            bookId = bookId,
            progress = dto.progress,
            currentPage = dto.currentPage,
            totalPages = dto.totalPages,
            cfiLocation = dto.cfiLocation,
            documentId = dto.documentId,
            deviceId = dto.deviceId,
            chapterPageIndex = dto.chapterPageIndex,
            chapterTotalPages = dto.chapterTotalPages,
            chapterScrollPercent = dto.chapterScrollPercent
        )
    }
    
    fun getReadingHistory(userId: Int, limit: Int = 20): List<ReadingHistoryItemDTO> {
        return readingProgressRepository.getReadingHistory(userId, limit).map { item ->
            ReadingHistoryItemDTO(
                bookId = item.progress.bookId,
                bookTitle = item.bookTitle,
                bookAuthor = item.bookAuthor,
                bookCoverPath = item.bookCoverPath,
                bookFormat = item.bookFormat,
                progress = item.progress.progress,
                currentPage = item.progress.currentPage,
                totalPages = item.progress.totalPages,
                lastReadAt = item.progress.lastReadAt.toString()
            )
        }
    }
    
    // ============ 书签 ============
    
    fun getBookmarks(userId: Int, bookId: Int): List<BookmarkResponse> {
        return bookmarkRepository.findByUserAndBook(userId, bookId)
    }
    
    fun getAllUserBookmarks(userId: Int, limit: Int = 100, offset: Long = 0): List<BookmarkResponse> {
        return bookmarkRepository.findByUser(userId, limit, offset)
    }
    
    fun addBookmark(userId: Int, bookId: Int, dto: BookmarkDTO): BookmarkResponse {
        return bookmarkRepository.create(
            userId = userId,
            bookId = bookId,
            positionType = dto.positionType,
            positionValue = dto.positionValue,
            documentId = dto.documentId,
            title = dto.title,
            note = dto.note,
            color = dto.color
        )
    }
    
    fun updateBookmark(userId: Int, bookmarkId: Int, dto: BookmarkDTO): BookmarkResponse? {
        return bookmarkRepository.update(
            id = bookmarkId,
            userId = userId,
            title = dto.title,
            note = dto.note,
            color = dto.color
        )
    }
    
    fun deleteBookmark(userId: Int, bookmarkId: Int): Boolean {
        return bookmarkRepository.delete(bookmarkId, userId)
    }
    
    // ============ 阅读器设置 ============
    
    fun getReaderSettings(userId: Int): ReaderSettingsResponse? {
        return readerSettingsRepository.findByUser(userId)
    }
    
    fun updateReaderSettings(userId: Int, dto: ReaderSettingsDTO): ReaderSettingsResponse {
        return readerSettingsRepository.upsert(
            userId = userId,
            fontFamily = dto.fontFamily,
            fontSize = dto.fontSize,
            fontWeight = dto.fontWeight,
            lineHeight = dto.lineHeight,
            letterSpacing = dto.letterSpacing,
            paragraphSpacing = dto.paragraphSpacing,
            textAlign = dto.textAlign,
            pageMode = dto.pageMode,
            brightness = dto.brightness,
            marginHorizontal = dto.marginHorizontal,
            marginVertical = dto.marginVertical,
            firstLineIndent = dto.firstLineIndent,
            pageAnimationType = dto.pageAnimationType
        )
    }
    
    fun patchReaderSettings(
        userId: Int,
        fontFamily: String? = null,
        fontSize: Int? = null,
        fontWeight: Int? = null,
        lineHeight: Double? = null,
        letterSpacing: Double? = null,
        paragraphSpacing: Int? = null,
        textAlign: String? = null,
        pageMode: String? = null,
        brightness: Int? = null,
        marginHorizontal: Int? = null,
        marginVertical: Int? = null,
        firstLineIndent: Boolean? = null,
        pageAnimationType: String? = null
    ): ReaderSettingsResponse {
        return readerSettingsRepository.upsert(
            userId = userId,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            paragraphSpacing = paragraphSpacing,
            textAlign = textAlign,
            pageMode = pageMode,
            brightness = brightness,
            marginHorizontal = marginHorizontal,
            marginVertical = marginVertical,
            firstLineIndent = firstLineIndent,
            pageAnimationType = pageAnimationType
        )
    }
}
