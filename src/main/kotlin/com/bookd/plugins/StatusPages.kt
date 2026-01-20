package com.bookd.plugins

import com.bookd.domain.model.ErrorCode
import com.bookd.extension.respondError
import com.bookd.infrastructure.i18n.I18nException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory

/**
 * 配置全局异常处理
 *
 * 统一处理所有未捕获的异常，返回国际化的错误响应格式
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        // 处理国际化异常
        exception<I18nException> { call, cause ->
            logger.warn(
                "I18n error [{}]: {} at {}",
                cause.errorCode.code,
                cause.details ?: cause.errorCode.en,
                call.request.path()
            )
            call.respondError(cause)
        }

        // 处理 IllegalArgumentException（通常是参数验证失败）
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Validation error at {}: {}", call.request.path(), cause.message)
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCode.GEN_BAD_REQUEST,
                cause.message
            )
        }

        // 处理 IllegalStateException
        exception<IllegalStateException> { call, cause ->
            logger.warn("State error at {}: {}", call.request.path(), cause.message)
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCode.GEN_BAD_REQUEST,
                cause.message
            )
        }

        // 处理通用异常
        exception<Throwable> { call, cause ->
            logger.error("Unhandled error at ${call.request.path()}", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                ErrorCode.GEN_INTERNAL_ERROR,
                cause.message
            )
        }
    }
}
