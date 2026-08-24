package com.bookd.domain.service.parser.pdf

data class PdfParseLimits(
    val maxFileBytes: Long = 512L * 1024 * 1024,
    val maxPages: Int = 10_000,
    val maxPageTextBytes: Int = 2 * 1024 * 1024,
    val maxTotalTextBytes: Long = 128L * 1024 * 1024,
    val maxCoverPixels: Long = 20_000_000,
    val maxCoverBytes: Int = 20 * 1024 * 1024,
    val parseDeadlineMillis: Long = 120_000,
    val fallbackPagesPerChapter: Int = 20
)
