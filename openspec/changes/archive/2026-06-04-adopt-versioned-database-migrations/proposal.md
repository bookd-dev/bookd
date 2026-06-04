## Why

Backend startup currently creates missing tables and columns with Exposed auto-DDL. This works locally but is deprecated and risky for production because failures during startup schema mutation can leave partial state.

This change introduces explicit versioned schema management for the backend.

## What Changes

- Add a production schema migration mechanism.
- Represent the current schema as a baseline migration set.
- Replace production startup auto-DDL with migration execution.
- Keep tests able to create isolated transient schemas.

## Capabilities

### New Capabilities

- `backend-schema-management`: covers deterministic backend database schema migration.

### Modified Capabilities

- `server-gradle-dependency-baseline`: may be updated if a migration tool dependency is added.

## Impact

- Backend application startup, database initialization, build dependencies if needed, deployment documentation, and startup tests.
- No REST API or client changes.
