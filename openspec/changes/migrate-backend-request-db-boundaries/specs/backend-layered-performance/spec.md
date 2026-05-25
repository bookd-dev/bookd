## ADDED Requirements

### Requirement: Remaining route-facing repositories use the database execution boundary
Route-facing auth, user, tag, and bookshelf database work SHALL migrate to suspend repository methods backed by `DatabaseExecutor.dbQuery`.

#### Scenario: Auth and user paths are migrated
- **WHEN** route-facing auth or user-management methods perform database work
- **THEN** they SHALL use suspend repository methods backed by `DatabaseExecutor.dbQuery`
- **AND** login, logout, token validation, user deletion, and invite-token semantics SHALL remain compatible.

#### Scenario: Tag paths are migrated
- **WHEN** route-facing tag methods perform database work
- **THEN** they SHALL use suspend repository methods backed by `DatabaseExecutor.dbQuery`
- **AND** duplicate tag association behavior SHALL remain idempotent.

#### Scenario: Bookshelf paths are migrated
- **WHEN** route-facing bookshelf methods perform database work
- **THEN** they SHALL use suspend repository methods backed by `DatabaseExecutor.dbQuery`
- **AND** ownership checks, default shelf behavior, paging, and ordering SHALL remain compatible.
