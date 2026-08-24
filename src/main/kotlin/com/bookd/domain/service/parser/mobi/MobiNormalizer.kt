package com.bookd.domain.service.parser.mobi

import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import com.bookd.domain.service.parser.epub.EpubArchiveSafety
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import java.util.zip.ZipFile

fun interface MobiNormalizer {
    fun normalize(source: File): File
}

class ProcessMobiNormalizer(
    private val executable: File = File(System.getenv("BOOKD_MOBITOOL") ?: "/usr/local/bin/mobitool"),
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "bookd-mobi-cache"),
    private val converterVersion: String = "libmobi-v0.12-85dcfe803fc2a21020ddcf15c3eb66b93d388add",
    private val limits: MobiParseLimits = MobiParseLimits(),
    private val headerReader: MobiHeaderReader = MobiHeaderReader(limits)
) : MobiNormalizer {
    init {
        require(converterVersion.matches(Regex("[A-Za-z0-9._-]+"))) { "Converter version must be cache-key safe" }
    }

    override fun normalize(source: File): File {
        val header = headerReader.read(source)
        if (header.encryption != 0) {
            throw EbookParseException(EbookParseFailureReason.MOBI_PROTECTED, "Protected MOBI files are unsupported")
        }
        Files.createDirectories(cacheDirectory.toPath())
        val cacheKey = sha256(source)
        val cached = File(cacheDirectory, "$converterVersion-$cacheKey.epub")
        if (isValidEpub(cached)) return cached
        if (!executable.isFile || !executable.canExecute()) {
            throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZER_MISSING, "MOBI normalizer is unavailable")
        }

        val workDirectory = Files.createTempDirectory(cacheDirectory.toPath(), ".normalize-").toFile()
        try {
            val process = ProcessBuilder(executable.absolutePath, "-e", "-o", workDirectory.absolutePath, source.absolutePath)
                .redirectErrorStream(true)
                .start()
            val outputExceeded = AtomicBoolean(false)
            val outputDrainer = thread(name = "mobi-normalizer-output", isDaemon = true) {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > limits.maxProcessLogBytes) {
                            outputExceeded.set(true)
                            process.destroyForcibly()
                            break
                        }
                    }
                }
            }
            if (!process.waitFor(limits.normalizationDeadlineMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                outputDrainer.join(5_000)
                throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZATION_TIMEOUT, "MOBI normalization timed out")
            }
            outputDrainer.join(5_000)
            if (outputExceeded.get()) {
                throw EbookParseException(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, "MOBI normalizer log exceeds configured limit")
            }
            if (process.exitValue() != 0) {
                throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZATION_FAILED, "MOBI normalization failed")
            }
            val output = workDirectory.listFiles().orEmpty().singleOrNull { it.isFile && it.extension.equals("epub", true) }
                ?: throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZATION_FAILED, "MOBI normalizer did not produce one EPUB")
            if (output.length() <= 0 || output.length() > limits.maxNormalizedBytes || !isValidEpub(output)) {
                throw EbookParseException(EbookParseFailureReason.PARSER_LIMIT_EXCEEDED, "Normalized MOBI output is invalid or exceeds limits")
            }
            publish(output, cached)
            return cached
        } catch (e: EbookParseException) {
            throw e
        } catch (e: Exception) {
            throw EbookParseException(EbookParseFailureReason.MOBI_NORMALIZATION_FAILED, "MOBI normalization failed", e)
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    private fun isValidEpub(file: File): Boolean {
        if (!file.isFile || file.length() <= 0 || file.length() > limits.maxNormalizedBytes) return false
        return runCatching {
            ZipFile(file).use { zip ->
                EpubArchiveSafety.validate(zip)
                val mimetype = zip.getEntry("mimetype") ?: return@use false
                val value = EpubArchiveSafety.readBytes(zip, mimetype, 64).toString(Charsets.US_ASCII).trim()
                value == "application/epub+zip" && zip.getEntry("META-INF/container.xml") != null
            }
        }.getOrDefault(false)
    }

    private fun publish(source: File, target: File) {
        val temporary = File.createTempFile(".${target.name}.", ".tmp", cacheDirectory)
        try {
            source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
