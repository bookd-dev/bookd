package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.koin.java.KoinJavaComponent.get

@Serializable
data class ImportRequest(
    val jsonContent: String? = null,
    val useFile: Boolean = false
)

@Serializable
data class ImportResponse(
    val message: String,
    val imported: Int,
    val skipped: Int
)

fun Route.txtParseRuleRoutes() {
    route("/api/txt-parse-rules") {
        // 获取所有规则
        get {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val rules = txtParseRuleService.getAllRules()
            call.respondSuccess(rules)
        }

        // 获取启用的规则
        get("/enabled") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val rules = txtParseRuleService.getEnabledRules()
            call.respondSuccess(rules)
        }

        // 获取单个规则
        get("/{id}") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.RULE_INVALID_ID)
                return@get
            }

            val rule = txtParseRuleService.getRuleById(id)
            if (rule == null) {
                call.respondError(ErrorCode.RULE_NOT_FOUND)
                return@get
            }

            call.respondSuccess(rule)
        }

        // 创建规则
        post {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            try {
                val request = call.receive<TxtParseRuleService.CreateTxtParseRuleRequest>()
                val rule = txtParseRuleService.createRule(request)
                call.respondSuccess(HttpStatusCode.Created, rule)
            } catch (e: IllegalArgumentException) {
                call.respondError(ErrorCode.RULE_INVALID_REGEX, e.message)
            }
        }

        // 更新规则
        put("/{id}") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.RULE_INVALID_ID)
                return@put
            }

            try {
                val request = call.receive<TxtParseRuleService.CreateTxtParseRuleRequest>()
                val success = txtParseRuleService.updateRule(id, request)

                if (success) {
                    call.respondSuccessMessage(MessageBundle.Success.RULE_UPDATED)
                } else {
                    call.respondError(ErrorCode.RULE_NOT_FOUND)
                }
            } catch (e: IllegalArgumentException) {
                call.respondError(ErrorCode.RULE_INVALID_REGEX, e.message)
            }
        }

        // 删除规则
        delete("/{id}") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.RULE_INVALID_ID)
                return@delete
            }

            val success = txtParseRuleService.deleteRule(id)

            if (success) {
                call.respondSuccessMessage(MessageBundle.Success.RULE_DELETED)
            } else {
                call.respondError(ErrorCode.RULE_NOT_FOUND)
            }
        }

        // 切换规则启用状态
        post("/{id}/toggle") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respondError(ErrorCode.RULE_INVALID_ID)
                return@post
            }

            val success = txtParseRuleService.toggleRuleEnabled(id)

            if (success) {
                call.respondSuccessMessage(MessageBundle.Success.RULE_TOGGLED)
            } else {
                call.respondError(ErrorCode.RULE_NOT_FOUND)
            }
        }

        // 批量更新优先级
        post("/priorities") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val priorities = call.receive<Map<String, Int>>()
            val intPriorities = priorities.mapKeys { it.key.toInt() }
            val success = txtParseRuleService.updatePriorities(intPriorities)

            if (success) {
                call.respondSuccessMessage(MessageBundle.Success.PRIORITIES_UPDATED)
            } else {
                call.respondError(ErrorCode.RULE_PRIORITY_UPDATE_FAILED)
            }
        }

        // 从 JSON 文件导入规则
        post("/import") {
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val language = call.getLanguage()

            @Serializable
            data class JsonRule(
                val name: String,
                val rule: String,
                val example: String
            )

            val request = call.receive<ImportRequest>()

            val jsonContent = if (request.useFile || request.jsonContent.isNullOrBlank()) {
                // 从文件读取
                val jsonFile = java.io.File("static/txt_toc_rules.json")
                if (!jsonFile.exists()) {
                    call.respondError(ErrorCode.RULE_JSON_FILE_NOT_FOUND, jsonFile.absolutePath)
                    return@post
                }
                jsonFile.readText()
            } else {
                // 使用用户提供的 JSON 内容
                request.jsonContent
            }

            // 解析并导入
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

            call.respondSuccess(ImportResponse(
                message = MessageBundle.Success.RULES_IMPORTED.get(language),
                imported = imported,
                skipped = skipped
            ))
        }
    }
}
