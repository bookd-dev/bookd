## Why

Several backend APIs that perform management, operational, or library-mutating work were reachable without enforcing the existing bearer-token administrator boundary. This exposed filesystem browsing, scan control, parser configuration, background parsing control, source management, book metadata writes, cover writes, reparse, and tag mutation as unauthenticated or under-protected operations.

## What Changes

- Require administrator authorization for management, operational, and library-mutating backend routes.
- Require an authenticated user for app library entrypoints used by the client reader.
- Keep public catalogue/content reads and existing user-scoped reader APIs compatible.
- Reuse shared authentication helpers rather than adding route-local token parsing.

## Capabilities

### New Capabilities

- `backend-auth-boundaries`: defines backend public, authenticated-user, and administrator API boundaries.

### Modified Capabilities

None.

## Impact

- Affected backend routes: app, auth, book, book content, book source, filesystem, scan, tag, TXT parse rule, and background parse routes.
- Affected tests: route authorization and compatibility tests.
- No database schema, response envelope, successful response shape, or frontend API path changes.
