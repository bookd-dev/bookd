## Why

The backend currently treats application time zone as deployment-only `TZ` configuration, while future TTS work needs persisted external AI service configuration before any runtime integration is added. A backend capability is needed to persist, validate, protect, and expose these administrator-owned settings.

## What Changes

- Add persisted system settings for application time zone, with `TZ` retained as the initialization/default fallback.
- Add administrator-only APIs for reading and updating personalization settings.
- Add persisted AI provider, endpoint, and model configuration records.
- Represent TTS and LLM as independent capability flags on model records only; providers and endpoints do not expose capability fields.
- Support create, update, delete, enable/disable, and priority ordering for AI providers, endpoints, and models.
- Treat endpoint API keys as write-only secrets that are never returned in plaintext.
- Do not implement TTS generation, LLM calls, provider dispatch, health checks, or fallback execution in this change.

## Capabilities

### New Capabilities
- `backend-admin-personalization-settings`: Defines backend persistence, validation, runtime time zone handling, admin-only APIs, and secret-safe AI configuration responses.

### Modified Capabilities
- None.

## Impact

- Database migrations and Exposed entities for settings and AI configuration, including cleanup of unused provider/endpoint capability columns from earlier drafts.
- Repositories and services for settings, AI providers, endpoints, and models.
- `TimeProvider` initialization and runtime update behavior.
- New Ktor routes under `/api/admin/*`.
- Koin module registrations.
- Backend unit and route tests for validation, ordering, authorization, cascade deletion, and API key masking.
