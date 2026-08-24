package com.bookd.domain.service.parser

import com.bookd.domain.service.parser.mobi.MobiNormalizer
import com.bookd.domain.service.parser.mobi.ProcessMobiNormalizer
import java.io.File

/**
 * 解析器工厂
 */
class ParserFactory(
    private val txtParser: TxtParser,
    private val pdfParser: BookParser = PdfBookParser(),
    mobiNormalizer: MobiNormalizer = ProcessMobiNormalizer()
) {
    private val mobiParser: BookParser = MobiBookParser(mobiNormalizer)
    /**
     * 根据文件格式创建对应的解析器
     */
    fun createParser(file: File): BookParser? {
        return when (EbookFormatRegistry.detect(file)) {
            // EpubContentParser/InlineParser 持有单次解析状态，每本书使用独立实例以避免并发串扰。
            EbookFormat.EPUB -> EpubBookParser(EpubParser())
            EbookFormat.TXT -> TxtBookParser(txtParser)
            EbookFormat.PDF -> pdfParser
            EbookFormat.MOBI -> mobiParser
            null -> null
        }
    }
    
    /**
     * 检查格式是否支持
     */
    fun isSupported(extension: String): Boolean {
        return EbookFormatRegistry.fromExtension(extension) != null
    }
}
