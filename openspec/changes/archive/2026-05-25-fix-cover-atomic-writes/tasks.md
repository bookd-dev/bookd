## 1. Backend Cover Persistence

- [x] 1.1 Add focused tests for legacy cover replacement and temporary-file cleanup.
- [x] 1.2 Update EPUB metadata cover persistence to write to a same-directory temporary file before publishing.
- [x] 1.3 Publish replacement covers with an atomic move when supported and a same-directory replace fallback.

## 2. Verification

- [x] 2.1 Run targeted backend tests for EPUB metadata cover extraction.
- [x] 2.2 Confirm existing `/covers/*` URL shape and route registration remain unchanged.
