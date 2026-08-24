package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.mobi.MobiTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MobiMetadataExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given EXTH metadata when extracted then direct fields are returned without normalization`() {
        val file = MobiTestFixtures.createMobi(tempDir.resolve("metadata.mobi")).toFile()
        val extractor = MobiMetadataExtractor(
            normalizer = { error("normalizer must not be called when direct metadata exists") },
            coverStorage = LegacyCoverStorage(tempDir.resolve("covers").toFile())
        )

        val metadata = extractor.extractMetadata(file)

        assertNotNull(metadata)
        assertEquals("EXTH Title", metadata?.title)
        assertEquals("Author One", metadata?.author)
        assertEquals("Publisher", metadata?.publisher)
        assertEquals("Description", metadata?.description)
        assertEquals("9781402894626", metadata?.isbn)
        assertEquals(listOf("fiction"), metadata?.tags)
    }
}
