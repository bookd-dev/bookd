## 1. OpenSpec

- [x] 1.1 Create root and backend OpenSpec artifacts for `refactor-backend-architecture-v3`.
- [x] 1.2 Validate root and backend changes with `openspec validate --strict`.

## 2. Task Coordination

- [x] 2.1 Add a shared task coordinator for metadata and content parsing work.
- [x] 2.2 Refactor content parsing and metadata extraction services to use the coordinator.
- [x] 2.3 Make background parse service restart-safe after stop/start.

## 3. Route And Service Boundaries

- [x] 3.1 Move cover upload workflow behind `BookService`.
- [x] 3.2 Move registration bookshelf initialization behind `UserService`.
- [x] 3.3 Move TXT parse rule JSON import workflow behind `TxtParseRuleService`.
- [x] 3.4 Move filesystem browsing workflow behind a domain service.

## 4. Repository And Migration Hot Paths

- [x] 4.1 Add tag batch lookup/link methods and use them from metadata, auto-tag, and merge paths.
- [x] 4.2 Add safe indexes for tag-to-book lookup paths.
- [x] 4.3 Refactor image dimension migration so file IO occurs outside database transactions.

## 5. Verification

- [x] 5.1 Extend the local ebook benchmark with v3 before/after scenarios using `/Users/***/ebook` when configured.
- [x] 5.2 Add or update focused unit/route tests for touched behavior.
- [x] 5.3 Run backend tests and record benchmark evidence.
