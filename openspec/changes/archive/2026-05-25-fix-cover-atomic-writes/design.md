## Context

Legacy EPUB cover extraction writes files under `covers/` and publishes them through the existing `/covers/*` static media route. During a full metadata rescan the extractor may replace an existing file while the frontend is already reading it, which can make Ktor's static response length validation fail.

The fix must keep existing cover URLs and route ownership intact because the React web migration and backend compatibility specs already depend on `/covers/*` remaining backend-owned.

## Goals / Non-Goals

**Goals:**
- Publish replacement legacy cover files without exposing partially written bytes through `/covers/*`.
- Preserve current `/covers/book_<id>.<ext>` URL shape and extension behavior.
- Add focused regression coverage for the cover replacement write path.

**Non-Goals:**
- Do not migrate legacy covers to `/book_images`.
- Do not change static route registration, API response fields, or database schema.
- Do not add a new image processing dependency.

## Decisions

- Write extracted cover bytes to a temporary file in the same `covers/` directory, then atomically move it over the target when supported by the filesystem. Same-directory replacement avoids cross-device move failures and keeps permissions and deployment assumptions simple.
- Fall back to a same-directory replace if atomic move is not supported. This fallback is less strong than atomic move, but keeps behavior available on filesystems that reject `ATOMIC_MOVE`; the normal Docker/local path should use the atomic branch.
- Keep file names unchanged (`book_<id>.<extension>`) instead of versioning cover URLs. Versioned URLs would avoid overwrite races but would require database updates and stale-file cleanup beyond the observed failure.

## Risks / Trade-offs

- [Risk] Some filesystems may reject atomic replacement. -> Mitigation: use a fallback replace and cover the primary helper behavior with tests.
- [Risk] Temporary files could remain if extraction fails mid-write. -> Mitigation: delete the temporary file on failure before returning `null`.
- [Risk] Direct helper tests could overfit implementation. -> Mitigation: assert externally meaningful behavior: replacement succeeds, target bytes are complete, and temporary files are cleaned up.
