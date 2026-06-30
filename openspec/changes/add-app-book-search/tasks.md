## 1. Repository And Service

- [x] 1.1 Add `BookRepository` search result and count methods with query normalization and optional `sourceId`.
- [x] 1.2 Ensure repository search covers title, author, ISBN, and publisher with deterministic title/id ordering.
- [x] 1.3 Add `BookService` search result and count methods that enrich returned books with existing document statistics.

## 2. Route

- [x] 2.1 Add `GET /api/app/books/search` under `appRoutes`.
- [x] 2.2 Require authentication before running search.
- [x] 2.3 Validate missing or blank `q` as `BOOK_INVALID_PARAMS`.
- [x] 2.4 Apply `limit`, `offset`, and optional `sourceId`, then return `AppBooksResponse` with public cover URLs.

## 3. Backend Tests

- [x] 3.1 Add repository or service tests for title, author, ISBN, publisher, and source-filter matching.
- [x] 3.2 Add tests for pagination and count consistency.
- [x] 3.3 Add route tests for unauthenticated rejection and blank-query validation.
- [x] 3.4 Run targeted backend tests for the search API.

## 4. Verification

- [x] 4.1 Run `openspec validate add-app-book-search --strict` in the backend OpenSpec root.
- [x] 4.2 Run the selected Gradle backend test command after implementation.
