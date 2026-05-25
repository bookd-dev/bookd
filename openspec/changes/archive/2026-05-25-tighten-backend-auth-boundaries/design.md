## Context

The backend already has shared authentication helpers:

- `getAuthenticatedUser` / `getAuthenticatedUserId` for valid logged-in users.
- `requireAdminUser` for administrator-only operations.

The React admin client and Compose client already attach bearer tokens when available, so tightening server-side checks does not require changing request URLs or response shapes for authorized users.

## Decisions

- Classify management, operational, and library-mutating routes as administrator-only.
- Classify `/api/app/sources` and `/api/app/books` as authenticated-user routes, not administrator routes.
- Leave public catalogue/content reads, tag reads, health, web document, and static media routes anonymously accessible.
- Put authentication checks at the start of each protected route handler so unauthorized requests do not execute service side effects.
- Preserve existing helper error semantics: no token returns `AUTH_NO_TOKEN`; invalid or non-admin administrator checks return `AUTH_ADMIN_REQUIRED`.

## Non-Goals

- No role-based filtering of app source/book results in this change.
- No new authentication framework, JWT migration, database schema change, or frontend route redesign.
