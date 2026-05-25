package com.bookd.routes

import com.bookd.domain.model.ErrorCode
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.domain.service.ImportRulesResult
import com.bookd.extension.*
import com.bookd.infrastructure.i18n.MessageBundle
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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
            call.requireAdminUser() ?: return@get
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val rules = txtParseRuleService.getAllRules()
            call.respondSuccess(rules)
        }

        // 获取启用的规则
        get("/enabled") {
            call.requireAdminUser() ?: return@get
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val rules = txtParseRuleService.getEnabledRules()
            call.respondSuccess(rules)
        }

        // 获取单个规则
        get("/{id}") {
            call.requireAdminUser() ?: return@get
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
            call.requireAdminUser() ?: return@post
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
            call.requireAdminUser() ?: return@put
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
            call.requireAdminUser() ?: return@delete
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
            call.requireAdminUser() ?: return@post
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
            call.requireAdminUser() ?: return@post
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
            call.requireAdminUser() ?: return@post
            val txtParseRuleService = get<TxtParseRuleService>(TxtParseRuleService::class.java)
            val language = call.getLanguage()

            val request = call.receive<ImportRequest>()

            when (val result = txtParseRuleService.importRules(request.jsonContent, request.useFile)) {
                is ImportRulesResult.FileNotFound -> {
                    call.respondError(ErrorCode.RULE_JSON_FILE_NOT_FOUND, result.path)
                }
                is ImportRulesResult.Imported -> {
                    call.respondSuccess(ImportResponse(
                        message = MessageBundle.Success.RULES_IMPORTED.get(language),
                        imported = result.imported,
                        skipped = result.skipped
                    ))
                }
            }
        }
    }
}
