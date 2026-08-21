package com.bookd.domain.service.parser

import com.bookd.domain.model.ContentElement
import com.bookd.domain.service.TxtParseRuleService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class TxtParserTest {

    @TempDir
    lateinit var tempDir: Path

    private val ruleService = mockk<TxtParseRuleService>().also {
        coEvery { it.getEnabledRules() } returns emptyList()
    }
    private val parser = TxtParser(ruleService)

    @Test
    fun `given crlf text when parsing chapters then original offsets remain exact`() {
        val text = "preface\r\nChapter 1\r\nfirst body\r\nChapter 2\r\nsecond body"
        val file = tempDir.resolve("crlf.txt")
        Files.writeString(file, text)

        val structure = runBlocking { parser.parseStructure(file.toFile()) }

        requireNotNull(structure)
        assertEquals(2, structure.chapters.size)
        val first = structure.chapters[0]
        assertEquals(text.indexOf("Chapter 1"), first.startPos)
        assertEquals(text.indexOf("Chapter 2"), first.endPos)
        val firstElements = parser.extractChapterContent(structure.fullText, first)
        assertTrue(firstElements.texts().contains("Chapter 1"))
        assertTrue(firstElements.texts().contains("first body"))
        assertTrue(firstElements.texts().none { it.contains("Chapter 2") })
    }

    @Test
    fun `given gb18030 chinese text when parsing chapters then text is decoded without mojibake`() {
        val text = "第一章 开始\r\n中文内容\r\n第二章 继续\r\n后续内容"
        val file = tempDir.resolve("gb18030.txt")
        Files.write(file, text.toByteArray(Charset.forName("GB18030")))

        val structure = runBlocking { parser.parseStructure(file.toFile()) }

        requireNotNull(structure)
        assertEquals(listOf("第一章 开始", "第二章 继续"), structure.chapters.map { it.title })
        val firstElements = parser.extractChapterContent(structure.fullText, structure.chapters.first())
        assertTrue(firstElements.texts().contains("中文内容"))
    }

    private fun List<ContentElement>.texts(): List<String> = mapNotNull { element ->
        when (element) {
            is ContentElement.Heading -> element.text
            is ContentElement.Paragraph -> element.spans.joinToString("") { it.text }
            else -> null
        }
    }
}
