## Why

The backend memory diagnosis found no active leak in the running container, but it did identify low-risk retention and cleanup gaps around token caching, environment configuration, Redis startup, and JDBC lifecycle handling. Tightening these paths reduces false leak signals and keeps runtime resources bounded.

## What Changes

- Prune expired in-process token-cache entries during validation.
- Read documented `PARSE_BACKGROUND_*` background parser settings before legacy `BACKGROUND_PARSE_*` names.
- Close the active Hikari datasource during backend lifecycle cleanup.
- Treat Redis ping failure as cache unavailable and close the created Redis resources before falling back.
- Cover the changed behavior with focused backend unit tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: add runtime resource cleanup and bounded cache expectations.

## Impact

- Affected code: `UserService`, `BackgroundParseService`, `DatabaseConfig`, `BackendLifecycleService`, and Redis wiring in `KoinModule`.
- No REST API, response envelope, database schema, or client contract changes.
