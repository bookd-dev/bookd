## ADDED Requirements

### Requirement: Backend runtime resources remain bounded

Backend runtime services SHALL avoid unbounded in-process retention and SHALL close owned runtime resources through the coordinated backend lifecycle.

#### Scenario: Token validation cache contains expired entries

- **WHEN** token validation runs after cached entries have exceeded the token-cache TTL
- **THEN** expired entries SHALL be pruned without requiring those exact token strings to be validated again
- **AND** repeated valid token validation within the TTL SHALL still avoid repeated repository lookups.

#### Scenario: Runtime configuration controls background parsing

- **WHEN** deployment config provides `PARSE_BACKGROUND_ENABLED`, `PARSE_BACKGROUND_INTERVAL`, or `PARSE_BACKGROUND_BATCH_SIZE`
- **THEN** the background parse service SHALL honor those values
- **AND** `BACKGROUND_PARSE_ENABLED`, `BACKGROUND_PARSE_INTERVAL`, and `BACKGROUND_PARSE_BATCH_SIZE` SHALL remain fallback names for compatibility.

#### Scenario: Backend lifecycle cleanup runs

- **WHEN** the backend lifecycle service closes
- **THEN** background parsing, shared task coordination, optional Redis resources, and the active JDBC datasource SHALL be closed at most once.

#### Scenario: Redis health check fails during startup

- **WHEN** Redis is enabled but the created Redis service fails its startup ping
- **THEN** the backend SHALL close that Redis service and continue with cache disabled.
