package com.bookd.domain.service

import com.bookd.data.repository.AiConfigurationRepository
import com.bookd.domain.model.AiEndpointRequest
import com.bookd.domain.model.AiEndpointResponse
import com.bookd.domain.model.AiModelRequest
import com.bookd.domain.model.AiModelResponse
import com.bookd.domain.model.AiProviderRequest
import com.bookd.domain.model.AiProviderResponse
import java.net.URI

class AiConfigurationService(
    private val repository: AiConfigurationRepository
) {
    suspend fun listProviders(): List<AiProviderResponse> {
        return repository.listProviders().map { provider ->
            provider.toResponse(
                endpoints = repository.listEndpoints(provider.id).map { endpoint ->
                    endpoint.toResponse(models = repository.listModels(endpoint.id).map { it.toResponse() })
                }
            )
        }
    }

    suspend fun createProvider(request: AiProviderRequest): AiProviderResponse {
        val provider = repository.createProvider(
            name = requiredText(request.name, "Provider name"),
            providerKind = requiredText(request.providerKind, "Provider kind"),
            enabled = request.enabled,
            priority = request.priority
        )
        return provider.toResponse()
    }

    suspend fun updateProvider(id: Int, request: AiProviderRequest): AiProviderResponse? {
        return repository.updateProvider(
            id = id,
            name = requiredText(request.name, "Provider name"),
            providerKind = requiredText(request.providerKind, "Provider kind"),
            enabled = request.enabled,
            priority = request.priority
        )?.toResponse()
    }

    suspend fun deleteProvider(id: Int): Boolean = repository.deleteProvider(id)

    suspend fun createEndpoint(providerId: Int, request: AiEndpointRequest): AiEndpointResponse {
        repository.findProvider(providerId)
            ?: throw AiConfigurationNotFoundException("AI provider not found")
        validateEndpoint(request)
        val endpoint = repository.createEndpoint(
            providerId = providerId,
            baseUrl = requiredBaseUrl(request.baseUrl),
            apiKeySecret = request.apiKey?.takeIf { it.isNotBlank() },
            maxConcurrency = request.maxConcurrency,
            enabled = request.enabled,
            priority = request.priority
        )
        return endpoint.toResponse()
    }

    suspend fun updateEndpoint(id: Int, request: AiEndpointRequest): AiEndpointResponse? {
        val existing = repository.findEndpoint(id) ?: return null
        repository.findProvider(existing.providerId)
            ?: throw AiConfigurationNotFoundException("AI provider not found")
        validateEndpoint(request)
        val apiKeySecret = request.apiKey?.takeIf { it.isNotBlank() } ?: existing.apiKeySecret

        return repository.updateEndpoint(
            id = id,
            baseUrl = requiredBaseUrl(request.baseUrl),
            apiKeySecret = apiKeySecret,
            maxConcurrency = request.maxConcurrency,
            enabled = request.enabled,
            priority = request.priority
        )?.toResponse()
    }

    suspend fun deleteEndpoint(id: Int): Boolean = repository.deleteEndpoint(id)

    suspend fun createModel(endpointId: Int, request: AiModelRequest): AiModelResponse {
        repository.findEndpoint(endpointId)
            ?: throw AiConfigurationNotFoundException("AI endpoint not found")
        validateModel(request)
        val model = repository.createModel(
            endpointId = endpointId,
            modelName = requiredText(request.modelName, "Model name"),
            displayName = requiredText(request.displayName, "Display name"),
            supportsTts = request.supportsTts,
            supportsLlm = request.supportsLlm,
            enabled = request.enabled,
            priority = request.priority
        )
        return model.toResponse()
    }

    suspend fun updateModel(id: Int, request: AiModelRequest): AiModelResponse? {
        val existing = repository.findModel(id) ?: return null
        repository.findEndpoint(existing.endpointId)
            ?: throw AiConfigurationNotFoundException("AI endpoint not found")
        validateModel(request)
        return repository.updateModel(
            id = id,
            modelName = requiredText(request.modelName, "Model name"),
            displayName = requiredText(request.displayName, "Display name"),
            supportsTts = request.supportsTts,
            supportsLlm = request.supportsLlm,
            enabled = request.enabled,
            priority = request.priority
        )?.toResponse()
    }

    suspend fun deleteModel(id: Int): Boolean = repository.deleteModel(id)

    private fun validateEndpoint(request: AiEndpointRequest) {
        require(request.maxConcurrency >= 1) { "Max concurrency must be at least 1" }
    }

    private fun validateModel(request: AiModelRequest) {
        validateCapabilities(request.supportsTts, request.supportsLlm, "Model")
    }

    private fun validateCapabilities(supportsTts: Boolean, supportsLlm: Boolean, owner: String) {
        require(supportsTts || supportsLlm) { "$owner must enable at least one capability" }
    }

    private fun requiredText(value: String, fieldName: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "$fieldName is required" }
        return trimmed
    }

    private fun requiredBaseUrl(value: String): String {
        val trimmed = requiredText(value, "Base URL")
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Base URL is invalid")
        }
        require(uri.scheme == "http" || uri.scheme == "https") { "Base URL must start with http or https" }
        require(!uri.host.isNullOrBlank()) { "Base URL host is required" }
        return trimmed
    }

    private fun AiConfigurationRepository.ProviderRecord.toResponse(
        endpoints: List<AiEndpointResponse> = emptyList()
    ) = AiProviderResponse(
        id = id,
        name = name,
        providerKind = providerKind,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt,
        endpoints = endpoints
    )

    private fun AiConfigurationRepository.EndpointRecord.toResponse(
        models: List<AiModelResponse> = emptyList()
    ) = AiEndpointResponse(
        id = id,
        providerId = providerId,
        baseUrl = baseUrl,
        apiKeySet = !apiKeySecret.isNullOrBlank(),
        maxConcurrency = maxConcurrency,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt,
        models = models
    )

    private fun AiConfigurationRepository.ModelRecord.toResponse() = AiModelResponse(
        id = id,
        endpointId = endpointId,
        modelName = modelName,
        displayName = displayName,
        supportsTts = supportsTts,
        supportsLlm = supportsLlm,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class AiConfigurationNotFoundException(message: String) : IllegalArgumentException(message)
