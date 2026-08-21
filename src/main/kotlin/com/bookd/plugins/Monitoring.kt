package com.bookd.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    // 安装 XForwardedHeaders 插件以支持反向代理
    // 这样可以从 X-Forwarded-* 请求头中获取原始的协议、主机和端口
    install(XForwardedHeaders)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            "Status: $status, HTTP method: $httpMethod, Path: $path"
        }
    }
}
