## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `stabilize-backend-refactor-v5`.
- [x] 1.2 Validate the backend change with `openspec validate --strict`.

## 2. Lifecycle Cleanup

- [x] 2.1 Register Ktor shutdown cleanup for `BackgroundParseService`, `BookTaskCoordinator`, and optional `RedisService`.
- [x] 2.2 Make `BookTaskCoordinator.close()` and `BackgroundParseService.stop()` idempotent and test-covered.

## 3. Blocking Boundary And Warning Cleanup

- [x] 3.1 Move touched filesystem and scan route work to suspend IO boundaries while preserving response behavior.
- [x] 3.2 Move touched background parse status database lookup behind `DatabaseExecutor.dbQuery`.
- [x] 3.3 Fix safe compiler warnings for multipart part release, metadata logging safe calls, and TXT rule timestamp types.

## 4. Verification

- [x] 4.1 Add focused tests for lifecycle, restart, closed coordinator, filesystem compatibility, and background status behavior.
- [x] 4.2 Run backend tests with `./gradlew test --rerun-tasks`.
- [x] 4.3 Run root and backend OpenSpec strict validation.
