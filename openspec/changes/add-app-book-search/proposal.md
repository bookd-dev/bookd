## Why

The App backend only exposes authenticated per-source book paging, so the Compose client cannot search across a user's configured library from the existing search route. The backend needs a focused metadata search endpoint before the App search screen can be implemented correctly.

## What Changes

- Add `GET /api/app/books/search` for authenticated App clients.
- Search title, author, ISBN, and publisher with a non-blank query.
- Support optional `sourceId`, `limit`, and `offset` query parameters.
- Return paged `AppBooksResponse` data with public cover URLs and deterministic ordering.
- Add repository, service, route, and authorization regression coverage.

## Capabilities

### New Capabilities

- `backend-app-book-search`: Defines the authenticated backend App search API and result semantics.

### Modified Capabilities

None.

## Impact

- Affected routes: `routes/AppRoutes.kt`.
- Affected service/repository layers: `BookService`, `BookRepository`.
- Affected response contract: reuses existing `AppBooksResponse`.
- Affected tests: backend repository/service or route tests for matching, pagination, validation, and auth boundaries.
