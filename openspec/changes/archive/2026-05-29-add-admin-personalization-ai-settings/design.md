## Context

Backend startup initializes the database, runs versioned migrations, configures Koin, then starts services such as background parsing. `TimeProvider` currently resolves `TZ` lazily and stores it as a process-wide value. This change introduces persisted administrator settings, so the time zone path must become explicitly initialized and runtime-updatable without requiring environment mutation.

AI service settings are configuration-only in this change. They need durable schema and API contracts now so later TTS implementation can select configured TTS-capable models without changing how administrators manage credentials and model metadata.

## Goals / Non-Goals

**Goals:**
- Persist application time zone and AI service configuration.
- Keep `TZ` as fallback for first startup or missing setting.
- Add administrator-only APIs using the shared admin helper.
- Validate provider, endpoint, model capability, priority, base URL, concurrency, and time zone inputs.
- Preserve API keys on partial endpoint updates and never return plaintext keys.
- Cover repository, service, and route behavior with focused tests.

**Non-Goals:**
- No outbound AI HTTP client.
- No TTS generation or LLM execution.
- No provider health checking, quota tracking, fallback, or dispatch.
- No new dependency unless it is required for safe local secret storage and approved during implementation.

## Decisions

### Add dedicated backend entities

Use separate tables for:

- `system_settings`: unique setting key, value, and updated timestamp.
- `ai_providers`: provider name, kind, enabled state, priority, timestamps.
- `ai_provider_endpoints`: provider foreign key, base URL, API key secret, concurrency limit, enabled state, priority, timestamps.
- `ai_models`: endpoint foreign key, model name, display name, TTS/LLM capability flags, enabled state, priority, timestamps.

Separate tables keep endpoint credentials and model metadata normalized and support cascading cleanup when providers or endpoints are deleted.

### Isolate validation in services

Routes should remain thin and call service methods after administrator authorization. Services own validation rules:

- Time zone must be a valid non-blank time zone ID.
- Provider and endpoint records do not include TTS/LLM capability fields.
- Model records must enable at least one capability.
- Endpoint concurrency must be at least one.
- Provider, endpoint, and model list responses must be priority ordered.

### Keep API keys write-only

Endpoint create/update request DTOs may include an API key. Response DTOs expose `apiKeySet` and optionally a masked preview, but never plaintext. If an update omits the API key, the service keeps the existing stored secret; if it supplies a non-blank key, the service replaces it.

### Make time zone runtime-updatable

The persisted setting service initializes the active time zone during application startup after migrations and before routes/background services are used. Updating the time zone through the admin API persists the setting and updates the in-process time zone used by `TimeProvider`.

## Risks / Trade-offs

- Startup order becomes more important. → Initialize settings after migrations and before background services start.
- Time zone changes affect new timestamps but not already persisted local datetimes. → Document this behavior in service tests and avoid rewriting historical rows.
- Secret storage may need hardening later. → Keep route responses write-only and isolate storage behind the repository/service boundary.
- Multiple priority fields can be confusing. → Sort consistently by provider, endpoint, then model priority and cover ordering with tests.
- Databases created by earlier drafts may contain provider/endpoint capability columns. → Add a versioned cleanup migration that drops those unused columns without touching model capability metadata.

## Migration Plan

1. Add versioned migrations for new settings and AI configuration tables.
2. Add repositories and services.
3. Initialize persisted time zone from existing `TZ` fallback only if absent.
4. Update `TimeProvider` to support an initialized mutable application time zone.
5. Add admin routes and DI registrations.
6. Add tests before wiring the Web page to the new APIs.
