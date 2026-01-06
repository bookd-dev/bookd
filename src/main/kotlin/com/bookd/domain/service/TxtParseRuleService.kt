package com.bookd.domain.service

import com.bookd.data.repository.TxtParseRuleRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

class TxtParseRuleService(
    private val repository: TxtParseRuleRepository
) {
    private val logger = LoggerFactory.getLogger(TxtParseRuleService::class.java)
    
    @Serializable
    data class TxtParseRuleResponse(
        val id: Int,
        val name: String,
        val rule: String,
        val example: String?,
        val enabled: Boolean,
        val priority: Int,
        val createdAt: String,
        val updatedAt: String
    )
    
    @Serializable
    data class CreateTxtParseRuleRequest(
        val name: String,
        val rule: String,
        val example: String? = null,
        val enabled: Boolean = true,
        val priority: Int = 0
    )
    
    suspend fun getAllRules(): List<TxtParseRuleResponse> {
        return repository.getAllRules().map { it.toResponse() }
    }
    
    suspend fun getEnabledRules(): List<TxtParseRuleResponse> {
        return repository.getEnabledRules().map { it.toResponse() }
    }
    
    suspend fun getRuleById(id: Int): TxtParseRuleResponse? {
        return repository.getRuleById(id)?.toResponse()
    }
    
    suspend fun createRule(request: CreateTxtParseRuleRequest): TxtParseRuleResponse {
        // 验证正则表达式是否有效
        try {
            request.rule.toRegex()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: ${e.message}")
        }
        
        val dto = TxtParseRuleRepository.CreateTxtParseRuleDTO(
            name = request.name,
            rule = request.rule,
            example = request.example,
            enabled = request.enabled,
            priority = request.priority
        )
        return repository.createRule(dto).toResponse()
    }
    
    suspend fun updateRule(id: Int, request: CreateTxtParseRuleRequest): Boolean {
        // 验证正则表达式是否有效
        try {
            request.rule.toRegex()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: ${e.message}")
        }
        
        val dto = TxtParseRuleRepository.CreateTxtParseRuleDTO(
            name = request.name,
            rule = request.rule,
            example = request.example,
            enabled = request.enabled,
            priority = request.priority
        )
        return repository.updateRule(id, dto)
    }
    
    suspend fun deleteRule(id: Int): Boolean {
        return repository.deleteRule(id)
    }
    
    suspend fun toggleRuleEnabled(id: Int): Boolean {
        return repository.toggleRuleEnabled(id)
    }
    
    suspend fun updatePriorities(priorities: Map<Int, Int>): Boolean {
        return repository.updatePriorities(priorities)
    }
    
    /**
     * 初始化：从 JSON 文件导入规则（如果数据库为空）
     */
    suspend fun initializeFromJson(jsonFile: File, force: Boolean = false) {
        try {
            val existingRules = repository.getAllRules()
            if (existingRules.isNotEmpty() && !force) {
                logger.info("TXT parse rules already exist in database, skipping initialization")
                return
            }
            
            logger.info("Initializing TXT parse rules from ${jsonFile.absolutePath} (force: $force)")
            
            @Serializable
            data class JsonRule(
                val name: String,
                val rule: String,
                val example: String
            )
            
            val jsonContent = jsonFile.readText()
            val jsonRules = Json.decodeFromString<List<JsonRule>>(jsonContent)
            
            var imported = 0
            var skipped = 0
            
            jsonRules.forEachIndexed { index, jsonRule ->
                try {
                    // 检查是否已存在同名规则
                    val existing = existingRules.find { it.name == jsonRule.name }
                    if (existing != null && !force) {
                        logger.debug("Rule '${jsonRule.name}' already exists, skipping")
                        skipped++
                        return@forEachIndexed
                    }
                    
                    val dto = TxtParseRuleRepository.CreateTxtParseRuleDTO(
                        name = jsonRule.name,
                        rule = jsonRule.rule,
                        example = jsonRule.example,
                        enabled = true,
                        priority = index
                    )
                    repository.createRule(dto)
                    imported++
                    logger.debug("Imported rule: ${jsonRule.name}")
                } catch (e: Exception) {
                    logger.error("Failed to import rule: ${jsonRule.name}", e)
                }
            }
            
            logger.info("TXT parse rules import completed: imported=$imported, skipped=$skipped")
        } catch (e: Exception) {
            logger.error("Failed to initialize TXT parse rules from JSON", e)
            throw e
        }
    }
    
    private fun TxtParseRuleRepository.TxtParseRuleDTO.toResponse() = TxtParseRuleResponse(
        id = id,
        name = name,
        rule = rule,
        example = example,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
