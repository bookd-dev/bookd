package com.bookd.domain.service

import com.bookd.data.repository.TagRepository
import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.Tag
import com.bookd.domain.service.metadata.MetadataExtractorFactory
import org.slf4j.LoggerFactory
import java.io.File

class TagService(
    private val tagRepository: TagRepository,
    private val bookRepository: BookRepository
) {
    private val logger = LoggerFactory.getLogger(TagService::class.java)
    private val extractorFactory = MetadataExtractorFactory()
    
    /**
     * Extract tags from filename
     * Pattern: filename ends with -数字-作者-标签1,标签2,标签3.ext
     * Example: 书名-11600360-作者-附身,性转换,夺取.epub
     */
    fun extractTagsFromFilename(filename: String): List<String> {
        try {
            // Remove extension
            val nameWithoutExt = filename.substringBeforeLast('.')
            
            // Split by last dash to get the tag part
            val parts = nameWithoutExt.split('-')
            if (parts.size < 2) return emptyList()
            
            // The last part should be tags
            val tagPart = parts.last().trim()
            if (tagPart.isEmpty()) return emptyList()
            
            // Split by comma to get individual tags
            val tags = tagPart.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 50 } // Filter out invalid tags
                .distinct()
            
            return tags
        } catch (e: Exception) {
            logger.warn("Failed to extract tags from filename: $filename - ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Auto-tag a book based on its filename (legacy method)
     */
    fun autoTagBookFromFilename(bookId: Int): List<Tag> {
        val book = bookRepository.findById(bookId) ?: return emptyList()
        val filename = File(book.filePath).name
        val tagNames = extractTagsFromFilename(filename)
        
        if (tagNames.isEmpty()) return emptyList()

        return addTagsToBook(bookId, tagNames)
    }

    suspend fun autoTagBookFromFilenameAsync(bookId: Int): List<Tag> {
        val book = bookRepository.findByIdAsync(bookId) ?: return emptyList()
        val filename = File(book.filePath).name
        val tagNames = extractTagsFromFilename(filename)

        if (tagNames.isEmpty()) return emptyList()

        return addTagsToBookAsync(bookId, tagNames)
    }
    
    /**
     * Auto-tag a book from metadata (new method)
     */
    fun autoTagBook(bookId: Int): List<Tag> {
        val book = bookRepository.findById(bookId) ?: return emptyList()
        val file = File(book.filePath)
        
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist: ${book.filePath}")
            return emptyList()
        }
        
        return try {
            // 使用工厂创建对应格式的提取器
            val extractor = extractorFactory.createExtractor(file)
            
            // 提取元数据
            val metadata = extractor.extractMetadata(file)
            
            if (metadata == null || metadata.tags.isEmpty()) {
                logger.debug("No tags found in metadata for book $bookId (${file.name})")
                return emptyList()
            }
            
            logger.info("Extracting ${metadata.tags.size} tags from metadata for book $bookId: ${metadata.tags.joinToString(", ")}")
            
            val tags = addTagsToBook(bookId, metadata.tags)
            val addedCount = tags.size
            
            if (addedCount > 0) {
                logger.info("Successfully added $addedCount tags to book ID: $bookId")
            }
            tags
        } catch (e: Exception) {
            logger.error("Failed to extract tags from metadata for book $bookId: ${e.message}")
            emptyList()
        }
    }

    suspend fun autoTagBookAsync(bookId: Int): List<Tag> {
        val book = bookRepository.findByIdAsync(bookId) ?: return emptyList()
        val file = File(book.filePath)

        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist: ${book.filePath}")
            return emptyList()
        }

        return try {
            val extractor = extractorFactory.createExtractor(file)
            val metadata = extractor.extractMetadata(file)

            if (metadata == null || metadata.tags.isEmpty()) {
                logger.debug("No tags found in metadata for book $bookId (${file.name})")
                return emptyList()
            }

            logger.info("Extracting ${metadata.tags.size} tags from metadata for book $bookId: ${metadata.tags.joinToString(", ")}")

            val tags = addTagsToBookAsync(bookId, metadata.tags)
            val addedCount = tags.size

            if (addedCount > 0) {
                logger.info("Successfully added $addedCount tags to book ID: $bookId")
            }
            tags
        } catch (e: Exception) {
            logger.error("Failed to extract tags from metadata for book $bookId: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Auto-tag all books from metadata
     */
    fun autoTagAllBooks(): Map<String, Int> {
        val books = bookRepository.findAll()
        var booksTagged = 0
        var tagsCreated = 0
        val initialTagCount = tagRepository.getAllTags().size
        
        logger.info("Starting auto-tagging for ${books.size} books from metadata")
        
        for (book in books) {
            try {
                val tags = autoTagBook(book.id)
                if (tags.isNotEmpty()) {
                    booksTagged++
                    logger.debug("Tagged book ${book.id} (${book.title}) with ${tags.size} tags")
                }
            } catch (e: Exception) {
                logger.error("Failed to auto-tag book ${book.id}: ${e.message}")
            }
        }
        
        val finalTagCount = tagRepository.getAllTags().size
        tagsCreated = finalTagCount - initialTagCount
        
        logger.info("Auto-tagging completed: $booksTagged books tagged, $tagsCreated new tags created")
        
        return mapOf(
            "booksTagged" to booksTagged,
            "tagsCreated" to tagsCreated,
            "totalBooks" to books.size
        )
    }

    suspend fun autoTagAllBooksAsync(): Map<String, Int> {
        val books = bookRepository.findAllAsync()
        var booksTagged = 0
        val initialTagCount = tagRepository.getAllTagsAsync().size

        logger.info("Starting auto-tagging for ${books.size} books from metadata")

        for (book in books) {
            try {
                val tags = autoTagBookAsync(book.id)
                if (tags.isNotEmpty()) {
                    booksTagged++
                    logger.debug("Tagged book ${book.id} (${book.title}) with ${tags.size} tags")
                }
            } catch (e: Exception) {
                logger.error("Failed to auto-tag book ${book.id}: ${e.message}")
            }
        }

        val finalTagCount = tagRepository.getAllTagsAsync().size
        val tagsCreated = finalTagCount - initialTagCount

        logger.info("Auto-tagging completed: $booksTagged books tagged, $tagsCreated new tags created")

        return mapOf(
            "booksTagged" to booksTagged,
            "tagsCreated" to tagsCreated,
            "totalBooks" to books.size
        )
    }
    
    /**
     * Get all tags
     */
    fun getAllTags(): List<Tag> {
        return tagRepository.getAllTags()
    }

    suspend fun getAllTagsAsync(): List<Tag> {
        return tagRepository.getAllTagsAsync()
    }
    
    /**
     * Get tags for a book
     */
    fun getTagsForBook(bookId: Int): List<Tag> {
        return tagRepository.getTagsByBookId(bookId)
    }

    suspend fun getTagsForBookAsync(bookId: Int): List<Tag> {
        return tagRepository.getTagsByBookIdAsync(bookId)
    }
    
    /**
     * Add tag to book
     */
    fun addTagToBook(bookId: Int, tagName: String): Tag {
        val tag = tagRepository.findOrCreateTag(tagName)
        tagRepository.addTagToBook(bookId, tag.id)
        return tag
    }

    suspend fun addTagToBookAsync(bookId: Int, tagName: String): Tag {
        val tag = tagRepository.findOrCreateTagAsync(tagName)
        tagRepository.addTagToBookAsync(bookId, tag.id)
        return tag
    }
    
    /**
     * Remove tag from book
     */
    fun removeTagFromBook(bookId: Int, tagId: Int): Boolean {
        return tagRepository.removeTagFromBook(bookId, tagId)
    }

    suspend fun removeTagFromBookAsync(bookId: Int, tagId: Int): Boolean {
        return tagRepository.removeTagFromBookAsync(bookId, tagId)
    }
    
    /**
     * Delete a tag (and remove from all books)
     */
    fun deleteTag(tagId: Int): Boolean {
        return tagRepository.deleteTag(tagId)
    }

    suspend fun deleteTagAsync(tagId: Int): Boolean {
        return tagRepository.deleteTagAsync(tagId)
    }
    
    /**
     * Get tag statistics (tag -> book count)
     */
    fun getTagStats(): Map<Tag, Int> {
        val stats = tagRepository.getTagStats()
        val tags = tagRepository.getAllTags()
        return tags.associateWith { stats[it.id] ?: 0 }
    }

    suspend fun getTagStatsAsync(): Map<Tag, Int> {
        val stats = tagRepository.getTagStatsAsync()
        val tags = tagRepository.getAllTagsAsync()
        return tags.associateWith { stats[it.id] ?: 0 }
    }
    
    /**
     * Get books by tag
     */
    fun getBooksByTagId(tagId: Int): List<Int> {
        return tagRepository.getBookIdsByTagId(tagId)
    }

    suspend fun getBooksByTagIdAsync(tagId: Int): List<Int> {
        return tagRepository.getBookIdsByTagIdAsync(tagId)
    }
    
    /**
     * Create a new tag manually
     */
    fun createTag(tagName: String): Tag {
        return tagRepository.findOrCreateTag(tagName)
    }

    suspend fun createTagAsync(tagName: String): Tag {
        return tagRepository.findOrCreateTagAsync(tagName)
    }
    
    /**
     * Merge multiple tags into one target tag
     * All books from source tags will be tagged with the target tag
     * Source tags will be deleted after merge
     */
    fun mergeTags(sourceTagIds: List<Int>, targetTagName: String): Tag {
        // Create or get target tag
        val targetTag = tagRepository.findOrCreateTag(targetTagName)
        
        // For each source tag
        for (sourceTagId in sourceTagIds) {
            // Skip if source is the same as target
            if (sourceTagId == targetTag.id) continue
            
            // Get all books with this source tag
            val bookIds = tagRepository.getBookIdsByTagId(sourceTagId)
            
            // Add target tag to all these books
            tagRepository.addTagToBooks(bookIds, targetTag.id)
            
            // Delete the source tag
            tagRepository.deleteTag(sourceTagId)
        }
        
        return targetTag
    }

    suspend fun mergeTagsAsync(sourceTagIds: List<Int>, targetTagName: String): Tag {
        val targetTag = tagRepository.findOrCreateTagAsync(targetTagName)

        for (sourceTagId in sourceTagIds) {
            if (sourceTagId == targetTag.id) continue

            val bookIds = tagRepository.getBookIdsByTagIdAsync(sourceTagId)
            tagRepository.addTagToBooksAsync(bookIds, targetTag.id)
            tagRepository.deleteTagAsync(sourceTagId)
        }

        return targetTag
    }

    private fun addTagsToBook(bookId: Int, tagNames: Collection<String>): List<Tag> {
        val tagsByName = tagRepository.findOrCreateTags(tagNames)
        if (tagsByName.isEmpty()) return emptyList()

        val tagsById = tagsByName.values.associateBy { it.id }
        val addedTagIds = tagRepository.addTagsToBook(bookId, tagsById.keys)
        return addedTagIds.mapNotNull { tagsById[it] }
    }

    private suspend fun addTagsToBookAsync(bookId: Int, tagNames: Collection<String>): List<Tag> {
        val tagsByName = tagRepository.findOrCreateTagsAsync(tagNames)
        if (tagsByName.isEmpty()) return emptyList()

        val tagsById = tagsByName.values.associateBy { it.id }
        val addedTagIds = tagRepository.addTagsToBookAsync(bookId, tagsById.keys)
        return addedTagIds.mapNotNull { tagsById[it] }
    }
}
