package com.bookd.domain.service.metadata

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File

class EpubTagExtractionTest {
    
    companion object {
        private const val TEST_EPUB_PATH = "/Users/shenchao/ebook/EBook/我怎么可能成为你的恋人 小说/我怎么可能成为你的恋人，不行不行！(※不是不可能！？) 4 - みかみてれん & 竹嶋えく.epub"
        
        @JvmStatic
        fun testEpubExists(): Boolean = File(TEST_EPUB_PATH).exists()
    }
    
    @Test
    @EnabledIf("testEpubExists")
    fun `should extract tags from EPUB dc subject fields`() {
        // Given
        val extractor = EpubMetadataExtractor()
        val epubFile = File(TEST_EPUB_PATH)
        
        // When
        val metadata = extractor.extractMetadata(epubFile)
        
        // Then
        assertNotNull(metadata, "Metadata should be extracted")
        
        metadata?.let {
            // Verify basic metadata
            assertEquals("我怎么可能成为你的恋人，不行不行！(※不是不可能！？)4", it.title)
            assertEquals("みかみてれん", it.author)
            assertEquals("集英社", it.publisher)
            
            // Verify tags extraction
            val expectedTags = setOf("校园", "欢乐向", "恋爱", "百合", "女性视角", "青梅竹马")
            val extractedTags = it.tags.toSet()
            
            assertEquals(6, it.tags.size, "Should extract 6 tags")
            assertEquals(expectedTags, extractedTags, "Should extract all expected tags")
            
            println("✅ Successfully extracted tags: ${it.tags.joinToString(", ")}")
        }
    }
    
    @Test
    fun `BookMetadata should support tags field`() {
        // Given
        val tags = listOf("fantasy", "adventure", "magic")
        
        // When
        val metadata = BookMetadata(
            title = "Test Book",
            author = "Test Author",
            publisher = "Test Publisher",
            description = "Test Description",
            isbn = "1234567890",
            tags = tags
        )
        
        // Then
        assertEquals(tags, metadata.tags)
        assertEquals(3, metadata.tags.size)
    }
    
    @Test
    fun `BookMetadata tags should default to empty list`() {
        // When
        val metadata = BookMetadata(
            title = "Test Book",
            author = "Test Author"
        )
        
        // Then
        assertTrue(metadata.tags.isEmpty())
        assertEquals(emptyList<String>(), metadata.tags)
    }
}
