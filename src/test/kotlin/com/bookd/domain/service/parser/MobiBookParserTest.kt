package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.parser.mobi.MobiTestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MobiBookParserTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given normalized EPUB when MOBI parsed then navigation content links resources and anchors reuse EPUB behavior`() = runBlocking {
        val mobi = MobiTestFixtures.createMobi(tempDir.resolve("source.mobi")).toFile()
        val epub = MobiTestFixtures.createEpub(tempDir.resolve("normalized.epub")).toFile()
        val parser = MobiBookParser { epub }

        val structure = requireNotNull(parser.parseStructure(mobi))
        val first = parser.parseChapterContents(mobi, structure.chapters)
        val second = parser.parseChapterContents(mobi, structure.chapters)
        val resources = mutableListOf<String>()
        parser.forEachResource(mobi) { path, _ -> resources += path }

        assertEquals(1, structure.chapters.size)
        assertEquals(first.values.flatten().map { it.anchorId }, second.values.flatten().map { it.anchorId })
        assertTrue(first.values.flatten().any { it is ContentElement.Heading && it.text == "Chapter" })
        assertTrue(first.values.flatten().any { it is ContentElement.Paragraph && it.spans.any { span -> span.link != null } })
        assertEquals(listOf("image.png"), resources)
    }
}
