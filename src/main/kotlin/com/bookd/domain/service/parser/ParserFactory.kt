package com.bookd.domain.service.parser

import com.bookd.domain.service.metadata.EbookParserClient
import java.io.File

/**
 * 解析器工厂
 */
class ParserFactory(
    private val txtParser: TxtParser,
    private val ebookParserClient: EbookParserClient?
) {
    /**
     * 根据文件格式创建对应的解析器
     */
    fun createParser(file: File): BookParser? {
        val extension = file.extension.lowercase()
        return when (extension) {
            "epub" -> {
                if (ebookParserClient != null) {
                    EpubBookParser(ebookParserClient)
                } else {
                    throw IllegalStateException("eBook Parser service is not available")
                }
            }
            "txt" -> TxtBookParser(txtParser)
            else -> null
        }
    }
    
    /**
     * 检查格式是否支持
     */
    fun isSupported(extension: String): Boolean {
        return extension.lowercase() in listOf("epub", "txt")
    }
}
