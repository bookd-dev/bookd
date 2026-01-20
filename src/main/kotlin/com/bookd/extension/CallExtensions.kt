package com.bookd.extension

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.model.ErrorResponse
import com.bookd.domain.model.MessageResponse
import com.bookd.domain.model.SuccessResponse
import com.bookd.infrastructure.i18n.I18nException
import com.bookd.infrastructure.i18n.Message
import com.bookd.infrastructure.i18n.MessageBundle
import com.bookd.infrastructure.i18n.SupportedLanguage
import com.bookd.infrastructure.time.TimeProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * ApplicationCall 国际化扩展函数
 *
 * 提供统一的成功/错误响应方法，自动处理国际化和响应格式包装。
 */

// ===== 语言处理 =====

/**
 * 获取当前请求的语言
 *
 * 从 Accept-Language header 解析语言，默认返回中文
 */
fun ApplicationCall.getLanguage(): SupportedLanguage {
    val acceptLanguage = request.header("Accept-Language")
    return SupportedLanguage.fromHeader(acceptLanguage)
}

// ===== 成功响应 =====

/**
 * 成功响应（带数据）
 *
 * 包装数据为统一的 SuccessResponse 格式：
 * ```json
 * {
 *   "data": { ... },
 *   "timestamp": "2026-01-20T10:30:00",
 *   "path": "/api/books/1"
 * }
 * ```
 */
suspend inline fun <reified T : Any> ApplicationCall.respondSuccess(data: T) {
    respond(SuccessResponse(
        data = data,
        timestamp = TimeProvider.now().toString(),
        path = request.path()
    ))
}

/**
 * 成功响应（带 HTTP 状态码和数据）
 */
suspend inline fun <reified T : Any> ApplicationCall.respondSuccess(status: HttpStatusCode, data: T) {
    respond(status, SuccessResponse(
        data = data,
        timestamp = TimeProvider.now().toString(),
        path = request.path()
    ))
}

/**
 * 成功响应（仅消息，自动国际化）
 *
 * 用于操作成功但无具体数据返回的场景：
 * ```json
 * {
 *   "data": { "message": "已登出", "success": true },
 *   "timestamp": "2026-01-20T10:30:00",
 *   "path": "/api/auth/logout"
 * }
 * ```
 */
suspend fun ApplicationCall.respondSuccessMessage(message: Message) {
    val language = getLanguage()
    respondSuccess(MessageResponse(
        message = message.get(language),
        success = true
    ))
}

/**
 * 成功响应（仅消息，带状态码）
 */
suspend fun ApplicationCall.respondSuccessMessage(status: HttpStatusCode, message: Message) {
    val language = getLanguage()
    respondSuccess(status, MessageResponse(
        message = message.get(language),
        success = true
    ))
}

// ===== 错误响应 =====

/**
 * 错误响应（使用 ErrorCode 定义的默认状态码）
 *
 * 自动根据 Accept-Language 返回国际化错误消息：
 * ```json
 * {
 *   "code": "AUTH_001",
 *   "message": "用户名或密码错误",
 *   "timestamp": "2026-01-20T10:30:00",
 *   "path": "/api/auth/login"
 * }
 * ```
 */
suspend fun ApplicationCall.respondError(errorCode: ErrorCode, details: String? = null) {
    respondError(errorCode.defaultStatus, errorCode, details)
}

/**
 * 错误响应（自定义状态码）
 */
suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    errorCode: ErrorCode,
    details: String? = null
) {
    val language = getLanguage()
    respond(status, ErrorResponse(
        code = errorCode.code,
        message = MessageBundle.getMessage(errorCode, language),
        details = details,
        timestamp = TimeProvider.now().toString(),
        path = request.path()
    ))
}

/**
 * 从 I18nException 响应错误
 */
suspend fun ApplicationCall.respondError(exception: I18nException) {
    respondError(exception.httpStatus, exception.errorCode, exception.details)
}

/**
 * 从普通异常响应错误
 */
suspend fun ApplicationCall.respondError(exception: Throwable) {
    respondError(
        HttpStatusCode.InternalServerError,
        ErrorCode.GEN_INTERNAL_ERROR,
        exception.message
    )
}

// ===== 便捷方法 =====

/**
 * 校验参数并返回错误（如果参数无效）
 *
 * 用法:
 * ```kotlin
 * val id = call.parameters["id"]?.toIntOrNull()
 * if (id == null) {
 *     call.respondBadRequest(ErrorCode.BOOK_INVALID_ID)
 *     return@get
 * }
 * ```
 */
suspend fun ApplicationCall.respondBadRequest(errorCode: ErrorCode, details: String? = null) {
    respondError(HttpStatusCode.BadRequest, errorCode, details)
}

/**
 * 资源未找到错误
 */
suspend fun ApplicationCall.respondNotFound(errorCode: ErrorCode, details: String? = null) {
    respondError(HttpStatusCode.NotFound, errorCode, details)
}

/**
 * 未授权错误
 */
suspend fun ApplicationCall.respondUnauthorized(errorCode: ErrorCode, details: String? = null) {
    respondError(HttpStatusCode.Unauthorized, errorCode, details)
}

/**
 * 禁止访问错误
 */
suspend fun ApplicationCall.respondForbidden(errorCode: ErrorCode, details: String? = null) {
    respondError(HttpStatusCode.Forbidden, errorCode, details)
}

/**
 * 资源冲突错误
 */
suspend fun ApplicationCall.respondConflict(errorCode: ErrorCode, details: String? = null) {
    respondError(HttpStatusCode.Conflict, errorCode, details)
}

/**
 * 无内容响应（204 No Content）
 *
 * 用于删除操作成功等无需返回内容的场景
 */
suspend fun ApplicationCall.respondNoContent() {
    respond(HttpStatusCode.NoContent, Unit)
}
