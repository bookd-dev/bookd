## Why

The backend is structurally healthier after recent refactors, but several local quality issues remain. They are small enough to improve safely and concrete enough to justify a dedicated cleanup pass.

## What Changes

- Normalize shared route extension package naming.
- Add route parameter and query parsing helpers.
- Apply helpers to high-duplication routes while preserving each route's existing `ErrorCode`.
- Extract duplicated content image URL/dimension/aspect-ratio transformation logic.
- Extract response mapping helpers where they reduce repeated route code.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-layered-performance`: clarifies code-quality refactors that preserve layering and API compatibility.

## Impact

- Backend extension helpers, book/app/bookshelf route imports, selected route parsing code, and `BookContentService` transformation code.
- No schema, API, auth, pagination, sorting, or response field changes.
