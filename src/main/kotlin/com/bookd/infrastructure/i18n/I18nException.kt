package com.bookd.infrastructure.i18n

import com.bookd.domain.model.ErrorCode
import io.ktor.http.*

/**
 * 国际化异常类
 *
 * 用于 Service 层抛出带 ErrorCode 的异常，
 * 在 Routes 层或全局异常处理中统一转换为国际化错误响应。
 *
 * @property errorCode 错误码
 * @property httpStatus HTTP 状态码（可选，默认使用 ErrorCode 中定义的状态码）
 * @property details 详细错误信息（可选，如异常堆栈、参数错误详情）
 * @property cause 原始异常（可选）
 */
class I18nException(
    val errorCode: ErrorCode,
    val httpStatus: HttpStatusCode = errorCode.defaultStatus,
    val details: String? = null,
    cause: Throwable? = null
) : Exception(errorCode.code, cause) {

    companion object {
        /**
         * 快速创建 Bad Request 异常
         */
        fun badRequest(errorCode: ErrorCode, details: String? = null): I18nException {
            return I18nException(errorCode, HttpStatusCode.BadRequest, details)
        }

        /**
         * 快速创建 Not Found 异常
         */
        fun notFound(errorCode: ErrorCode, details: String? = null): I18nException {
            return I18nException(errorCode, HttpStatusCode.NotFound, details)
        }

        /**
         * 快速创建 Unauthorized 异常
         */
        fun unauthorized(errorCode: ErrorCode, details: String? = null): I18nException {
            return I18nException(errorCode, HttpStatusCode.Unauthorized, details)
        }

        /**
         * 快速创建 Forbidden 异常
         */
        fun forbidden(errorCode: ErrorCode, details: String? = null): I18nException {
            return I18nException(errorCode, HttpStatusCode.Forbidden, details)
        }

        /**
         * 快速创建 Internal Server Error 异常
         */
        fun internalError(errorCode: ErrorCode, details: String? = null, cause: Throwable? = null): I18nException {
            return I18nException(errorCode, HttpStatusCode.InternalServerError, details, cause)
        }

        /**
         * 从普通异常创建
         */
        fun from(cause: Throwable): I18nException {
            return I18nException(
                errorCode = ErrorCode.GEN_INTERNAL_ERROR,
                details = cause.message,
                cause = cause
            )
        }
    }
}
