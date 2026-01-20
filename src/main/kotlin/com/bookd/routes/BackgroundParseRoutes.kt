package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.BackgroundParseService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.backgroundParseRoutes() {
    route("/api/background-parse") {

        // 获取后台解析服务状态
        get("/status") {
            val service = get<BackgroundParseService>(BackgroundParseService::class.java)
            val status = service.getStatus()
            call.respondSuccess(status)
        }

        // 启动后台解析服务
        post("/start") {
            val service = get<BackgroundParseService>(BackgroundParseService::class.java)
            service.start()
            call.respondSuccessMessage(MessageBundle.Success.PARSE_SERVICE_STARTED)
        }

        // 停止后台解析服务
        post("/stop") {
            val service = get<BackgroundParseService>(BackgroundParseService::class.java)
            service.stop()
            call.respondSuccessMessage(MessageBundle.Success.PARSE_SERVICE_STOPPED)
        }
    }
}
