package com.bookd.routes

import com.bookd.domain.service.TxtParseRuleService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.koin.java.KoinJavaComponent.get

fun Route.txtParseRuleRoutes() {
    route("/api/txt-parse-rules") {
        // 获取所有规则
        get {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val rules = txtParseRuleService.getAllRules()
                call.respond(HttpStatusCode.OK, rules)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 获取启用的规则
        get("/enabled") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val rules = txtParseRuleService.getEnabledRules()
                call.respond(HttpStatusCode.OK, rules)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 获取单个规则
        get("/{id}") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                
                val rule = txtParseRuleService.getRuleById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rule not found"))
                
                call.respond(HttpStatusCode.OK, rule)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 创建规则
        post {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val request = call.receive<TxtParseRuleService.CreateTxtParseRuleRequest>()
                val rule = txtParseRuleService.createRule(request)
                call.respond(HttpStatusCode.Created, rule)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 更新规则
        put("/{id}") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                
                val request = call.receive<TxtParseRuleService.CreateTxtParseRuleRequest>()
                val success = txtParseRuleService.updateRule(id, request)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Rule updated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rule not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 删除规则
        delete("/{id}") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                
                val success = txtParseRuleService.deleteRule(id)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Rule deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rule not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 切换规则启用状态
        post("/{id}/toggle") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                
                val success = txtParseRuleService.toggleRuleEnabled(id)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Rule toggled"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rule not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 批量更新优先级
        post("/priorities") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                val priorities = call.receive<Map<String, Int>>()
                val intPriorities = priorities.mapKeys { it.key.toInt() }
                val success = txtParseRuleService.updatePriorities(intPriorities)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Priorities updated"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to update priorities"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 从 JSON 文件导入规则
        post("/import") {
            try {
                val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
                
                @kotlinx.serialization.Serializable
                data class ImportRequest(
                    val jsonContent: String? = null,
                    val useFile: Boolean = false
                )
                
                @kotlinx.serialization.Serializable
                data class ImportResponse(
                    val message: String,
                    val imported: Int,
                    val skipped: Int
                )
                
                val request = call.receive<ImportRequest>()
                
                val jsonContent = if (request.useFile || request.jsonContent.isNullOrBlank()) {
                    // 从文件读取
                    val jsonFile = java.io.File("txt_toc_rules.json")
                    if (!jsonFile.exists()) {
                        return@post call.respond(
                            HttpStatusCode.NotFound, 
                            mapOf("error" to "JSON file not found: ${jsonFile.absolutePath}")
                        )
                    }
                    jsonFile.readText()
                } else {
                    // 使用用户提供的 JSON 内容
                    request.jsonContent
                }
                
                // 解析并导入
                @kotlinx.serialization.Serializable
                data class JsonRule(
                    val name: String,
                    val rule: String,
                    val example: String
                )
                
                val jsonRules = decodeFromString<List<JsonRule>>(jsonContent)
                val existingRules = txtParseRuleService.getAllRules()
                
                var imported = 0
                var skipped = 0
                
                jsonRules.forEachIndexed { index, jsonRule ->
                    try {
                        // 检查是否已存在同名规则
                        val existing = existingRules.find { it.name == jsonRule.name }
                        if (existing != null) {
                            skipped++
                            return@forEachIndexed
                        }
                        
                        val createRequest = TxtParseRuleService.CreateTxtParseRuleRequest(
                            name = jsonRule.name,
                            rule = jsonRule.rule,
                            example = jsonRule.example,
                            enabled = true,
                            priority = index
                        )
                        txtParseRuleService.createRule(createRequest)
                        imported++
                    } catch (e: Exception) {
                        // 忽略单个规则的错误，继续导入其他规则
                    }
                }
                
                call.respond(HttpStatusCode.OK, ImportResponse(
                    message = "Rules imported successfully",
                    imported = imported,
                    skipped = skipped
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
