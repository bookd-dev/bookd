package com.bookd.domain.model

import io.ktor.http.*

/**
 * 统一错误码定义
 *
 * 错误码格式: {模块}_{序号}
 * - AUTH_xxx: 认证相关错误
 * - USER_xxx: 用户管理相关错误
 * - BOOK_xxx: 书籍相关错误
 * - TAG_xxx: 标签相关错误
 * - SOURCE_xxx: 书籍来源相关错误
 * - RULE_xxx: TXT解析规则相关错误
 * - SCAN_xxx: 扫描相关错误
 * - READ_xxx: 阅读相关错误
 * - FS_xxx: 文件系统相关错误
 * - PARSE_xxx: 后台解析相关错误
 * - GEN_xxx: 通用错误
 */
enum class ErrorCode(
    val code: String,
    val defaultStatus: HttpStatusCode,
    val zhCN: String,
    val en: String
) {
    // ===== Authentication Errors (AUTH_xxx) =====
    AUTH_INVALID_CREDENTIALS(
        "AUTH_001", HttpStatusCode.Unauthorized,
        "用户名或密码错误", "Invalid credentials"
    ),
    AUTH_NO_TOKEN(
        "AUTH_002", HttpStatusCode.Unauthorized,
        "未提供认证令牌", "No token provided"
    ),
    AUTH_INVALID_TOKEN(
        "AUTH_003", HttpStatusCode.Unauthorized,
        "无效的认证令牌", "Invalid token"
    ),
    AUTH_ADMIN_REQUIRED(
        "AUTH_004", HttpStatusCode.Forbidden,
        "需要管理员权限", "Admin access required"
    ),
    AUTH_ADMIN_EXISTS(
        "AUTH_005", HttpStatusCode.Conflict,
        "管理员已存在", "Admin already exists"
    ),
    AUTH_USERNAME_EXISTS(
        "AUTH_006", HttpStatusCode.BadRequest,
        "用户名已存在", "Username already exists"
    ),
    AUTH_INVITE_REQUIRED(
        "AUTH_007", HttpStatusCode.BadRequest,
        "需要邀请码", "Invite token required"
    ),
    AUTH_INVALID_INVITE(
        "AUTH_008", HttpStatusCode.BadRequest,
        "无效的邀请码或用户名已存在", "Invalid invite token or username already exists"
    ),
    AUTH_SETUP_FAILED(
        "AUTH_009", HttpStatusCode.BadRequest,
        "初始化设置失败", "Setup failed"
    ),
    AUTH_NOT_AUTHORIZED(
        "AUTH_010", HttpStatusCode.Forbidden,
        "未授权的操作", "Not authorized"
    ),

    // ===== User Management Errors (USER_xxx) =====
    USER_INVALID_ID(
        "USER_001", HttpStatusCode.BadRequest,
        "无效的用户ID", "Invalid user ID"
    ),
    USER_NOT_FOUND(
        "USER_002", HttpStatusCode.NotFound,
        "用户不存在", "User not found"
    ),

    // ===== Book Errors (BOOK_xxx) =====
    BOOK_INVALID_ID(
        "BOOK_001", HttpStatusCode.BadRequest,
        "无效的书籍ID", "Invalid book ID"
    ),
    BOOK_NOT_FOUND(
        "BOOK_002", HttpStatusCode.NotFound,
        "书籍不存在", "Book not found"
    ),
    BOOK_PARSE_CHAPTERS_FAILED(
        "BOOK_003", HttpStatusCode.InternalServerError,
        "章节解析失败", "Failed to parse chapters"
    ),
    BOOK_COVER_GENERATION_FAILED(
        "BOOK_004", HttpStatusCode.InternalServerError,
        "封面生成失败", "Failed to generate cover"
    ),
    BOOK_FILE_SAVE_FAILED(
        "BOOK_005", HttpStatusCode.InternalServerError,
        "文件保存失败", "Failed to save file"
    ),
    BOOK_MULTIPART_FAILED(
        "BOOK_006", HttpStatusCode.BadRequest,
        "多部分请求处理失败", "Failed to process multipart"
    ),
    BOOK_NO_COVER_FILE(
        "BOOK_007", HttpStatusCode.BadRequest,
        "未提供封面文件", "No cover file provided"
    ),
    BOOK_MANIFEST_NOT_FOUND(
        "BOOK_008", HttpStatusCode.NotFound,
        "书籍清单未找到，内容可能尚未解析", "Book manifest not found. Content may not be parsed yet."
    ),
    BOOK_ALREADY_PARSING(
        "BOOK_009", HttpStatusCode.Conflict,
        "书籍正在解析中或未找到", "Book is already being parsed or not found"
    ),
    BOOK_INVALID_PARAMS(
        "BOOK_010", HttpStatusCode.BadRequest,
        "无效的参数", "Invalid parameters"
    ),
    BOOK_CHAPTER_NOT_FOUND(
        "BOOK_011", HttpStatusCode.NotFound,
        "章节未找到", "Chapter not found"
    ),

    // ===== Tag Errors (TAG_xxx) =====
    TAG_NAME_EMPTY(
        "TAG_001", HttpStatusCode.BadRequest,
        "标签名称不能为空", "Tag name cannot be empty"
    ),
    TAG_NOT_FOUND(
        "TAG_002", HttpStatusCode.NotFound,
        "标签不存在", "Tag not found"
    ),
    TAG_INVALID_ID(
        "TAG_003", HttpStatusCode.BadRequest,
        "无效的标签ID", "Invalid tag ID"
    ),
    TAG_SOURCE_IDS_EMPTY(
        "TAG_004", HttpStatusCode.BadRequest,
        "源标签ID列表不能为空", "Source tag IDs cannot be empty"
    ),
    TAG_TARGET_NAME_EMPTY(
        "TAG_005", HttpStatusCode.BadRequest,
        "目标标签名称不能为空", "Target tag name cannot be empty"
    ),
    TAG_MERGE_FAILED(
        "TAG_006", HttpStatusCode.InternalServerError,
        "合并标签失败", "Failed to retrieve merged tag"
    ),
    TAG_INVALID_BOOK_OR_TAG_ID(
        "TAG_007", HttpStatusCode.BadRequest,
        "无效的书籍ID或标签ID", "Invalid book ID or tag ID"
    ),
    TAG_NOT_FOUND_FOR_BOOK(
        "TAG_008", HttpStatusCode.NotFound,
        "该书籍未找到此标签", "Tag not found for this book"
    ),

    // ===== BookSource Errors (SOURCE_xxx) =====
    SOURCE_INVALID_ID(
        "SOURCE_001", HttpStatusCode.BadRequest,
        "无效的来源ID", "Invalid source ID"
    ),
    SOURCE_NOT_FOUND(
        "SOURCE_002", HttpStatusCode.NotFound,
        "来源不存在", "Source not found"
    ),

    // ===== TxtParseRule Errors (RULE_xxx) =====
    RULE_INVALID_ID(
        "RULE_001", HttpStatusCode.BadRequest,
        "无效的规则ID", "Invalid ID"
    ),
    RULE_NOT_FOUND(
        "RULE_002", HttpStatusCode.NotFound,
        "规则不存在", "Rule not found"
    ),
    RULE_INVALID_REGEX(
        "RULE_003", HttpStatusCode.BadRequest,
        "无效的正则表达式", "Invalid regex pattern"
    ),
    RULE_PRIORITY_UPDATE_FAILED(
        "RULE_004", HttpStatusCode.BadRequest,
        "优先级更新失败", "Failed to update priorities"
    ),
    RULE_JSON_FILE_NOT_FOUND(
        "RULE_005", HttpStatusCode.NotFound,
        "JSON文件未找到", "JSON file not found"
    ),

    // ===== Scan Errors (SCAN_xxx) =====
    SCAN_INVALID_SOURCE_ID(
        "SCAN_001", HttpStatusCode.BadRequest,
        "无效的来源ID", "Invalid source ID"
    ),

    // ===== Reading Errors (READ_xxx) =====
    READ_PROGRESS_NOT_FOUND(
        "READ_001", HttpStatusCode.NotFound,
        "未找到阅读进度", "No reading progress found"
    ),
    READ_INVALID_BOOKMARK_ID(
        "READ_002", HttpStatusCode.BadRequest,
        "无效的书签ID", "Invalid bookmark ID"
    ),
    READ_BOOKMARK_NOT_FOUND(
        "READ_003", HttpStatusCode.NotFound,
        "书签不存在", "Bookmark not found"
    ),

    // ===== FileSystem Errors (FS_xxx) =====
    FS_DIR_NOT_FOUND(
        "FS_001", HttpStatusCode.NotFound,
        "目录不存在", "Directory not found"
    ),
    FS_NOT_A_DIRECTORY(
        "FS_002", HttpStatusCode.BadRequest,
        "路径不是目录", "Path is not a directory"
    ),
    FS_PERMISSION_DENIED(
        "FS_003", HttpStatusCode.Forbidden,
        "权限被拒绝", "Permission denied"
    ),
    FS_LIST_FAILED(
        "FS_004", HttpStatusCode.InternalServerError,
        "列出目录失败", "Failed to list directory"
    ),
    FS_PATH_REQUIRED(
        "FS_005", HttpStatusCode.BadRequest,
        "路径参数必填", "Path parameter is required"
    ),

    // ===== BackgroundParse Errors (PARSE_xxx) =====
    PARSE_STATUS_FAILED(
        "PARSE_001", HttpStatusCode.InternalServerError,
        "获取解析状态失败", "Failed to get parse status"
    ),
    PARSE_START_FAILED(
        "PARSE_002", HttpStatusCode.InternalServerError,
        "后台解析服务启动失败", "Failed to start background parse service"
    ),
    PARSE_STOP_FAILED(
        "PARSE_003", HttpStatusCode.InternalServerError,
        "后台解析服务停止失败", "Failed to stop background parse service"
    ),

    // ===== General Errors (GEN_xxx) =====
    GEN_BAD_REQUEST(
        "GEN_001", HttpStatusCode.BadRequest,
        "请求参数错误", "Bad request"
    ),
    GEN_INTERNAL_ERROR(
        "GEN_002", HttpStatusCode.InternalServerError,
        "服务器内部错误", "Internal server error"
    ),
    GEN_UNKNOWN_ERROR(
        "GEN_003", HttpStatusCode.InternalServerError,
        "未知错误", "Unknown error"
    );

    companion object {
        /**
         * 根据错误码字符串查找 ErrorCode
         */
        fun fromCode(code: String): ErrorCode? {
            return entries.find { it.code == code }
        }
    }
}
