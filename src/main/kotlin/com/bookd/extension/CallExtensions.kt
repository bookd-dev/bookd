package com.bookd.extension

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.model.ErrorResponse
import com.bookd.domain.model.MessageResponse
import com.bookd.domain.model.SuccessResponse
import com.bookd.domain.model.User
import com.bookd.domain.model.UserRole
import com.bookd.domain.service.BookshelfException
import com.bookd.domain.service.UserService
import com.bookd.infrastructure.i18n.I18nException
import com.bookd.infrastructure.i18n.Message
import com.bookd.infrastructure.i18n.MessageBundle
import com.bookd.infrastructure.i18n.SupportedLanguage
import com.bookd.infrastructure.time.TimeProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.java.KoinJavaComponent.get

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

// ===== 身份验证 =====

/**
 * 获取当前认证用户的 ID
 * 如果未认证则返回 null 并自动响应错误
 */
suspend fun ApplicationCall.getAuthenticatedUserId(): Int? {
    return getAuthenticatedUser()?.id
}

/**
 * 获取当前认证用户。
 * 如果未认证则返回 null 并自动响应错误。
 */
suspend fun ApplicationCall.getAuthenticatedUser(): User? {
    val userService = get<UserService>(UserService::class.java)
    return getAuthenticatedUser(userService)
}

suspend fun ApplicationCall.getAuthenticatedUser(userService: UserService): User? {
    val token = request.header("Authorization")?.removePrefix("Bearer ")

    if (token == null) {
        respondError(ErrorCode.AUTH_NO_TOKEN)
        return null
    }

    val user = userService.validateToken(token)
    if (user == null) {
        respondError(ErrorCode.AUTH_INVALID_TOKEN)
        return null
    }

    return user
}

/**
 * 获取当前管理员用户。
 * 为兼容既有用户管理接口，token 无效和非管理员均返回 AUTH_ADMIN_REQUIRED。
 */
suspend fun ApplicationCall.requireAdminUser(): User? {
    val userService = get<UserService>(UserService::class.java)
    return requireAdminUser(userService)
}

suspend fun ApplicationCall.requireAdminUser(userService: UserService): User? {
    val token = request.header("Authorization")?.removePrefix("Bearer ")

    if (token == null) {
        respondError(ErrorCode.AUTH_NO_TOKEN)
        return null
    }

    val user = userService.validateToken(token)
    if (user == null || user.role != UserRole.ADMIN.value) {
        respondError(ErrorCode.AUTH_ADMIN_REQUIRED)
        return null
    }

    return user
}

// ===== Result 处理 =====

/**
 * 处理 BookshelfException 及其他异常的通用错误处理器
 * 
 * 如果异常是 BookshelfException，则使用其 errorCode 响应错误
 * 否则响应通用的内部服务器错误
 */
suspend fun ApplicationCall.handleBookshelfError(exception: Throwable) {
    when (exception) {
        is BookshelfException -> {
            respondError(exception.errorCode)
        }
        else -> {
            respondError(ErrorCode.GEN_INTERNAL_ERROR, exception.message)
        }
    }
}

/**
 * 处理 Result<T> 的成功和失败情况，使用 BookshelfException 处理器
 * 
 * @param result Result 对象
 * @param onSuccess 成功时的处理逻辑
 */
suspend inline fun <reified T : Any> ApplicationCall.handleResult(
    result: Result<T>,
    crossinline onSuccess: suspend ApplicationCall.(T) -> Unit
) {
    result.fold(
        onSuccess = { value -> onSuccess(value) },
        onFailure = { exception -> handleBookshelfError(exception) }
    )
}
