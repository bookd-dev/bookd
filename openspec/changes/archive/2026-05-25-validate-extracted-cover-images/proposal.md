## Why

Some EPUB files declare a cover image path whose bytes are not a valid image. Publishing those bytes through `/covers/*` preserves the path but leaves clients with a broken book cover even though the backend has an existing generated-cover fallback.

## What Changes

- Validate extracted legacy EPUB cover bytes before publishing them to `/covers/*`.
- Treat invalid or undecodable extracted cover bytes as extraction failure so metadata processing can use the existing generated text-cover fallback.
- Preserve existing public cover URL behavior for valid extracted covers.

## Capabilities

### New Capabilities

### Modified Capabilities
- `backend-react-web-hosting`: Static media handling now requires legacy extracted covers to be complete, decodable images before publication.

## Impact

- Affected backend code: EPUB cover extraction and legacy cover persistence.
- Affected behavior: invalid EPUB cover entries no longer produce broken `/covers/*` files; valid covers remain unchanged.
- No API, schema, route, client, or dependency changes.
