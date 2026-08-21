package com.bookd.domain.service

import com.bookd.data.entity.AiModels
import com.bookd.data.entity.AiProviderEndpoints
import com.bookd.data.entity.AiProviders
import com.bookd.data.repository.AiConfigurationRepository
import com.bookd.domain.model.AiEndpointRequest
import com.bookd.domain.model.AiModelRequest
import com.bookd.domain.model.AiProviderRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AiConfigurationServiceTest {

    @Test
    fun `given provider endpoint and model when listing then records are priority ordered and secrets are masked`() = runBlocking {
        connectDatabase("ordered")
        val repository = AiConfigurationRepository()
        val service = AiConfigurationService(repository)

        service.createProvider(providerRequest(name = "Slow", priority = 20))
        val fast = service.createProvider(providerRequest(name = "Fast", priority = 1))
        val endpoint = service.createEndpoint(
            fast.id,
            endpointRequest(baseUrl = "https://api.example.com/v1", apiKey = "secret-key", priority = 2)
        )
        service.createModel(endpoint.id, modelRequest(modelName = "tts-fast", priority = 1, supportsTts = true, supportsLlm = false))

        val providers = service.listProviders()

        assertEquals(listOf("Fast", "Slow"), providers.map { it.name })
        assertTrue(providers.first().endpoints.single().apiKeySet)
        assertEquals("tts-fast", providers.first().endpoints.single().models.single().modelName)
    }

    @Test
    fun `given endpoint update without api key when saving then existing secret is preserved and replacement is explicit`() = runBlocking {
        connectDatabase("api_key")
        val repository = AiConfigurationRepository()
        val service = AiConfigurationService(repository)
        val provider = service.createProvider(providerRequest())
        val endpoint = service.createEndpoint(provider.id, endpointRequest(apiKey = "old-key"))

        service.updateEndpoint(endpoint.id, endpointRequest(apiKey = null, baseUrl = "https://api.example.com/v2"))

        assertEquals("old-key", repository.findEndpoint(endpoint.id)?.apiKeySecret)

        service.updateEndpoint(endpoint.id, endpointRequest(apiKey = "new-key", baseUrl = "https://api.example.com/v3"))

        assertEquals("new-key", repository.findEndpoint(endpoint.id)?.apiKeySecret)
        assertTrue(service.listProviders().single().endpoints.single().apiKeySet)
    }

    @Test
    fun `given model without capabilities when saving then validation rejects model`() = runBlocking {
        connectDatabase("capabilities")
        val repository = AiConfigurationRepository()
        val service = AiConfigurationService(repository)

        val provider = service.createProvider(providerRequest())
        val endpoint = service.createEndpoint(provider.id, endpointRequest())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.createModel(endpoint.id, modelRequest(supportsTts = false, supportsLlm = false))
            }
        }
    }

    @Test
    fun `given provider deletion when provider owns endpoints and models then owned configuration is deleted`() = runBlocking {
        connectDatabase("cascade")
        val repository = AiConfigurationRepository()
        val service = AiConfigurationService(repository)
        val provider = service.createProvider(providerRequest())
        val endpoint = service.createEndpoint(provider.id, endpointRequest())
        val model = service.createModel(endpoint.id, modelRequest())

        assertTrue(service.deleteProvider(provider.id))

        assertFalse(service.deleteProvider(provider.id))
        assertEquals(null, repository.findEndpoint(endpoint.id))
        assertEquals(null, repository.findModel(model.id))
    }

    private fun providerRequest(
        name: String = "OpenAI Compatible",
        priority: Int = 0
    ) = AiProviderRequest(
        name = name,
        providerKind = "openai_compatible",
        enabled = true,
        priority = priority
    )

    private fun endpointRequest(
        baseUrl: String = "https://api.example.com/v1",
        apiKey: String? = "secret-key",
        priority: Int = 0
    ) = AiEndpointRequest(
        baseUrl = baseUrl,
        apiKey = apiKey,
        maxConcurrency = 2,
        enabled = true,
        priority = priority
    )

    private fun modelRequest(
        modelName: String = "tts-1",
        priority: Int = 0,
        supportsTts: Boolean = true,
        supportsLlm: Boolean = false
    ) = AiModelRequest(
        modelName = modelName,
        displayName = modelName,
        supportsTts = supportsTts,
        supportsLlm = supportsLlm,
        enabled = true,
        priority = priority
    )

    private fun connectDatabase(name: String) {
        Database.connect(
            url = "jdbc:h2:mem:ai_configuration_${name}_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(AiProviders, AiProviderEndpoints, AiModels)
        }
    }
}
