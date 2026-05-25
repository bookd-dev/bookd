## 1. OpenSpec

- [x] 1.1 Add root and backend OpenSpec artifacts for `optimize-backend-hotpaths-v2`.
- [x] 1.2 Validate root and backend changes with `openspec validate --strict`.

## 2. Book Statistics

- [x] 2.1 Add additive `books` statistic columns and useful indexes.
- [x] 2.2 Read book statistics from precomputed columns, with document aggregate fallback for old rows.
- [x] 2.3 Update parse completion and startup backfill to maintain precomputed statistics.
- [x] 2.4 Cover precomputed and fallback statistics with unit tests.

## 3. Bookshelf And Detail Queries

- [x] 3.1 Add repository methods for batch bookshelf counts and user book membership summaries.
- [x] 3.2 Replace bookshelf list/detail N+1 counts with batch methods.
- [x] 3.3 Replace in-memory full shelf sorting/paging with SQL-level paging.
- [x] 3.4 Cover ordering, paging, totals, and membership semantics with tests.

## 4. Parse, Scan, Auth

- [x] 4.1 Batch document/content/stat/resource writes during parsing.
- [x] 4.2 Batch scan duplicate lookup by file paths.
- [x] 4.3 Add short-lived token validation caching and invalidation.
- [x] 4.4 Replace touched direct console logging with structured logger calls.

## 5. Verification

- [x] 5.1 Run `./gradlew test` from `bookd/`.
- [x] 5.2 Record local benchmark evidence for the optimized paths.
