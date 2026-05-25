## Why

The backend refactor series is functionally complete, but review found a few stability gaps that are better handled as a small compatibility-preserving pass. This avoids another large refactor while tightening lifecycle cleanup and request execution boundaries.

## What Changes

- Add Ktor application stop cleanup for background parsing, shared book task coordination, and optional Redis connections.
- Move touched filesystem and background status work behind IO-safe suspend boundaries.
- Add minimal async repository wrappers for touched database paths.
- Clean safe compiler warnings.
- Add focused lifecycle and compatibility tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: clarify lifecycle cleanup and touched blocking-boundary expectations for stability cleanup work.

## Impact

- Backend application lifecycle wiring, `BackgroundParseService`, `BookTaskCoordinator`, `FileSystemService`, selected repository methods, and tests.
- No API, schema, dependency, client, or media route contract changes.
