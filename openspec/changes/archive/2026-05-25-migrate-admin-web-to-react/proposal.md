## Why

The backend currently serves static HTML pages directly from resources. To host the new React web shell, Ktor needs SPA-compatible document routing and the metadata API must preserve the title editing behavior exposed by the legacy admin page.

## What Changes

- Serve the React application document for web routes while preserving backend API and media routes.
- Build and package `bookd-web` static assets with the backend jar.
- Allow `PUT /api/books/{id}/metadata` to update book title when provided.
- Add backend tests for the route fallback and title metadata update.

## Capabilities

### New Capabilities

- `backend-react-web-hosting`: Defines backend requirements for hosting the React web app and preserving metadata compatibility.

### Modified Capabilities

None.

## Impact

- Affects `bookd` Gradle build, Ktor web routes, book metadata route request model, and backend tests.
- Does not change existing API response envelopes or auth token format.
