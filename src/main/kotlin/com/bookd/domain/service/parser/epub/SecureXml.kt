package com.bookd.domain.service.parser.epub

import org.w3c.dom.Document
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 中的 XML 均来自不可信文件，统一禁用 DTD、外部实体和外部 schema。
 */
internal object SecureXml {
    fun parse(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            // EPUB 2 的标准 NCX 常带公共 DOCTYPE；允许声明本身，但禁止加载外部 DTD/实体。
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        return factory.newDocumentBuilder().parse(input)
    }
}
