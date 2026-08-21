## 1. Backend Cover Validation

- [x] 1.1 Validate extracted legacy cover bytes before publishing them under `/covers/*`.
- [x] 1.2 Treat undecodable extracted cover bytes as extraction failure so generated-cover fallback can run.
- [x] 1.3 Preserve atomic replacement behavior and temporary-file cleanup.

## 2. Verification

- [x] 2.1 Add focused tests for invalid cover bytes preserving existing files.
- [x] 2.2 Run targeted backend tests and OpenSpec validation.
