package com.bookd.domain.service.parser

open class EbookParseException(
    val reason: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

object EbookParseFailureReason {
    const val INVALID_SIGNATURE = "invalid_signature"
    const val PARSER_LIMIT_EXCEEDED = "parser_limit_exceeded"
    const val PDF_IMAGE_ONLY = "pdf_text_layer_missing"
    const val PDF_PROTECTED = "pdf_protected"
    const val MOBI_PROTECTED = "mobi_protected"
    const val MOBI_NORMALIZER_MISSING = "mobi_normalizer_missing"
    const val MOBI_NORMALIZATION_FAILED = "mobi_normalization_failed"
    const val MOBI_NORMALIZATION_TIMEOUT = "mobi_normalization_timeout"
}
