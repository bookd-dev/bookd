package com.bookd.domain.model

import kotlinx.serialization.Serializable

/**
 * 统一API响应包装类
 * 
 * 所有API响应都将被包装为此格式，包含：
 * - timestamp: 响应时间戳（ISO 8601格式）
 * - path: 请求路径
 * 
 * 子类型：
 * - SuccessResponse: 成功响应，包含 data 字段
 * - ErrorResponse: 错误响应，包含 code、message、details 字段
 */
@Serializable
sealed class ApiResponse {
    abstract val timestamp: String
    abstract val path: String
}

/**
 * 成功响应
 * 
 * @param T 数据类型
 * @property data 响应数据
 * @property timestamp 响应时间戳
 * @property path 请求路径
 */
@Serializable
data class SuccessResponse<T : @Serializable Any>(
    val data: T,
    override val timestamp: String,
    override val path: String
) : ApiResponse()

/**
 * 错误响应
 * 
 * @property code 错误码（如 "AUTH_001", "BOOK_002"）
 * @property message 国际化错误消息
 * @property details 可选的详细错误信息（如异常堆栈、参数错误详情）
 * @property timestamp 响应时间戳
 * @property path 请求路径
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: String? = null,
    override val timestamp: String,
    override val path: String
) : ApiResponse()

/**
 * 操作消息响应（用于无具体数据返回的成功操作）
 * 
 * @property message 操作结果消息
 * @property success 是否成功（默认true）
 */
@Serializable
data class MessageResponse(
    val message: String,
    val success: Boolean = true
)
