## Overview

The backend remains the production web host. The web build is generated from `../bookd-web` and copied into backend generated resources so the jar can serve the React assets without committing build output.

## Static Hosting

- Keep `/api/*`, `/covers/*`, and `/book_images/*` handled by existing backend routes.
- Serve static assets from generated `static/web` resources.
- Return the React `index.html` for `/`, `/login`, `/setup`, `/reader`, `/admin`, and `/admin/{...}`.
- Do not keep legacy HTML pages on the active route path once React is wired.

## Build Integration

- Add Gradle tasks that run `npm install` only when needed and `npm run build` in `../bookd-web`.
- Copy `bookd-web/dist` into generated backend resources before `processResources`.
- Keep `bookd-web/dist` out of source control.

## Metadata Compatibility

The legacy admin edit UI includes a title input, but the existing backend route does not pass title through. The route will accept optional `title`, validate it when present, and forward it to `BookRepository.updateMetadata`.

## Testing

Backend tests cover `UpdateMetadataRequest` serialization/behavior and the web route fallback. Route tests should not require a running database.
