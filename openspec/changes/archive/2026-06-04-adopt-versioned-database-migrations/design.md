## Context

`Application.module()` initializes the database and calls `SchemaUtils.createMissingTablesAndColumns`. The warning from Exposed recommends using explicit migration statements with a migration tool.

## Goals

- Make schema upgrades deterministic.
- Avoid deprecated production startup schema mutation.
- Preserve existing local and test workflows as much as possible.
- Support existing databases without data loss.

## Non-Goals

- No schema redesign.
- No data model cleanup.
- No API change.
- No frontend change.

## Approach

- Add a schema migration runner integrated immediately after `DatabaseConfig.init`.
- Add baseline migrations matching the currently declared Exposed tables.
- Gate any test-only schema creation to test code or test configuration.
- Fail startup if production migrations fail, before plugins, routes, or background services start.
- Document the baseline behavior for existing deployments.

## Risks

- Existing databases without migration metadata need a safe baseline path.
- Migration execution must be idempotent across restarts.
- Build/deployment scripts must include migration resources.
