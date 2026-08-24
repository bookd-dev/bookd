package com.bookd.domain.service.parser.mobi

import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ProcessMobiNormalizerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `given successful normalizer when called twice then valid output is cached by source digest`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("source with spaces.mobi")).toFile()
        val executable = createExecutable("success.sh", """
            #!/bin/sh
            cp "$(dirname "$0")/template.epub" "$3/converted.epub"
        """.trimIndent())
        MobiTestFixtures.createEpub(tempDir.resolve("template.epub"))
        val normalizer = ProcessMobiNormalizer(executable, tempDir.resolve("cache").toFile(), "test-v1")

        val first = normalizer.normalize(source)
        executable.delete()
        val second = normalizer.normalize(source)

        assertEquals(first, second)
        assertTrue(second.isFile)
    }

    @Test
    fun `given protected MOBI when normalized then protection is rejected before process start`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("protected.mobi"), encryption = 1).toFile()
        val error = assertThrows(EbookParseException::class.java) {
            ProcessMobiNormalizer(tempDir.resolve("missing").toFile()).normalize(source)
        }
        assertEquals(EbookParseFailureReason.MOBI_PROTECTED, error.reason)
    }

    @Test
    fun `given missing normalizer when normalized then stable missing reason is returned`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("missing.mobi")).toFile()
        val error = assertThrows(EbookParseException::class.java) {
            ProcessMobiNormalizer(tempDir.resolve("missing-tool").toFile()).normalize(source)
        }
        assertEquals(EbookParseFailureReason.MOBI_NORMALIZER_MISSING, error.reason)
    }

    @Test
    fun `given nonzero converter exit when normalized then stable failure reason is returned`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("failed.mobi")).toFile()
        val executable = createExecutable("failed.sh", "#!/bin/sh\nexit 9")

        val error = assertThrows(EbookParseException::class.java) {
            ProcessMobiNormalizer(executable, tempDir.resolve("cache-failed").toFile()).normalize(source)
        }

        assertEquals(EbookParseFailureReason.MOBI_NORMALIZATION_FAILED, error.reason)
    }

    @Test
    fun `given slow normalizer when deadline expires then process is killed with timeout reason`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("timeout.mobi")).toFile()
        val executable = createExecutable("slow.sh", "#!/bin/sh\nsleep 5")
        val normalizer = ProcessMobiNormalizer(
            executable,
            tempDir.resolve("cache-timeout").toFile(),
            limits = MobiParseLimits(normalizationDeadlineMillis = 20)
        )

        val error = assertThrows(EbookParseException::class.java) { normalizer.normalize(source) }

        assertEquals(EbookParseFailureReason.MOBI_NORMALIZATION_TIMEOUT, error.reason)
    }

    @Test
    fun `given oversized normalized output when conversion completes then limit reason is returned`() {
        val source = MobiTestFixtures.createMobi(tempDir.resolve("oversized.mobi")).toFile()
        val executable = createExecutable("oversized.sh", """
            #!/bin/sh
            cp "$(dirname "$0")/template.epub" "$3/converted.epub"
        """.trimIndent())
        MobiTestFixtures.createEpub(tempDir.resolve("template.epub"))
        val normalizer = ProcessMobiNormalizer(
            executable,
            tempDir.resolve("cache-oversized").toFile(),
            limits = MobiParseLimits(maxNormalizedBytes = 10)
        )

        val error = assertThrows(EbookParseException::class.java) { normalizer.normalize(source) }

        assertEquals(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, error.reason)
    }

    private fun createExecutable(name: String, content: String) = tempDir.resolve(name).also { path ->
        path.writeText(content)
        assertTrue(path.toFile().setExecutable(true))
    }.toFile()
}
