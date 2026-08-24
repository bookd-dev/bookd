package com.bookd.domain.service.parser.mobi

data class MobiParseLimits(
    val maxFileBytes: Long = 512L * 1024 * 1024,
    val maxRecords: Int = 100_000,
    val maxHeaderRecordBytes: Int = 16 * 1024 * 1024,
    val maxImageBytes: Int = 20 * 1024 * 1024,
    val maxNormalizedBytes: Long = 2L * 1024 * 1024 * 1024,
    val normalizationDeadlineMillis: Long = 120_000,
    val maxProcessLogBytes: Long = 2L * 1024 * 1024
)
