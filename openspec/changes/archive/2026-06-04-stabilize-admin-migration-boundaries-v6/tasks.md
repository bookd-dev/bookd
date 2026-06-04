## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `stabilize-admin-migration-boundaries-v6`.
- [x] 1.2 Validate backend OpenSpec with `openspec validate --all --strict`.

## 2. Repository Boundary

- [x] 2.1 Add `ImageDimensionMigrationRepository`.
- [x] 2.2 Move resource and cover candidate queries behind `DatabaseExecutor.dbQuery`.
- [x] 2.3 Move resource and cover batch updates behind `DatabaseExecutor.dbQuery`.

## 3. Service And Route

- [x] 3.1 Convert migration service entry points to suspend functions.
- [x] 3.2 Keep image probing outside database transactions.
- [x] 3.3 Update admin routes and Koin registration.

## 4. Verification

- [x] 4.1 Add service tests for success, failure, exception, and empty-candidate behavior.
- [x] 4.2 Update admin route authorization tests.
- [x] 4.3 Run backend tests and OpenSpec strict validation.
