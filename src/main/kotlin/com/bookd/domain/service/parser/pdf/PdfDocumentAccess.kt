package com.bookd.domain.service.parser.pdf

import com.bookd.domain.service.parser.EbookParseException
import com.bookd.domain.service.parser.EbookParseFailureReason
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

internal object PdfDocumentAccess {
    fun <T> use(
        file: File,
        limits: PdfParseLimits,
        requireExtractionPermission: Boolean = true,
        block: (PDDocument) -> T
    ): T {
        if (file.length() > limits.maxFileBytes) {
            throw EbookParseException(
                EbookParseFailureReason.PARSER_LIMIT_EXCEEDED,
                "PDF source file exceeds configured size limit"
            )
        }

        val document = try {
            Loader.loadPDF(file, "", IOUtils.createTempFileOnlyStreamCache())
        } catch (e: InvalidPasswordException) {
            throw EbookParseException(EbookParseFailureReason.PDF_PROTECTED, "PDF requires a password", e)
        }

        document.use {
            if (requireExtractionPermission && !document.currentAccessPermission.canExtractContent()) {
                throw EbookParseException(
                    EbookParseFailureReason.PDF_PROTECTED,
                    "PDF permissions prohibit content extraction"
                )
            }
            if (document.numberOfPages <= 0 || document.numberOfPages > limits.maxPages) {
                throw EbookParseException(
                    EbookParseFailureReason.PARSER_LIMIT_EXCEEDED,
                    "PDF page count is outside configured bounds"
                )
            }
            return block(document)
        }
    }
}
