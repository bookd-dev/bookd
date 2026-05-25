## Why

Full metadata rescans can rewrite legacy `/covers/book_<id>.*` files while clients are reading the same static file. Ktor validates static response length against the file length captured at response start, so an in-place overwrite can surface `BodyLengthIsTooSmall` or `BodyLengthIsTooLong` and return `400 Bad Request` for otherwise valid cover requests.

## What Changes

- Make legacy EPUB cover extraction publish replacement cover files atomically instead of overwriting the served file in place.
- Preserve existing `/covers/*` URLs, file names, extensions, and static media routing behavior.
- Add regression coverage for replacing an existing cover so future refactors keep the publish step atomic.

## Capabilities

### New Capabilities

### Modified Capabilities
- `backend-react-web-hosting`: Static media handling now requires stable cover reads while backend cover extraction replaces existing legacy cover files.

## Impact

- Affected backend code: EPUB metadata cover extraction and local cover file persistence.
- Affected routes: existing `/covers/*` static media route behavior is preserved, with fewer transient `400` responses during rescans.
- No API, database schema, client, dependency, or deployment contract changes.
