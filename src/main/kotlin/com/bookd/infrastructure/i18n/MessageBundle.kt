package com.bookd.infrastructure.i18n

import com.bookd.domain.model.ErrorCode

/**
 * 国际化消息对象
 *
 * @property zhCN 简体中文消息
 * @property en 英文消息
 */
data class Message(
    val zhCN: String,
    val en: String
) {
    /**
     * 根据语言获取对应消息
     */
    fun get(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.ZH_CN -> zhCN
            SupportedLanguage.EN -> en
        }
    }
}

/**
 * 国际化消息管理
 *
 * 提供错误码消息和成功消息的国际化支持
 */
object MessageBundle {

    /**
     * 获取错误码对应的国际化消息
     */
    fun getMessage(errorCode: ErrorCode, language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.ZH_CN -> errorCode.zhCN
            SupportedLanguage.EN -> errorCode.en
        }
    }

    /**
     * 成功消息定义
     *
     * 用于操作成功但无具体数据返回的场景
     */
    object Success {
        // ===== Authentication =====
        val LOGGED_OUT = Message(
            "已登出",
            "Logged out"
        )

        // ===== User Management =====
        val USER_DELETED = Message(
            "用户已删除",
            "User deleted"
        )

        // ===== Book =====
        val BOOK_QUEUED_REPARSE = Message(
            "书籍已加入重新解析队列",
            "Book queued for reparse"
        )

        // ===== Tag =====
        // 标签操作通常返回实际数据，不需要消息

        // ===== BookSource =====
        val SOURCE_TOGGLED = Message(
            "来源已切换",
            "Source toggled"
        )

        // ===== TxtParseRule =====
        val RULE_UPDATED = Message(
            "规则已更新",
            "Rule updated"
        )
        val RULE_DELETED = Message(
            "规则已删除",
            "Rule deleted"
        )
        val RULE_TOGGLED = Message(
            "规则已切换",
            "Rule toggled"
        )
        val PRIORITIES_UPDATED = Message(
            "优先级已更新",
            "Priorities updated"
        )
        val RULES_IMPORTED = Message(
            "规则导入成功",
            "Rules imported successfully"
        )

        // ===== Reading =====
        val BOOKMARK_DELETED = Message(
            "书签已删除",
            "Bookmark deleted"
        )

        // ===== BackgroundParse =====
        val PARSE_SERVICE_STARTED = Message(
            "后台解析服务已启动",
            "Background parse service started"
        )
        val PARSE_SERVICE_STOPPED = Message(
            "后台解析服务已停止",
            "Background parse service stopped"
        )
    }
}
