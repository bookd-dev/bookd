## ADDED Requirements

### Requirement: Authenticated App book search API
The backend SHALL expose an authenticated App endpoint for searching books by metadata.

#### Scenario: Authenticated search request succeeds
- **WHEN** an authenticated App client requests `GET /api/app/books/search` with a non-blank `q` query parameter
- **THEN** the backend SHALL return an `AppBooksResponse` containing matching books, `total`, `limit`, `offset`, and `hasMore`.

#### Scenario: Unauthenticated search is rejected
- **WHEN** an unauthenticated client requests the App book search endpoint
- **THEN** the backend SHALL reject the request using the existing authentication error response.

#### Scenario: Blank query is rejected
- **WHEN** an authenticated App client requests the App book search endpoint with a missing or blank `q` query parameter
- **THEN** the backend SHALL return `BOOK_INVALID_PARAMS`.

### Requirement: Book search matches supported metadata fields
The backend SHALL search normalized book metadata fields without requiring the client to choose a field.

#### Scenario: Search matches title or author
- **WHEN** the search query matches a book title or author case-insensitively for ASCII text
- **THEN** the matching book SHALL be included in the paged search results.

#### Scenario: Search matches publication metadata
- **WHEN** the search query matches a book ISBN or publisher case-insensitively for ASCII text
- **THEN** the matching book SHALL be included in the paged search results.

#### Scenario: Optional source filter is provided
- **WHEN** the search request includes a valid `sourceId`
- **THEN** the backend SHALL limit matching results and totals to books from that source.

### Requirement: Book search response preserves App book list semantics
The backend SHALL return search results with the same public book data semantics as App book lists.

#### Scenario: Results include public cover URLs
- **WHEN** search results include books with cover paths
- **THEN** the backend SHALL return those books with public cover URLs built from the current request base URL.

#### Scenario: Results are paged deterministically
- **WHEN** the search request includes `limit` and `offset`
- **THEN** the backend SHALL apply those pagination values and return results ordered by title ascending and then id ascending.

#### Scenario: No matches are found
- **WHEN** no books match the normalized query and optional source filter
- **THEN** the backend SHALL return an empty `books` list, `total` equal to `0`, and `hasMore` equal to `false`.
