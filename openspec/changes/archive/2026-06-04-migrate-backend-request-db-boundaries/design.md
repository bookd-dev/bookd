## Context

Previous refactors migrated selected repositories and blocking paths. The remaining synchronous transaction usage is broad enough that converting everything at once would be risky.

## Goals

- Move request-triggered database work to `DatabaseExecutor.dbQuery`.
- Keep each domain migration behavior-preserving.
- Avoid unnecessary query rewrites.
- Keep synchronous APIs until callers are safely migrated.

## Non-Goals

- No full repository rewrite in one pass.
- No auth policy changes.
- No tag or bookshelf semantics changes.
- No response DTO changes.

## Approach

- Start with route-facing methods only.
- For each domain, add suspend repository methods that mirror current behavior.
- Convert service methods used by Ktor routes to suspend.
- Update routes and tests.
- Remove synchronous methods only in a later cleanup when no callers remain.

## Risks

- Auth cache invalidation must remain correct.
- Tag merge and association methods must remain idempotent.
- Bookshelf default shelf and ownership behavior must remain exact.
