## Why

The Ktor backend has grown around correct but uneven layering: some routes access repositories directly, most repository calls use blocking Exposed transactions, and high-frequency endpoints contain N+1 query patterns. This change defines the backend-specific refactor needed to keep behavior stable while improving response speed and maintainability.

## What Changes

- Refactor backend internals toward stricter `routes -> service -> repository` boundaries.
- Add batch and aggregate repository methods for book statistics, document navigation, and resource lookup.
- Add a unified database execution boundary for blocking Exposed work.
- Add safe indexes for existing high-frequency query paths.
- Keep all current REST API paths, response payloads, error codes, authentication behavior, and parsing semantics compatible.
- Preserve API and media routes for the React admin migration without preserving the old static HTML admin page as the target UI behavior.
- No breaking API changes.

## Capabilities

### New Capabilities

- `backend-layered-performance`: Defines backend-specific requirements for layered routing, database execution boundaries, query aggregation, indexing, cache fallback, and regression tests.

### Modified Capabilities

None.

## Impact

- Affects `routes`, `domain/service`, `data/repository`, `data/entity`, `config`, `plugins`, and `infrastructure/cache`.
- Adds or updates backend unit tests under `bookd/src/test/kotlin`.
- May add non-breaking Exposed indexes to existing tables.
- Does not require client changes.
- Does not preserve legacy static admin HTML behavior.
