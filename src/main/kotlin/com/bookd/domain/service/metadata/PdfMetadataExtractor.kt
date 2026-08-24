package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.pdf.PdfDocumentAccess
import com.bookd.domain.service.parser.pdf.PdfParseLimits
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt

class PdfMetadataExtractor internal constructor(
    private val limits: PdfParseLimits = PdfParseLimits(),
    private val coverStorage: LegacyCoverStorage = LegacyCoverStorage()
) : MetadataExtractor {
    private val logger = LoggerFactory.getLogger(PdfMetadataExtractor::class.java)

    override fun extractMetadata(file: File): BookMetadata? = try {
        PdfDocumentAccess.use(file, limits, requireExtractionPermission = false) { document ->
            val info = document.documentInformation
            val subject = info.subject.clean()
            val keywords = info.keywords.clean()
            BookMetadata(
                title = info.title.clean(),
                author = info.author.clean(),
                publisher = info.getCustomMetadataValue("Publisher").clean(),
                description = subject?.let { Jsoup.parse(it).text() },
                isbn = findIsbn(info.getCustomMetadataValue("ISBN"), subject, keywords),
                tags = listOfNotNull(subject, keywords)
                    .flatMap { it.split(',', ';', '|', '/') }
                    .map(String::trim)
                    .filter { it.isNotBlank() && it.length <= 50 }
                    .distinct()
            )
        }
    } catch (e: Exception) {
        logger.warn("Failed to extract PDF metadata from ${file.name}: ${e.message}")
        null
    }

    override fun extractCover(file: File, bookId: Int): String? = try {
        PdfDocumentAccess.use(file, limits, requireExtractionPermission = false) { document ->
            val page = document.getPage(0)
            val widthPoints = page.mediaBox.width.toDouble().coerceAtLeast(1.0)
            val heightPoints = page.mediaBox.height.toDouble().coerceAtLeast(1.0)
            val preferredScale = 150.0 / 72.0
            val preferredPixels = widthPoints * heightPoints * preferredScale * preferredScale
            val scale = if (preferredPixels <= limits.maxCoverPixels) {
                preferredScale
            } else {
                sqrt(limits.maxCoverPixels / (widthPoints * heightPoints))
            }.toFloat()

            val image = PDFRenderer(document).apply { isSubsamplingAllowed = true }
                .renderImage(0, scale, ImageType.RGB)
            check(image.width.toLong() * image.height <= limits.maxCoverPixels) {
                "Rendered PDF cover exceeds pixel limit"
            }
            val output = ByteArrayOutputStream()
            check(ImageIO.write(image, "png", output)) { "No PNG writer available" }
            val bytes = output.toByteArray()
            check(bytes.size <= limits.maxCoverBytes) { "Rendered PDF cover exceeds byte limit" }
            coverStorage.saveCover(bookId, "png", bytes.inputStream())
        }
    } catch (e: Exception) {
        logger.warn("Failed to extract PDF cover from ${file.name}: ${e.message}")
        null
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotBlank)

    private fun findIsbn(vararg values: String?): String? {
        val isbnPattern = Regex("(?i)\\b[0-9X][0-9X -]{8,20}[0-9X]\\b")
        return values.asSequence().filterNotNull()
            .flatMap { isbnPattern.findAll(it).map(MatchResult::value) }
            .map { it.filter(Char::isLetterOrDigit).uppercase() }
            .filter { it.length == 10 || it.length == 13 }
            .firstOrNull()
    }
}
