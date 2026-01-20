package com.bookd.infrastructure.i18n

/**
 * 支持的语言枚举
 *
 * 目前支持：
 * - ZH_CN: 简体中文（默认）
 * - EN: 英文
 */
enum class SupportedLanguage(val code: String) {
    ZH_CN("zh-CN"),
    EN("en");

    companion object {
        /**
         * 从 Accept-Language HTTP Header 解析语言
         *
         * 支持的格式:
         * - "zh-CN,zh;q=0.9,en;q=0.8"
         * - "en-US,en;q=0.9"
         * - "zh"
         * - "en"
         *
         * @param header Accept-Language header 值
         * @return 解析出的语言，默认返回 ZH_CN
         */
        fun fromHeader(header: String?): SupportedLanguage {
            if (header.isNullOrBlank()) return ZH_CN

            // 解析 Accept-Language: en-US,en;q=0.9,zh-CN;q=0.8
            val languages = header.split(",").map { 
                it.split(";")[0].trim().lowercase() 
            }

            // 按顺序匹配（靠前的优先级更高）
            for (lang in languages) {
                when {
                    lang.startsWith("en") -> return EN
                    lang.startsWith("zh") -> return ZH_CN
                }
            }

            // 默认中文
            return ZH_CN
        }

        /**
         * 从语言代码解析
         *
         * @param code 语言代码，如 "zh-CN"、"en"
         * @return 对应的语言，默认返回 ZH_CN
         */
        fun fromCode(code: String?): SupportedLanguage {
            if (code.isNullOrBlank()) return ZH_CN
            
            val lowerCode = code.lowercase()
            return when {
                lowerCode.startsWith("en") -> EN
                lowerCode.startsWith("zh") -> ZH_CN
                else -> ZH_CN
            }
        }
    }
}
