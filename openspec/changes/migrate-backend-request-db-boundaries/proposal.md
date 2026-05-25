## Why

Some request-facing services still call repositories backed by direct `transaction {}` methods. The backend now has an established `DatabaseExecutor.dbQuery` boundary, so remaining request paths should migrate gradually by domain.

## What Changes

- Add suspend repository/service methods for selected auth/user, tag, and bookshelf request paths.
- Keep synchronous wrappers where untouched callers still need them.
- Preserve route paths, response shapes, sorting, pagination, ownership checks, and error codes.
- Add focused tests for each migrated domain.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: extends database execution-boundary requirements to remaining request-facing domains.

## Impact

- `UserRepository`/`UserService`, `TagRepository`/`TagService`, `BookshelfRepository`/`BookshelfItemRepository`/`BookshelfService`, and corresponding routes/tests.
- No schema or client changes.
