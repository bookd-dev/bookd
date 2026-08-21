# backend-layered-performance Specification

## Purpose
Define backend layering, database execution, query batching, index, cache fallback, admin web boundary, and regression-test requirements for API-compatible performance refactors.
## Requirements
### Requirement: Routes delegate business work to services
Backend routes SHALL only parse request inputs, perform authentication checks, and return standardized responses; business work and repository access SHALL be delegated to domain services.

#### Scenario: Route currently accesses a repository
- **WHEN** a route is touched by the architecture performance refactor
- **THEN** direct repository access in that route SHALL be moved behind a service method that preserves the existing response behavior.

#### Scenario: Touched route owns multipart, import, or filesystem workflow
- **WHEN** cover upload, registration bookshelf initialization, TXT rule import, or filesystem browsing behavior is touched
- **THEN** the business workflow SHALL be moved behind a service method or small service result type.
- **AND** the route SHALL preserve the same HTTP status and `ErrorCode` behavior for representative success and failure cases.

#### Scenario: Service raises business errors
- **WHEN** service logic detects invalid input, missing resources, or forbidden access
- **THEN** it SHALL preserve existing `ErrorCode` semantics through the established response or exception handling path.

### Requirement: Blocking database work uses a consistent execution boundary
Repository operations using Exposed/JDBC SHALL run behind a consistent database execution boundary so blocking database work does not run directly on request coroutine execution.

#### Scenario: Repository method is added or refactored
- **WHEN** a repository method is introduced or substantially changed
- **THEN** it SHALL use the backend database execution boundary for Exposed transactions.

#### Scenario: Service coordinates multiple database reads
- **WHEN** a service coordinates multiple repository reads for one API response
- **THEN** the service SHALL avoid holding a long transaction across file parsing, network cache access, or other blocking non-database work.

#### Scenario: Touched request work performs blocking operational access
- **WHEN** a route-touched service operation lists directories, validates filesystem paths, reads filesystem roots, scans book sources, or computes background status using blocking database work
- **THEN** the operation SHALL run behind a coroutine IO or database execution boundary.
- **AND** route responses SHALL preserve the same success and error semantics.

#### Scenario: Image dimensions are migrated
- **WHEN** image dimension migration reads image files from storage
- **THEN** file IO SHALL happen outside long database transactions.
- **AND** database updates SHALL be written in short transactions.

### Requirement: Backend background tasks have a coordinated lifecycle
Backend asynchronous parsing and metadata tasks SHALL use a coordinated lifecycle boundary instead of each touched service owning unrelated coroutine state.

#### Scenario: Content parsing is queued
- **WHEN** a book content parse is queued asynchronously
- **THEN** duplicate parse launches for the same book SHALL be skipped while one is already in flight.
- **AND** the task SHALL remove the in-flight marker after success or failure.

#### Scenario: Background parse service is restarted
- **WHEN** the background parse service is stopped and then started again
- **THEN** future scheduled parsing attempts SHALL still be able to launch.

#### Scenario: Backend application stops
- **WHEN** the Ktor application stops
- **THEN** background parsing, shared book task coordination, and optional Redis resources SHALL be closed or stopped without leaking long-lived executors or connections.
- **AND** cleanup SHALL be safe to invoke more than once.

### Requirement: Hot-path queries are aggregated or batched
Backend services SHALL avoid repeated per-item database lookups on high-frequency API paths when equivalent batched or aggregate queries can preserve behavior.

#### Scenario: Book list statistics are returned
- **WHEN** book list or book-source paged list endpoints return chapter count, word count, image count, and cover ratio
- **THEN** document statistics SHALL be loaded with a batched or aggregate repository query rather than a per-book document query loop.

#### Scenario: Chapter content transforms image resources
- **WHEN** chapter content is returned with image URLs, dimensions, and aspect ratios
- **THEN** document resources for that chapter response SHALL be loaded with a batched lookup rather than one database query per image element.

#### Scenario: Chapter navigation is returned
- **WHEN** chapter content includes previous and next chapter indexes
- **THEN** navigation data SHALL be loaded through a lightweight repository query without requiring all document content rows.

### Requirement: Existing query paths have safe indexes
The backend SHALL add non-breaking indexes for existing high-frequency filtering, joining, and ordering paths when those indexes improve the refactored query plan.

#### Scenario: Book lists are filtered by source
- **WHEN** books are listed or counted by source
- **THEN** the database schema SHALL include indexes that support source filtering and title ordering without changing table semantics.

#### Scenario: Documents and resources are loaded by book
- **WHEN** document, table-of-contents, content, or resource records are loaded for a book
- **THEN** the database schema SHALL include indexes that support the existing lookup predicates.

#### Scenario: User-scoped reading data is loaded
- **WHEN** reading progress, bookmarks, reader settings, or bookshelf membership are loaded for a user and book
- **THEN** the database schema SHALL include indexes that support the existing user-scoped lookup predicates.

### Requirement: Optional cache remains a fallback accelerator
Redis-backed cache behavior SHALL remain optional and SHALL NOT become the only source of truth for book parsing or content metadata.

#### Scenario: Redis is disabled or unavailable
- **WHEN** Redis is disabled, unavailable, or returns an error
- **THEN** backend endpoints SHALL continue to use database-backed behavior and preserve existing API responses.

#### Scenario: Cached parse state is missing
- **WHEN** parsed-state cache is missing for a book
- **THEN** the backend SHALL check database parse state before deciding whether parsing is required.

