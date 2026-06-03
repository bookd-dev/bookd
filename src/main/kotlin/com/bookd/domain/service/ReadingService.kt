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
    
    suspend fun getProgress(userId: Int, bookId: Int): ReadingProgressResponse? {
        return readingProgressRepository.findByUserAndBook(userId, bookId)
    }
    
    suspend fun updateProgress(userId: Int, bookId: Int, dto: ReadingProgressDTO): ReadingProgressResponse {
        return readingProgressRepository.upsert(
            userId = userId,
            bookId = bookId,
            progress = dto.progress,
            currentPage = dto.chapterIndex ?: dto.currentPage,
            totalPages = dto.totalPages,
            cfiLocation = dto.cfiLocation,
            documentId = dto.documentId,
            deviceId = dto.deviceId,
            anchorId = dto.anchorId,
            paragraphIndex = dto.paragraphIndex,
            scrollOffset = dto.scrollOffset,
            chapterPageIndex = dto.chapterPageIndex,
            chapterTotalPages = dto.chapterTotalPages,
            chapterScrollPercent = dto.chapterScrollPercent
        )
    }
    
    suspend fun getReadingHistory(userId: Int, limit: Int = 20): List<ReadingHistoryItemDTO> {
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
    
    suspend fun getBookmarks(userId: Int, bookId: Int): List<BookmarkResponse> {
        return bookmarkRepository.findByUserAndBook(userId, bookId)
    }
    
    suspend fun getAllUserBookmarks(userId: Int, limit: Int = 100, offset: Long = 0): List<BookmarkResponse> {
        return bookmarkRepository.findByUser(userId, limit, offset)
    }
    
    suspend fun addBookmark(userId: Int, bookId: Int, dto: BookmarkDTO): BookmarkResponse {
        return bookmarkRepository.create(
            userId = userId,
            bookId = bookId,
            positionType = dto.positionType ?: if (dto.anchorId != null) "anchor" else "page",
            positionValue = dto.positionValue ?: buildBookmarkPositionValue(dto),
            documentId = dto.documentId,
            chapterIndex = dto.chapterIndex,
            anchorId = dto.anchorId,
            paragraphIndex = dto.paragraphIndex,
            scrollOffset = dto.scrollOffset,
            title = dto.title,
            note = dto.note,
            color = dto.color
        )
    }
    
    suspend fun updateBookmark(userId: Int, bookmarkId: Int, dto: BookmarkDTO): BookmarkResponse? {
        return bookmarkRepository.update(
            id = bookmarkId,
            userId = userId,
            title = dto.title,
            note = dto.note,
            color = dto.color
        )
    }
    
    suspend fun deleteBookmark(userId: Int, bookmarkId: Int): Boolean {
        return bookmarkRepository.delete(bookmarkId, userId)
    }

    private fun buildBookmarkPositionValue(dto: BookmarkDTO): String {
        val chapter = dto.chapterIndex ?: 0
        val fallback = dto.paragraphIndex ?: 0
        val offset = dto.scrollOffset ?: 0
        return dto.anchorId?.let { "anchor:$chapter:$it:$fallback:$offset" }
            ?: "chapter:$chapter:$fallback:$offset"
    }
    
    // ============ 阅读器设置 ============
    
    suspend fun getReaderSettings(userId: Int): ReaderSettingsResponse? {
        return readerSettingsRepository.findByUser(userId)
    }
    
    suspend fun updateReaderSettings(userId: Int, dto: ReaderSettingsDTO): ReaderSettingsResponse {
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
    
    suspend fun patchReaderSettings(
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
