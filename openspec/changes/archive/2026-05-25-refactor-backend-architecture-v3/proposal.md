## Why

The backend has already optimized the largest list/detail hot paths, but several remaining areas still mix route orchestration, background lifecycle, repeated tag writes, and file IO inside database transactions. This change tightens those boundaries while keeping all client-visible behavior compatible.

## What Changes

- Add a shared internal task coordinator for content parsing and metadata extraction.
- Move touched route business logic for cover uploads, registration bookshelf initialization, TXT rule import, and filesystem browsing into services.
- Add tag batch repository/service operations and safe tag association indexes.
- Refactor image-dimension migration to use short database transactions around filesystem work.
- Extend local benchmark coverage using `/Users/***/ebook` for before/after comparison.
- No public API changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: Extends route delegation, blocking-work boundaries, task lifecycle, and migration transaction requirements.
- `backend-hotpath-performance`: Extends hot-path validation to include tag batching and local v3 benchmark evidence.

## Impact

- Affects backend services, repositories, selected route files, DI registration, entity indexes, and tests.
- Does not affect frontend code or external API contracts.
