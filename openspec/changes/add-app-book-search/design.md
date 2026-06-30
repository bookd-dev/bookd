## Context

`GET /api/app/books` is authenticated and optimized for per-source paging, but it requires `sourceId` and cannot serve a global App search screen. The existing backend layering is `routes -> domain/service -> data/repository`, with App book responses using `AppBooksResponse` and public cover URL conversion in the route.

## Goals / Non-Goals

**Goals:**

- Add an authenticated metadata search endpoint under `/api/app`.
- Keep query parsing and validation in the route while delegating search logic to `BookService` and `BookRepository`.
- Search title, author, ISBN, and publisher with optional source filtering.
- Preserve App book list response shape and public cover URL behavior.
- Add regression coverage for matching, validation, paging, and authorization.

**Non-Goals:**

- Do not change existing `/api/app/books` behavior.
- Do not search parsed chapter content.
- Do not introduce a full-text index, external search service, or database schema migration.
- Do not add tag, format, bookshelf, or read-progress filters in this pass.

## Decisions

- Add `GET /api/app/books/search` as a dedicated route.
  - Rationale: it avoids conditional `sourceId` behavior on the existing `/api/app/books` route.
  - Alternative considered: allow `q` on `/api/app/books`. Rejected to keep existing contract stable.

- Use `AppBooksResponse`.
  - Rationale: search results are rendered like App book lists and need the same paging fields.
  - Alternative considered: introduce a search-specific response. Rejected until ranking/facets are needed.

- Add paired repository methods for result rows and counts.
  - Rationale: route responses need `total` and `hasMore`, and tests can validate count/result consistency.
  - Alternative considered: compute total from a full result list. Rejected because it would defeat pagination.

- Normalize the query by trimming and using ASCII case-insensitive matching.
  - Rationale: users should not need exact ASCII case, while CJK matching works through ordinary substring matching.
  - Alternative considered: database-specific full-text search. Rejected for portability and scope.

## Risks / Trade-offs

- [Risk] Metadata `LIKE` search can become expensive on very large libraries. Mitigation: apply `limit`/`offset`, keep matching fields bounded, and defer indexes until benchmark evidence justifies them.
- [Risk] Database collation behavior differs by backend. Mitigation: use normalized string expressions for predictable ASCII case-insensitive matching.
- [Risk] Search result ordering may not feel relevance-ranked. Mitigation: order deterministically by title and id for this first pass; richer ranking can be added later without changing the route shape.

## Migration Plan

No schema migration is required. The new route can be deployed alongside existing App routes and removed independently if rollback is needed.

## Open Questions

- Whether future search should include tags, descriptions, or parsed content.
- Whether large-library benchmarks should drive an index or search table after the first implementation.
