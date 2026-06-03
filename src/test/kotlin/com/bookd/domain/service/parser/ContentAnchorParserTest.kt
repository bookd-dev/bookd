package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.domain.service.parser.epub.EpubContentParser
import io.mockk.mockk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentAnchorParserTest {

    @Test
    fun `given epub source ids when parsed then anchors are stable and prefer source ids`() {
        val parser = EpubContentParser()
        val html = """
            <html><body>
                <h1 id="chapter-title">标题</h1>
                <p id="intro">Intro text</p>
                <p>Repeated</p>
                <p>Repeated</p>
            </body></html>
        """.trimIndent()

        val first = parser.parseHtml(html, "Text/chapter01.xhtml")
        val second = parser.parseHtml(html, "Text/chapter01.xhtml")

        assertEquals(first.map { it.anchorId }, second.map { it.anchorId })
        assertTrue(first[0].anchorId?.contains("#chapter-title") == true)
        assertTrue(first[1].anchorId?.contains("#intro") == true)
        assertEquals(first.mapNotNull { it.anchorId }.size, first.mapNotNull { it.anchorId }.toSet().size)
    }

    @Test
    fun `given txt duplicate paragraphs when parsed then anchors are stable and unique`() {
        val parser = TxtParser(mockk<TxtParseRuleService>(relaxed = true))
        val text = "第 1 章\n重复段落\n重复段落"
        val chapter = TxtParser.ChapterInfo(
            index = 2,
            title = "第 1 章",
            startPos = 0,
            endPos = text.length
        )

        val first = parser.extractChapterContent(text, chapter)
        val second = parser.extractChapterContent(text, chapter)

        assertEquals(first.map { it.anchorId }, second.map { it.anchorId })
        assertEquals(first.mapNotNull { it.anchorId }.size, first.mapNotNull { it.anchorId }.toSet().size)
    }

    @Test
    fun `given legacy content json without anchors when decoded then anchor is null`() {
        val json = Json { ignoreUnknownKeys = true }
        val content = """
            [
              {
                "type": "paragraph",
                "spans": [{"text": "legacy"}]
              }
            ]
        """.trimIndent()

        val elements = json.decodeFromString<List<ContentElement>>(content)

        assertEquals(1, elements.size)
        assertTrue(elements.single() is ContentElement.Paragraph)
        assertEquals(null, elements.single().anchorId)
        assertNotNull(elements.single())
    }
}
