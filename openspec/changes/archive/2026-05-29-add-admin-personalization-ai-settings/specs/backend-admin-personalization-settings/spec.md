## ADDED Requirements

### Requirement: Backend persists administrator personalization settings
The backend SHALL persist administrator-owned personalization settings using deterministic schema migrations.

#### Scenario: Settings schema is missing
- **WHEN** the backend starts against a database without personalization settings tables
- **THEN** schema migrations SHALL create the required settings and AI configuration tables without dropping existing user data.

#### Scenario: Time zone setting is missing
- **WHEN** no persisted time zone setting exists
- **THEN** the backend SHALL initialize the active time zone from a valid `TZ` environment value.
- **AND** it SHALL fall back to the system default time zone when `TZ` is absent or invalid.

### Requirement: Backend updates runtime time zone from persisted settings
The backend SHALL use a persisted time zone setting as the runtime application time zone after initialization.

#### Scenario: Valid time zone is saved
- **WHEN** an administrator saves a valid time zone ID
- **THEN** the backend SHALL persist the value.
- **AND** subsequent `TimeProvider` local datetime operations SHALL use the saved time zone.

#### Scenario: Invalid time zone is saved
- **WHEN** an administrator saves an invalid or blank time zone ID
- **THEN** the backend SHALL reject the request with a validation error.
- **AND** the active runtime time zone SHALL remain unchanged.

### Requirement: Backend exposes personalization APIs only to administrators
The backend SHALL require administrator authorization before reading or mutating personalization and AI configuration.

#### Scenario: Request has no token
- **WHEN** a request calls a personalization or AI configuration endpoint without a bearer token
- **THEN** the backend SHALL return the existing no-token authentication error.
- **AND** it SHALL NOT execute the settings operation.

#### Scenario: Request has non-admin token
- **WHEN** a request calls a personalization or AI configuration endpoint with a non-admin user token
- **THEN** the backend SHALL return the existing admin-required error.
- **AND** it SHALL NOT execute the settings operation.

#### Scenario: Request has admin token
- **WHEN** an administrator calls a personalization or AI configuration endpoint
- **THEN** the backend SHALL execute the requested settings operation and return the standard success envelope.

### Requirement: Backend manages AI providers with priority
The backend SHALL allow administrators to create, update, delete, enable, disable, and list AI providers ordered by priority.

#### Scenario: Provider is valid
- **WHEN** an administrator saves a provider with name, provider kind, enabled state, and priority
- **THEN** the backend SHALL persist the provider.
- **AND** provider list responses SHALL be ordered by priority.
- **AND** provider request and response DTOs SHALL NOT contain TTS or LLM capability fields.

#### Scenario: Provider is deleted
- **WHEN** an administrator deletes a provider
- **THEN** the backend SHALL delete its endpoints and models as owned configuration.

### Requirement: Backend manages provider endpoints with secret-safe API keys
The backend SHALL allow administrators to manage provider endpoints while keeping API keys write-only.

#### Scenario: Endpoint is valid
- **WHEN** an administrator saves an endpoint with base URL, API key state, concurrency limit, enabled state, and priority
- **THEN** the backend SHALL persist the endpoint under the provider.
- **AND** endpoint list responses SHALL be ordered by priority within each provider.
- **AND** endpoint request and response DTOs SHALL NOT contain TTS or LLM capability fields.

#### Scenario: Endpoint response is returned
- **WHEN** the backend returns endpoint configuration
- **THEN** the response SHALL include whether an API key is configured.
- **AND** the response SHALL NOT include the plaintext API key.

#### Scenario: Endpoint update omits API key
- **WHEN** an administrator updates endpoint fields without supplying an API key
- **THEN** the backend SHALL preserve the existing stored API key.

### Requirement: Backend manages models under provider endpoints
The backend SHALL allow administrators to manage AI models under provider endpoints.

#### Scenario: Model is valid
- **WHEN** an administrator saves a model with model name, display name, at least one capability flag, enabled state, and priority
- **THEN** the backend SHALL persist the model under the endpoint.
- **AND** model list responses SHALL be ordered by priority within each endpoint.

#### Scenario: Model has no capability
- **WHEN** an administrator saves a model with both TTS and LLM disabled
- **THEN** the backend SHALL reject the request with a validation error.

### Requirement: Backend does not execute AI integrations
The backend SHALL store AI configuration metadata without calling configured AI services in this change.

#### Scenario: AI configuration is saved
- **WHEN** an administrator saves AI provider, endpoint, or model configuration
- **THEN** the backend SHALL NOT call external TTS or LLM APIs.
- **AND** it SHALL NOT expose generation, dispatch, retry, or fallback endpoints for AI execution.
