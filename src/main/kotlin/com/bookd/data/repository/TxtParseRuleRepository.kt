package com.bookd.data.repository

import com.bookd.infrastructure.time.TimeProvider
import com.bookd.data.entity.TxtParseRules
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

class TxtParseRuleRepository {
    
    data class TxtParseRuleDTO(
        val id: Int,
        val name: String,
        val rule: String,
        val example: String?,
        val enabled: Boolean,
        val priority: Int,
        val createdAt: Instant,
        val updatedAt: Instant
    )
    
    data class CreateTxtParseRuleDTO(
        val name: String,
        val rule: String,
        val example: String? = null,
        val enabled: Boolean = true,
        val priority: Int = 0
    )
    
    suspend fun getAllRules(): List<TxtParseRuleDTO> = 
        newSuspendedTransaction {
            TxtParseRules.selectAll()
                .orderBy(TxtParseRules.priority to SortOrder.ASC)
                .map { it.toDTO() }
        }
    
    suspend fun getEnabledRules(): List<TxtParseRuleDTO> =
        newSuspendedTransaction {
            TxtParseRules.selectAll().where { TxtParseRules.enabled eq true }
                .orderBy(TxtParseRules.priority to SortOrder.ASC)
                .map { it.toDTO() }
        }
    
    suspend fun getRuleById(id: Int): TxtParseRuleDTO? =
        newSuspendedTransaction {
            TxtParseRules.selectAll().where { TxtParseRules.id eq id }
                .map { it.toDTO() }
                .firstOrNull()
        }
    
    suspend fun createRule(dto: CreateTxtParseRuleDTO): TxtParseRuleDTO =
        newSuspendedTransaction {
            val now = TimeProvider.now()
            val id = TxtParseRules.insertAndGetId {
                it[name] = dto.name
                it[rule] = dto.rule
                it[example] = dto.example
                it[enabled] = dto.enabled
                it[priority] = dto.priority
                it[createdAt] = now
                it[updatedAt] = now
            }
            TxtParseRules.selectAll().where { TxtParseRules.id eq id }
                .map { it.toDTO() }
                .first()
        }
    
    suspend fun updateRule(id: Int, dto: CreateTxtParseRuleDTO): Boolean =
        newSuspendedTransaction {
            TxtParseRules.update({ TxtParseRules.id eq id }) {
                it[name] = dto.name
                it[rule] = dto.rule
                it[example] = dto.example
                it[enabled] = dto.enabled
                it[priority] = dto.priority
                it[updatedAt] = TimeProvider.now()
            } > 0
        }
    
    suspend fun deleteRule(id: Int): Boolean =
        newSuspendedTransaction {
            TxtParseRules.deleteWhere { TxtParseRules.id eq id } > 0
        }
    
    suspend fun toggleRuleEnabled(id: Int): Boolean =
        newSuspendedTransaction {
            val currentEnabled = TxtParseRules.selectAll().where { TxtParseRules.id eq id }
                .map { it[TxtParseRules.enabled] }
                .firstOrNull() ?: return@newSuspendedTransaction false
            
            TxtParseRules.update({ TxtParseRules.id eq id }) {
                it[enabled] = !currentEnabled
                it[updatedAt] = TimeProvider.now()
            } > 0
        }
    
    suspend fun updatePriorities(priorities: Map<Int, Int>): Boolean =
        newSuspendedTransaction {
            priorities.forEach { (id, priority) ->
                TxtParseRules.update({ TxtParseRules.id eq id }) {
                    it[TxtParseRules.priority] = priority
                    it[updatedAt] = TimeProvider.now()
                }
            }
            true
        }
    
    private fun ResultRow.toDTO() = TxtParseRuleDTO(
        id = this[TxtParseRules.id].value,
        name = this[TxtParseRules.name],
        rule = this[TxtParseRules.rule],
        example = this[TxtParseRules.example],
        enabled = this[TxtParseRules.enabled],
        priority = this[TxtParseRules.priority],
        createdAt = this[TxtParseRules.createdAt].toInstant(TimeProvider.timeZone),
        updatedAt = this[TxtParseRules.updatedAt].toInstant(TimeProvider.timeZone)
    )
}
