package com.bookd.routes

import com.bookd.domain.model.AiEndpointRequest
import com.bookd.domain.model.AiModelRequest
import com.bookd.domain.model.AiProviderRequest
import com.bookd.domain.model.ErrorCode
import com.bookd.domain.model.PersonalizationOverviewResponse
import com.bookd.domain.model.TimeZoneUpdateRequest
import com.bookd.domain.service.AiConfigurationNotFoundException
import com.bookd.domain.service.AiConfigurationService
import com.bookd.domain.service.PersonalizationSettingsService
import com.bookd.extension.requireAdminUser
import com.bookd.extension.respondError
import com.bookd.extension.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.get

fun Route.adminPersonalizationRoutes() {
    route("/api/admin") {
        get("/personalization") {
            call.requireAdminUser() ?: return@get
            val settingsService = get<PersonalizationSettingsService>(PersonalizationSettingsService::class.java)
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            call.respondSuccess(
                PersonalizationOverviewResponse(
                    settings = settingsService.getSettings(),
                    providers = aiService.listProviders()
                )
            )
        }

        get("/settings/personalization") {
            call.requireAdminUser() ?: return@get
            val settingsService = get<PersonalizationSettingsService>(PersonalizationSettingsService::class.java)
            call.respondSuccess(settingsService.getSettings())
        }

        put("/settings/time-zone") {
            call.requireAdminUser() ?: return@put
            val settingsService = get<PersonalizationSettingsService>(PersonalizationSettingsService::class.java)
            try {
                call.respondSuccess(settingsService.updateTimeZone(call.receive<TimeZoneUpdateRequest>()))
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        get("/ai-providers") {
            call.requireAdminUser() ?: return@get
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            call.respondSuccess(aiService.listProviders())
        }

        post("/ai-providers") {
            call.requireAdminUser() ?: return@post
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                call.respondSuccess(HttpStatusCode.Created, aiService.createProvider(call.receive<AiProviderRequest>()))
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        put("/ai-providers/{providerId}") {
            call.requireAdminUser() ?: return@put
            val providerId = call.requiredAdminId("providerId") ?: return@put
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                val provider = aiService.updateProvider(providerId, call.receive<AiProviderRequest>())
                if (provider == null) {
                    call.respondNotFound("AI provider not found")
                } else {
                    call.respondSuccess(provider)
                }
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        delete("/ai-providers/{providerId}") {
            call.requireAdminUser() ?: return@delete
            val providerId = call.requiredAdminId("providerId") ?: return@delete
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            if (aiService.deleteProvider(providerId)) {
                call.respondSuccess(mapOf("success" to true))
            } else {
                call.respondNotFound("AI provider not found")
            }
        }

        post("/ai-providers/{providerId}/endpoints") {
            call.requireAdminUser() ?: return@post
            val providerId = call.requiredAdminId("providerId") ?: return@post
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                call.respondSuccess(HttpStatusCode.Created, aiService.createEndpoint(providerId, call.receive<AiEndpointRequest>()))
            } catch (e: AiConfigurationNotFoundException) {
                call.respondNotFound(e.message ?: "AI provider not found")
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        put("/ai-endpoints/{endpointId}") {
            call.requireAdminUser() ?: return@put
            val endpointId = call.requiredAdminId("endpointId") ?: return@put
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                val endpoint = aiService.updateEndpoint(endpointId, call.receive<AiEndpointRequest>())
                if (endpoint == null) {
                    call.respondNotFound("AI endpoint not found")
                } else {
                    call.respondSuccess(endpoint)
                }
            } catch (e: AiConfigurationNotFoundException) {
                call.respondNotFound(e.message ?: "AI provider not found")
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        delete("/ai-endpoints/{endpointId}") {
            call.requireAdminUser() ?: return@delete
            val endpointId = call.requiredAdminId("endpointId") ?: return@delete
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            if (aiService.deleteEndpoint(endpointId)) {
                call.respondSuccess(mapOf("success" to true))
            } else {
                call.respondNotFound("AI endpoint not found")
            }
        }

        post("/ai-endpoints/{endpointId}/models") {
            call.requireAdminUser() ?: return@post
            val endpointId = call.requiredAdminId("endpointId") ?: return@post
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                call.respondSuccess(HttpStatusCode.Created, aiService.createModel(endpointId, call.receive<AiModelRequest>()))
            } catch (e: AiConfigurationNotFoundException) {
                call.respondNotFound(e.message ?: "AI endpoint not found")
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        put("/ai-models/{modelId}") {
            call.requireAdminUser() ?: return@put
            val modelId = call.requiredAdminId("modelId") ?: return@put
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            try {
                val model = aiService.updateModel(modelId, call.receive<AiModelRequest>())
                if (model == null) {
                    call.respondNotFound("AI model not found")
                } else {
                    call.respondSuccess(model)
                }
            } catch (e: AiConfigurationNotFoundException) {
                call.respondNotFound(e.message ?: "AI endpoint not found")
            } catch (e: IllegalArgumentException) {
                call.respondValidationError(e)
            }
        }

        delete("/ai-models/{modelId}") {
            call.requireAdminUser() ?: return@delete
            val modelId = call.requiredAdminId("modelId") ?: return@delete
            val aiService = get<AiConfigurationService>(AiConfigurationService::class.java)
            if (aiService.deleteModel(modelId)) {
                call.respondSuccess(mapOf("success" to true))
            } else {
                call.respondNotFound("AI model not found")
            }
        }
    }
}

private suspend fun ApplicationCall.requiredAdminId(name: String): Int? {
    val id = parameters[name]?.toIntOrNull()
    if (id == null) {
        respondError(ErrorCode.GEN_BAD_REQUEST, "Invalid $name")
    }
    return id
}

private suspend fun ApplicationCall.respondValidationError(error: IllegalArgumentException) {
    respondError(ErrorCode.GEN_BAD_REQUEST, error.message)
}

private suspend fun ApplicationCall.respondNotFound(details: String) {
    respondError(HttpStatusCode.NotFound, ErrorCode.GEN_BAD_REQUEST, details)
}
