## 1. Baseline And Tests

- [ ] 1.1 Add focused tests that capture current book list statistics behavior, including empty-document books and cover aspect ratio calculation.
- [ ] 1.2 Add focused tests that capture current chapter content behavior, including image URL transformation, image dimensions, aspect ratio, previous chapter index, and next chapter index.
- [ ] 1.3 Add representative route or service tests for touched routes that currently access repositories directly.

## 2. Database Execution Boundary

- [ ] 2.1 Add a small database execution helper for Exposed transactions and blocking JDBC work.
- [ ] 2.2 Refactor the repository methods touched by this change to use the database execution helper.
- [ ] 2.3 Confirm file parsing, Redis calls, and image processing are not wrapped in long database transactions.

## 3. Batch Query Implementation

- [ ] 3.1 Add repository support for batch document statistics by book ID.
- [ ] 3.2 Refactor book list enrichment to use batch statistics while preserving returned chapter count, word count, image count, and cover aspect ratio.
- [ ] 3.3 Add repository support for loading document resources by book ID and resource paths in one call.
- [ ] 3.4 Refactor chapter content image transformation to use the batch resource map.
- [ ] 3.5 Add repository support for lightweight previous and next document indexes.
- [ ] 3.6 Refactor chapter content navigation to use the lightweight navigation query.

## 4. Layer Boundary Cleanup

- [ ] 4.1 Move touched `BookRoutes` repository access behind service methods.
- [ ] 4.2 Move touched `BookContentRoutes` behavior behind service methods where the route currently performs business orchestration.
- [ ] 4.3 Preserve all existing success and error response outcomes for the touched routes.

## 5. Index And Cache Safety

- [ ] 5.1 Add non-breaking Exposed indexes for book source filtering and title ordering.
- [ ] 5.2 Add non-breaking Exposed indexes for book document, table-of-contents, content, and resource lookup paths.
- [ ] 5.3 Add non-breaking Exposed indexes for user-scoped reading progress, bookmarks, reader settings, and bookshelf membership lookup paths.
- [ ] 5.4 Preserve Redis optional behavior and database fallback for parsed-state and content metadata paths.
- [ ] 5.5 Preserve `/api/*`, `/covers`, and `/book_images` routing while leaving legacy admin HTML document behavior to `migrate-admin-web-to-react`.

## 6. Verification

- [ ] 6.1 Run backend tests with `./gradlew test`.
- [ ] 6.2 Validate the backend OpenSpec change with `openspec validate refactor-backend-architecture-performance --strict`.
- [ ] 6.3 Validate the root OpenSpec change from the repository root with `openspec validate refactor-backend-architecture-performance --strict`.
- [ ] 6.4 Record relative performance evidence for optimized hot paths, such as reduced query count or representative execution cost.
