## Context

The existing migration implementation loads all candidates in direct transactions, probes image files, then writes updates in another direct transaction. It already avoids file IO inside the transaction loop, but it does not use the repository/database execution boundary established by recent refactors.

## Goals

- Separate migration data access from migration orchestration.
- Keep database work behind `DatabaseExecutor.dbQuery`.
- Keep file IO under coroutine IO execution.
- Preserve exact route behavior and response shape.

## Non-Goals

- No endpoint redesign.
- No new database columns or indexes.
- No migration framework work.
- No broad conversion of unrelated repositories.

## Approach

- Introduce `ImageDimensionMigrationRepository`.
- Move candidate DTOs that represent database rows into the repository or repository-adjacent model.
- Keep update DTOs internal to the migration path.
- Have `ImageDimensionMigrationService` call repository suspend methods, perform image dimension extraction, and pass update batches back.
- Update route tests to use coroutine-aware mocks after service methods become suspend.

## Risks

- Route test mocks must distinguish admin-auth rejection from service execution.
- Success and failure counts must continue to reflect extraction results.
- Legacy `/covers/` behavior must remain unchanged.
