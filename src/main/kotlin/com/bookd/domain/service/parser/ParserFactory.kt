package com.bookd.domain.service.parser

import java.io.File

/**
 * 解析器工厂
 */
class ParserFactory(
    private val txtParser: TxtParser
) {
    private val epubParser = EpubParser()
    private val pdfParser = PdfParser()
    private val htmlParser = HtmlParser()
    
    /**
     * 根据文件格式创建对应的解析器
     */
    fun createParser(file: File): BookParser? {
        val extension = file.extension.lowercase()
        return when (extension) {
            "epub" -> EpubBookParser(epubParser)
            "txt" -> TxtBookParser(txtParser)
            "pdf" -> PdfBookParser(pdfParser)
            "html", "htm" -> HtmlBookParser(htmlParser)
            else -> null
        }
    }
    
    /**
     * 检查格式是否支持
     */
    fun isSupported(extension: String): Boolean {
        return extension.lowercase() in listOf("epub", "txt", "pdf", "html", "htm")
    }
}
