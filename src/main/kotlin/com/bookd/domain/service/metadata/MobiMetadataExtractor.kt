package com.bookd.domain.service.metadata

import com.bookd.domain.service.parser.mobi.MobiHeaderReader
import com.bookd.domain.service.parser.mobi.MobiNormalizer
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.File

class MobiMetadataExtractor internal constructor(
    private val normalizer: MobiNormalizer,
    private val headerReader: MobiHeaderReader = MobiHeaderReader(),
    private val coverStorage: LegacyCoverStorage = LegacyCoverStorage()
) : MetadataExtractor {
    private val logger = LoggerFactory.getLogger(MobiMetadataExtractor::class.java)

    override fun extractMetadata(file: File): BookMetadata? = try {
        val header = headerReader.read(file)
        val direct = BookMetadata(
            title = header.title,
            author = header.authors.joinToString(", ").takeIf(String::isNotBlank),
            publisher = header.publisher,
            description = header.description?.let { Jsoup.parse(it).text() },
            isbn = header.isbn,
            tags = header.subjects.distinct()
        )
        if (direct.title != null || direct.author != null) direct
        else EpubMetadataExtractor().extractMetadata(normalizer.normalize(file))
    } catch (e: Exception) {
        logger.warn("Failed to extract MOBI metadata from ${file.name}: ${e.message}")
        null
    }

    override fun extractCover(file: File, bookId: Int): String? = try {
        val cover = headerReader.read(file).cover
        if (cover != null) {
            coverStorage.saveCover(bookId, cover.extension, cover.bytes.inputStream())
        } else {
            EpubMetadataExtractor(coverStorage).extractCover(normalizer.normalize(file), bookId)
        }
    } catch (e: Exception) {
        logger.warn("Failed to extract MOBI cover from ${file.name}: ${e.message}")
        null
    }
}
