package com.bookd.domain.service.parser

import java.io.File

/**
 * 解析器工厂
 */
class ParserFactory(
    private val txtParser: TxtParser
) {
    /**
     * 根据文件格式创建对应的解析器
     */
    fun createParser(file: File): BookParser? {
        val extension = file.extension.lowercase()
        return when (extension) {
            // EpubContentParser/InlineParser 持有单次解析状态，每本书使用独立实例以避免并发串扰。
            "epub" -> EpubBookParser(EpubParser())
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
