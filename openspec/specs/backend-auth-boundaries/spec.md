# backend-auth-boundaries Specification

## Purpose
Define backend authentication and authorization boundaries for public reader APIs, authenticated app APIs, and administrator-only management APIs.

## Requirements
### Requirement: Administrator APIs require administrator authorization
The backend SHALL require an administrator bearer token before executing management, operational, or library-mutating API work.

#### Scenario: Administrator API is requested without authentication
- **WHEN** a request calls an administrator-only endpoint without a bearer token
- **THEN** the backend SHALL return the existing no-token authentication error
- **AND** it SHALL NOT execute the underlying service operation.

#### Scenario: Administrator API is requested by a non-admin user
- **WHEN** a request calls an administrator-only endpoint with a token for a non-admin user
- **THEN** the backend SHALL return the existing admin-required error
- **AND** it SHALL NOT execute the underlying service operation.

#### Scenario: Administrator API is requested by an admin user
- **WHEN** a request calls an administrator-only endpoint with a token for an admin user
- **THEN** the backend SHALL execute the existing service operation
- **AND** it SHALL preserve the existing successful response shape.

### Requirement: Management and operational routes are administrator-only
The backend SHALL classify management and operational APIs as administrator-only.

#### Scenario: Book source management is requested
- **WHEN** a request calls `/api/sources` or any nested `/api/sources/*` endpoint
- **THEN** the request SHALL require administrator authorization.

#### Scenario: Operational filesystem, scan, parser-rule, or background-parse API is requested
- **WHEN** a request calls `/api/filesystem/*`, `/api/scan/*`, `/api/txt-parse-rules/*`, or `/api/background-parse/*`
- **THEN** the request SHALL require administrator authorization.

#### Scenario: Library metadata or tag mutation is requested
- **WHEN** a request updates book metadata, uploads or generates a cover, queues a book reparse, or mutates tags/book-tag associations
- **THEN** the request SHALL require administrator authorization.

### Requirement: App library entrypoints require an authenticated user
The backend SHALL require a valid bearer token for app library entrypoints while preserving ordinary reader access for authenticated users.

#### Scenario: App sources are requested
- **WHEN** a request calls `GET /api/app/sources`
- **THEN** the request SHALL require a valid authenticated user.

#### Scenario: App books are requested
- **WHEN** a request calls `GET /api/app/books`
- **THEN** the request SHALL require a valid authenticated user.

### Requirement: Public and reader routes remain compatible
The backend SHALL keep public and existing user-scoped reader routes compatible unless they are explicitly classified as administrator-only or authenticated app entrypoints.

#### Scenario: Public catalogue or content route is requested
- **WHEN** a request calls public book list/detail/chapter, manifest, tag read, health, web document, or static media endpoints
- **THEN** the backend SHALL preserve anonymous access and the existing response shape.

#### Scenario: Existing user-scoped reader route is requested
- **WHEN** a request calls reading progress, bookmarks, reader settings, bookshelf, or book detail APIs that already require a user
- **THEN** the backend SHALL preserve the existing authenticated-user behavior and error semantics.

### Requirement: Routes use shared authentication helpers
Backend routes SHALL use shared authentication helpers for current-user and administrator checks instead of duplicating bearer-token parsing.

#### Scenario: Current user is required
- **WHEN** a route needs a valid logged-in user
- **THEN** it SHALL obtain the user through the shared current-user helper
- **AND** it SHALL preserve no-token and invalid-token error semantics.

#### Scenario: Administrator is required
- **WHEN** a route needs administrator access
- **THEN** it SHALL obtain the administrator through the shared admin helper
- **AND** it SHALL preserve no-token, invalid-token, and non-admin error semantics.
