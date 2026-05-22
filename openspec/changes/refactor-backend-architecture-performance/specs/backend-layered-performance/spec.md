## ADDED Requirements

### Requirement: Routes delegate business work to services
Backend routes SHALL only parse request inputs, perform authentication checks, and return standardized responses; business work and repository access SHALL be delegated to domain services.

#### Scenario: Route currently accesses a repository
- **WHEN** a route is touched by the architecture performance refactor
- **THEN** direct repository access in that route SHALL be moved behind a service method that preserves the existing response behavior.

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