### Requirement: Legacy admin HTML is not a backend performance compatibility target
The backend performance refactor SHALL NOT preserve legacy static admin HTML behavior as a compatibility requirement; admin web document routing SHALL follow the React admin migration change.

#### Scenario: Web document routing is touched
- **WHEN** backend route or static hosting code related to `/`, `/login`, `/setup`, `/reader`, `/admin`, or `/admin/*` is touched during performance work
- **THEN** the implementation SHALL preserve `/api/*`, `/covers`, and `/book_images` routing behavior.
- **AND** it SHALL NOT reintroduce or depend on the old static HTML admin document as the target UI behavior.

#### Scenario: Admin workflows are considered during backend refactor
- **WHEN** a backend service or API used by the admin UI is refactored
- **THEN** the backend API contract SHALL remain compatible for the React admin migration instead of preserving legacy HTML page internals.

### Requirement: Refactored backend behavior is covered by tests
Backend refactor tasks SHALL add or update tests for each modified business path.

#### Scenario: Book statistics query is refactored
- **WHEN** book list statistics loading changes
- **THEN** tests SHALL verify returned chapter count, word count, image count, and cover aspect ratio remain compatible with existing behavior.

#### Scenario: Chapter content loading is refactored
- **WHEN** chapter content resource lookup or navigation changes
- **THEN** tests SHALL verify image URL transformation, dimensions, aspect ratio, previous chapter index, and next chapter index remain compatible.

#### Scenario: Route delegation changes
- **WHEN** a route is changed to delegate repository work through a service
- **THEN** route or service tests SHALL verify the same success and error response outcomes for representative requests.

#### Scenario: Task coordination changes
- **WHEN** parsing or metadata background task coordination changes
- **THEN** tests SHALL verify duplicate suppression, completion cleanup, closed-state behavior, and restart behavior where applicable.

#### Scenario: Blocking boundary changes
- **WHEN** a filesystem or background status operation is moved to an IO-safe boundary
- **THEN** tests SHALL verify representative success and failure responses remain compatible.

### Requirement: Backend runtime resources remain bounded

Backend runtime services SHALL avoid unbounded in-process retention and SHALL close owned runtime resources through the coordinated backend lifecycle.

#### Scenario: Token validation cache contains expired entries

- **WHEN** token validation runs after cached entries have exceeded the token-cache TTL
- **THEN** expired entries SHALL be pruned without requiring those exact token strings to be validated again
- **AND** repeated valid token validation within the TTL SHALL still avoid repeated repository lookups.

#### Scenario: Runtime configuration controls background parsing

- **WHEN** deployment config provides `PARSE_BACKGROUND_ENABLED`, `PARSE_BACKGROUND_INTERVAL`, or `PARSE_BACKGROUND_BATCH_SIZE`
- **THEN** the background parse service SHALL honor those values
- **AND** `BACKGROUND_PARSE_ENABLED`, `BACKGROUND_PARSE_INTERVAL`, and `BACKGROUND_PARSE_BATCH_SIZE` SHALL remain fallback names for compatibility.

#### Scenario: Backend lifecycle cleanup runs

- **WHEN** the backend lifecycle service closes
- **THEN** background parsing, shared task coordination, optional Redis resources, and the active JDBC datasource SHALL be closed at most once.

#### Scenario: Redis health check fails during startup

- **WHEN** Redis is enabled but the created Redis service fails its startup ping
- **THEN** the backend SHALL close that Redis service and continue with cache disabled.

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

### Requirement: Backend code-quality refactors preserve layering
Backend code-quality refactors SHALL reduce concrete duplication or complexity while preserving the established route-service-repository layering.

#### Scenario: Route helpers are introduced
- **WHEN** route parameter or query parsing is moved to shared helpers
- **THEN** each caller SHALL pass or preserve its existing `ErrorCode`
- **AND** successful response behavior SHALL remain unchanged.

#### Scenario: Extension package naming is normalized
- **WHEN** route extension helpers are moved into the normal backend extension package
- **THEN** all call sites SHALL import the normalized package
- **AND** public cover URL conversion SHALL preserve existing behavior.

#### Scenario: Content image transformation is extracted
- **WHEN** image transformation logic is shared between regular images and footnote images
- **THEN** URL construction, dimensions, aspect ratio, and missing-resource behavior SHALL remain compatible.

#### Scenario: Route response mapping is extracted
- **WHEN** response mapping helpers are introduced
- **THEN** routes SHALL continue to parse transport input and delegate business work to services
- **AND** helpers SHALL NOT introduce new business rules.

### Requirement: Admin image migration data access is repository-owned
Image-dimension migration database access SHALL be delegated to a repository while the service coordinates image probing and result counting.

#### Scenario: Resource dimension candidates are migrated
- **WHEN** resource image candidates are loaded or updated
- **THEN** the repository SHALL execute database work through `DatabaseExecutor.dbQuery`
- **AND** image file probing SHALL happen outside the database transaction.

#### Scenario: Cover dimension candidates are migrated
- **WHEN** book cover candidates are loaded or updated
- **THEN** the repository SHALL execute database work through `DatabaseExecutor.dbQuery`
- **AND** legacy `/covers/` and `/book_images/` path handling SHALL preserve existing behavior.

#### Scenario: Admin route executes migration
- **WHEN** an administrator calls an image-dimension migration endpoint
- **THEN** the route SHALL await the migration service result
- **AND** it SHALL preserve the current success response fields.
