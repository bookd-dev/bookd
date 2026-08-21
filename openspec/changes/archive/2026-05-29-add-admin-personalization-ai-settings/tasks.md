## 1. Schema And Persistence

- [x] 1.1 Add Exposed entities for system settings, AI providers, AI provider endpoints, and AI models.
- [x] 1.2 Add deterministic schema migrations for the new tables and unused provider/endpoint capability-column cleanup.
- [x] 1.3 Add repositories for settings, providers, endpoints, models, ordered queries, and owned cascading deletes.

## 2. Runtime Settings

- [x] 2.1 Update `TimeProvider` to support initialized and runtime-updated application time zones.
- [x] 2.2 Implement settings service logic for time zone fallback, validation, persistence, and runtime update.
- [x] 2.3 Initialize persisted settings during application startup after migrations and before background services start.

## 3. AI Configuration Services And Routes

- [x] 3.1 Add request and response DTOs for personalization settings, providers, endpoints, and models.
- [x] 3.2 Implement provider validation, CRUD, enable/disable, and priority-ordered listing.
- [x] 3.3 Implement endpoint validation, CRUD, enable/disable, priority-ordered listing, concurrency checks, and API key preservation/replacement.
- [x] 3.4 Implement model validation, CRUD, enable/disable, priority-ordered listing, and capability checks.
- [x] 3.5 Add administrator-only Ktor routes under `/api/admin/*`.
- [x] 3.6 Register new repositories and services in Koin.

## 4. Backend Tests

- [x] 4.1 Add repository/service tests for settings fallback, valid/invalid time zones, and runtime time zone update.
- [x] 4.2 Add repository/service tests for provider, endpoint, model capability validation, ordering, cascading deletion, and legacy capability-column cleanup.
- [x] 4.3 Add tests proving API keys are write-only, preserved when omitted, and replaceable when provided.
- [x] 4.4 Add route tests for no-token, non-admin, and admin requests.
- [x] 4.5 Run targeted Gradle tests for new backend settings and AI configuration behavior.
