package com.bookd.routes

import com.bookd.domain.service.BackgroundParseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.backgroundParseRoutes() {
    route("/api/background-parse") {
        
        // 获取后台解析服务状态
        get("/status") {
            try {
                val service = get<BackgroundParseService>(BackgroundParseService::class.java)
                val status = service.getStatus()
                call.respond(HttpStatusCode.OK, status)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 启动后台解析服务
        post("/start") {
            try {
                val service = get<BackgroundParseService>(BackgroundParseService::class.java)
                service.start()
                call.respond(HttpStatusCode.OK, mapOf("message" to "Background parse service started"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 停止后台解析服务
        post("/stop") {
            try {
                val service = get<BackgroundParseService>(BackgroundParseService::class.java)
                service.stop()
                call.respond(HttpStatusCode.OK, mapOf("message" to "Background parse service stopped"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
}
