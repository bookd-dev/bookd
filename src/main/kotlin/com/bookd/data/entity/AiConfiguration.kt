package com.bookd.data.entity

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.datetime

object AiProviders : IntIdTable("ai_providers") {
    val name = varchar("name", 120)
    val providerKind = varchar("provider_kind", 80)
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        index(false, priority)
    }
}

object AiProviderEndpoints : IntIdTable("ai_provider_endpoints") {
    val providerId = reference("provider_id", AiProviders, onDelete = ReferenceOption.CASCADE)
    val baseUrl = varchar("base_url", 1000)
    val apiKeySecret = text("api_key_secret").nullable()
    val maxConcurrency = integer("max_concurrency").default(1)
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        index(false, providerId, priority)
    }
}

object AiModels : IntIdTable("ai_models") {
    val endpointId = reference("endpoint_id", AiProviderEndpoints, onDelete = ReferenceOption.CASCADE)
    val modelName = varchar("model_name", 160)
    val displayName = varchar("display_name", 160)
    val supportsTts = bool("supports_tts").default(false)
    val supportsLlm = bool("supports_llm").default(false)
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        index(false, endpointId, priority)
    }
}
