package com.bookd.config

/**
 * eBook Parser 微服务配置
 */
data class EbookParserConfig(
    val enabled: Boolean = System.getenv("EBOOK_PARSER_ENABLED")?.toBoolean() ?: false,
    val serviceUrl: String = System.getenv("EBOOK_PARSER_SERVICE_URL") ?: "http://localhost:7920",
    val timeoutMs: Long = System.getenv("EBOOK_PARSER_TIMEOUT_MS")?.toLong() ?: 30000L
)
