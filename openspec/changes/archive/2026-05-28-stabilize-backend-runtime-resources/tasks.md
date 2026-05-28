## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `stabilize-backend-runtime-resources`.
- [x] 1.2 Validate the backend OpenSpec change before implementation completion.

## 2. Runtime Resource Hygiene

- [x] 2.1 Prune expired `UserService` token-cache entries during validation while preserving cache hits within TTL.
- [x] 2.2 Make `BackgroundParseService` honor `PARSE_BACKGROUND_*` variables with legacy fallback names.
- [x] 2.3 Close JDBC pool resources through backend lifecycle cleanup and close Redis when startup health check fails.

## 3. Tests

- [x] 3.1 Add focused unit tests for token cache pruning and background parse configuration.
- [x] 3.2 Add or update lifecycle and Redis initialization tests for resource close behavior.

## 4. Verification And Archive

- [x] 4.1 Run backend unit tests.
- [x] 4.2 Sync and archive the backend OpenSpec change.
- [x] 4.3 Run strict OpenSpec validation after archive.
