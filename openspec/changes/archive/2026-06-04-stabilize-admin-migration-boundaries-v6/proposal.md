## Why

The admin image-dimension migration path is operational and potentially expensive. The current route is protected, but the service still mixes migration orchestration with direct Exposed transactions.

This change completes the safe boundary cleanup for that path without changing API behavior.

## What Changes

- Add repository methods for missing resource-dimension candidates, missing cover-dimension candidates, and batch dimension updates.
- Execute those repository methods through `DatabaseExecutor.dbQuery`.
- Convert image-dimension migration service methods to suspend functions.
- Keep image probing and legacy cover-path handling outside database transactions.
- Keep admin route response fields unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: strengthen the image-dimension migration execution-boundary requirement.

## Impact

- `ImageDimensionMigrationService`, new migration repository, admin routes, Koin registration, and focused tests.
- No API, schema, client, or media route changes.
