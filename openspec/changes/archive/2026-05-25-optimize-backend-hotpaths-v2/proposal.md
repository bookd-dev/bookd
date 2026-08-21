## Why

The backend still has several API-compatible hot paths that perform more work than necessary after the first optimization pass. Book statistics are computed at read time, bookshelf counts and membership checks still have repeated queries, scan import checks each file path separately, parsing writes one record at a time, and token validation hits the database on every authenticated request.

## What Changes

- Add internal book statistics columns and keep them in sync during parsing/backfill.
- Refactor bookshelf list, book detail, and shelf paging queries to batch or join data.
- Batch scan duplicate detection and parser persistence work.
- Add short-lived token validation caching with explicit invalidation.
- Preserve all current API contracts and media routes.

## Capabilities

### New Capabilities

- `backend-hotpath-performance`: Defines backend-specific requirements for API-compatible v2 hot-path optimization.

## Impact

- Affects `data/entity`, `data/repository`, `domain/service`, `config`, `extension`, and backend tests.
- Adds non-breaking database columns and indexes.
- Does not affect frontend code or API shape.
