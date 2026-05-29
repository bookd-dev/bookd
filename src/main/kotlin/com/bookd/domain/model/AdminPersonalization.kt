package com.bookd.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PersonalizationSettingsResponse(
    val timeZone: String
)

@Serializable
data class PersonalizationOverviewResponse(
    val settings: PersonalizationSettingsResponse,
    val providers: List<AiProviderResponse>
)

@Serializable
data class TimeZoneUpdateRequest(
    val timeZone: String
)

@Serializable
data class AiProviderRequest(
    val name: String,
    val providerKind: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

@Serializable
data class AiEndpointRequest(
    val baseUrl: String,
    val apiKey: String? = null,
    val maxConcurrency: Int,
    val enabled: Boolean = true,
    val priority: Int = 0
)

@Serializable
data class AiModelRequest(
    val modelName: String,
    val displayName: String,
    val supportsTts: Boolean,
    val supportsLlm: Boolean,
    val enabled: Boolean = true,
    val priority: Int = 0
)

@Serializable
data class AiProviderResponse(
    val id: Int,
    val name: String,
    val providerKind: String,
    val enabled: Boolean,
    val priority: Int,
    val createdAt: String,
    val updatedAt: String,
    val endpoints: List<AiEndpointResponse>
)

@Serializable
data class AiEndpointResponse(
    val id: Int,
    val providerId: Int,
    val baseUrl: String,
    val apiKeySet: Boolean,
    val maxConcurrency: Int,
    val enabled: Boolean,
    val priority: Int,
    val createdAt: String,
    val updatedAt: String,
    val models: List<AiModelResponse>
)

@Serializable
data class AiModelResponse(
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
