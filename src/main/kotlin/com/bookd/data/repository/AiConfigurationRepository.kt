package com.bookd.data.repository

import com.bookd.data.entity.AiModels
import com.bookd.data.entity.AiProviderEndpoints
import com.bookd.data.entity.AiProviders
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import com.bookd.infrastructure.time.TimeProvider
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class AiConfigurationRepository {
    data class ProviderRecord(
        val id: Int,
        val name: String,
        val providerKind: String,
        val enabled: Boolean,
        val priority: Int,
        val createdAt: String,
        val updatedAt: String
    )

    data class EndpointRecord(
        val id: Int,
        val providerId: Int,
        val baseUrl: String,
        val apiKeySecret: String?,
        val maxConcurrency: Int,
        val enabled: Boolean,
        val priority: Int,
        val createdAt: String,
        val updatedAt: String
    )

    data class ModelRecord(
        val id: Int,
        val endpointId: Int,
        val modelName: String,
        val displayName: String,
        val supportsTts: Boolean,
        val supportsLlm: Boolean,
        val enabled: Boolean,
        val priority: Int,
        val createdAt: String,
        val updatedAt: String
    )

    suspend fun listProviders(): List<ProviderRecord> = dbQuery {
        AiProviders.selectAll()
            .orderBy(AiProviders.priority to SortOrder.ASC, AiProviders.id to SortOrder.ASC)
            .map { it.toProviderRecord() }
    }

    suspend fun findProvider(id: Int): ProviderRecord? = dbQuery {
        findProviderInCurrentTransaction(id)
    }

    suspend fun createProvider(
        name: String,
        providerKind: String,
        enabled: Boolean,
        priority: Int
    ): ProviderRecord = dbQuery {
        val now = TimeProvider.now()
        val id = AiProviders.insertAndGetId {
            it[AiProviders.name] = name
            it[AiProviders.providerKind] = providerKind
            it[AiProviders.enabled] = enabled
            it[AiProviders.priority] = priority
            it[createdAt] = now
            it[updatedAt] = now
        }
        requireNotNull(findProviderInCurrentTransaction(id.value))
    }

    suspend fun updateProvider(
        id: Int,
        name: String,
        providerKind: String,
        enabled: Boolean,
        priority: Int
    ): ProviderRecord? = dbQuery {
        val updated = AiProviders.update({ AiProviders.id eq id }) {
            it[AiProviders.name] = name
            it[AiProviders.providerKind] = providerKind
            it[AiProviders.enabled] = enabled
            it[AiProviders.priority] = priority
            it[updatedAt] = TimeProvider.now()
        }
        if (updated == 0) null else findProviderInCurrentTransaction(id)
    }

    suspend fun deleteProvider(id: Int): Boolean = dbQuery {
        val endpointIds = AiProviderEndpoints.selectAll()
            .where { AiProviderEndpoints.providerId eq id }
            .map { it[AiProviderEndpoints.id].value }
        endpointIds.forEach { endpointId ->
            AiModels.deleteWhere { AiModels.endpointId eq endpointId }
        }
        AiProviderEndpoints.deleteWhere { AiProviderEndpoints.providerId eq id }
        AiProviders.deleteWhere { AiProviders.id eq id } > 0
    }

    suspend fun listEndpoints(providerId: Int): List<EndpointRecord> = dbQuery {
        AiProviderEndpoints.selectAll()
            .where { AiProviderEndpoints.providerId eq providerId }
            .orderBy(AiProviderEndpoints.priority to SortOrder.ASC, AiProviderEndpoints.id to SortOrder.ASC)
            .map { it.toEndpointRecord() }
    }

    suspend fun findEndpoint(id: Int): EndpointRecord? = dbQuery {
        findEndpointInCurrentTransaction(id)
    }

    suspend fun createEndpoint(
        providerId: Int,
        baseUrl: String,
        apiKeySecret: String?,
        maxConcurrency: Int,
        enabled: Boolean,
        priority: Int
    ): EndpointRecord = dbQuery {
        val now = TimeProvider.now()
        val id = AiProviderEndpoints.insertAndGetId {
            it[AiProviderEndpoints.providerId] = providerId
            it[AiProviderEndpoints.baseUrl] = baseUrl
            it[AiProviderEndpoints.apiKeySecret] = apiKeySecret
            it[AiProviderEndpoints.maxConcurrency] = maxConcurrency
            it[AiProviderEndpoints.enabled] = enabled
            it[AiProviderEndpoints.priority] = priority
            it[createdAt] = now
            it[updatedAt] = now
        }
        requireNotNull(findEndpointInCurrentTransaction(id.value))
    }

    suspend fun updateEndpoint(
        id: Int,
        baseUrl: String,
        apiKeySecret: String?,
        maxConcurrency: Int,
        enabled: Boolean,
        priority: Int
    ): EndpointRecord? = dbQuery {
        val updated = AiProviderEndpoints.update({ AiProviderEndpoints.id eq id }) {
            it[AiProviderEndpoints.baseUrl] = baseUrl
            it[AiProviderEndpoints.apiKeySecret] = apiKeySecret
            it[AiProviderEndpoints.maxConcurrency] = maxConcurrency
            it[AiProviderEndpoints.enabled] = enabled
            it[AiProviderEndpoints.priority] = priority
            it[updatedAt] = TimeProvider.now()
        }
        if (updated == 0) null else findEndpointInCurrentTransaction(id)
    }

    suspend fun deleteEndpoint(id: Int): Boolean = dbQuery {
        AiModels.deleteWhere { AiModels.endpointId eq id }
        AiProviderEndpoints.deleteWhere { AiProviderEndpoints.id eq id } > 0
    }

    suspend fun listModels(endpointId: Int): List<ModelRecord> = dbQuery {
        AiModels.selectAll()
            .where { AiModels.endpointId eq endpointId }
            .orderBy(AiModels.priority to SortOrder.ASC, AiModels.id to SortOrder.ASC)
            .map { it.toModelRecord() }
    }

    suspend fun findModel(id: Int): ModelRecord? = dbQuery {
        findModelInCurrentTransaction(id)
    }

    suspend fun createModel(
        endpointId: Int,
        modelName: String,
        displayName: String,
        supportsTts: Boolean,
        supportsLlm: Boolean,
        enabled: Boolean,
        priority: Int
    ): ModelRecord = dbQuery {
        val now = TimeProvider.now()
        val id = AiModels.insertAndGetId {
            it[AiModels.endpointId] = endpointId
            it[AiModels.modelName] = modelName
            it[AiModels.displayName] = displayName
            it[AiModels.supportsTts] = supportsTts
            it[AiModels.supportsLlm] = supportsLlm
            it[AiModels.enabled] = enabled
            it[AiModels.priority] = priority
            it[createdAt] = now
            it[updatedAt] = now
        }
        requireNotNull(findModelInCurrentTransaction(id.value))
    }

    suspend fun updateModel(
        id: Int,
        modelName: String,
        displayName: String,
        supportsTts: Boolean,
        supportsLlm: Boolean,
        enabled: Boolean,
        priority: Int
    ): ModelRecord? = dbQuery {
        val updated = AiModels.update({ AiModels.id eq id }) {
            it[AiModels.modelName] = modelName
            it[AiModels.displayName] = displayName
            it[AiModels.supportsTts] = supportsTts
            it[AiModels.supportsLlm] = supportsLlm
            it[AiModels.enabled] = enabled
            it[AiModels.priority] = priority
            it[updatedAt] = TimeProvider.now()
        }
        if (updated == 0) null else findModelInCurrentTransaction(id)
    }

    suspend fun deleteModel(id: Int): Boolean = dbQuery {
        AiModels.deleteWhere { AiModels.id eq id } > 0
    }

    private fun findProviderInCurrentTransaction(id: Int): ProviderRecord? {
        return AiProviders.selectAll()
            .where { AiProviders.id eq id }
            .map { it.toProviderRecord() }
            .singleOrNull()
    }

    private fun findEndpointInCurrentTransaction(id: Int): EndpointRecord? {
        return AiProviderEndpoints.selectAll()
            .where { AiProviderEndpoints.id eq id }
            .map { it.toEndpointRecord() }
            .singleOrNull()
    }

    private fun findModelInCurrentTransaction(id: Int): ModelRecord? {
        return AiModels.selectAll()
            .where { AiModels.id eq id }
            .map { it.toModelRecord() }
            .singleOrNull()
    }

    private fun ResultRow.toProviderRecord() = ProviderRecord(
        id = this[AiProviders.id].value,
        name = this[AiProviders.name],
        providerKind = this[AiProviders.providerKind],
        enabled = this[AiProviders.enabled],
        priority = this[AiProviders.priority],
        createdAt = this[AiProviders.createdAt].toString(),
        updatedAt = this[AiProviders.updatedAt].toString()
    )

    private fun ResultRow.toEndpointRecord() = EndpointRecord(
        id = this[AiProviderEndpoints.id].value,
        providerId = this[AiProviderEndpoints.providerId].value,
        baseUrl = this[AiProviderEndpoints.baseUrl],
        apiKeySecret = this[AiProviderEndpoints.apiKeySecret],
        maxConcurrency = this[AiProviderEndpoints.maxConcurrency],
        enabled = this[AiProviderEndpoints.enabled],
        priority = this[AiProviderEndpoints.priority],
        createdAt = this[AiProviderEndpoints.createdAt].toString(),
        updatedAt = this[AiProviderEndpoints.updatedAt].toString()
    )

    private fun ResultRow.toModelRecord() = ModelRecord(
        id = this[AiModels.id].value,
        endpointId = this[AiModels.endpointId].value,
        modelName = this[AiModels.modelName],
        displayName = this[AiModels.displayName],
        supportsTts = this[AiModels.supportsTts],
        supportsLlm = this[AiModels.supportsLlm],
        enabled = this[AiModels.enabled],
        priority = this[AiModels.priority],
        createdAt = this[AiModels.createdAt].toString(),
        updatedAt = this[AiModels.updatedAt].toString()
    )
}
