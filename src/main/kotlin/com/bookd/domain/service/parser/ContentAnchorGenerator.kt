package com.bookd.domain.service.parser

import java.security.MessageDigest

class ContentAnchorGenerator(
    private val sourceKind: String,
    private val chapterIdentity: String
) {
    private val occurrences = mutableMapOf<String, Int>()
    private val chapterKey = shortHash(chapterIdentity)

    fun sourceAnchor(sourceId: String?): String? {
        val normalized = sourceId?.trim()?.trimStart('#')?.takeIf { it.isNotBlank() } ?: return null
        return "$sourceKind:$chapterKey#${sanitize(normalized)}"
    }

    fun generatedAnchor(elementType: String, fingerprint: String): String {
        val normalizedFingerprint = normalize(fingerprint)
        val fingerprintHash = shortHash(normalizedFingerprint)
        val occurrenceKey = "$elementType:$fingerprintHash"
        val occurrence = occurrences.getOrDefault(occurrenceKey, 0)
        occurrences[occurrenceKey] = occurrence + 1
        return "$sourceKind:$chapterKey:$elementType:$fingerprintHash:$occurrence"
    }

    fun anchorFor(sourceId: String?, elementType: String, fingerprint: String): String {
        return sourceAnchor(sourceId) ?: generatedAnchor(elementType, fingerprint)
    }

    private fun sanitize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9._:-]+"), "-")
            .trim('-')
            .ifBlank { shortHash(value) }
    }

    private fun normalize(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    private fun shortHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
