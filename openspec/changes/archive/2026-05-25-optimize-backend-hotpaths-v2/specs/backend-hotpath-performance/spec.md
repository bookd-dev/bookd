## ADDED Requirements

### Requirement: Book statistics are maintained internally
The backend SHALL avoid recomputing book list and detail statistics on every read when internal precomputed statistics are available.

#### Scenario: Book list or detail is returned
- **WHEN** a book response includes chapter count, total word count, total image count, or cover aspect ratio
- **THEN** the service SHALL populate those existing response fields from maintained internal book statistics when available.
- **AND** it SHALL fall back to document aggregation for rows that have not been backfilled.

#### Scenario: Book content parsing completes
- **WHEN** parsing creates or updates book documents
- **THEN** the backend SHALL update internal book statistic fields consistently with the saved document statistics.

### Requirement: Bookshelf aggregation avoids per-shelf and full-shelf repeated work
The backend SHALL batch bookshelf counts and push bookshelf book paging into repository queries while preserving existing ordering and response semantics.

#### Scenario: User bookshelves are listed
- **WHEN** a user requests their bookshelves
- **THEN** book counts SHALL be loaded in one batch query for the returned shelves.

#### Scenario: Books in a bookshelf are listed
- **WHEN** a user requests paged books in a bookshelf
- **THEN** sorting by latest reading time and unread fallback ordering SHALL be performed without loading all book IDs into service memory.

#### Scenario: Book detail includes bookshelf state
- **WHEN** book detail is returned for a user
- **THEN** bookshelf membership and default-shelf membership SHALL be loaded without separate per-shelf count queries.

### Requirement: Parse, scan, and auth hot paths reduce repeated work
The backend SHALL batch or cache repeated internal operations that do not change API behavior.

#### Scenario: A directory is scanned
- **WHEN** many supported book files are discovered
- **THEN** existing books SHALL be detected using batched file-path lookups rather than one lookup per file.

#### Scenario: Parsed documents and resources are persisted
- **WHEN** a parser produces multiple documents, contents, stats, or resources
- **THEN** database writes SHALL be batched in short transactions while file parsing and image processing remain outside database transactions.

#### Scenario: Authenticated requests validate a token
- **WHEN** the same valid token is checked repeatedly within a short interval
- **THEN** validation MAY use an in-process TTL cache.
- **AND** logout or user deletion SHALL invalidate affected cached credentials.

### Requirement: V2 hot-path behavior is covered by tests
Every modified v2 hot path SHALL have focused tests that verify preserved behavior and reduced repeated work.

#### Scenario: V2 implementation is completed
- **WHEN** the implementation is finished
- **THEN** backend tests SHALL pass and OpenSpec validations SHALL pass in strict mode.
