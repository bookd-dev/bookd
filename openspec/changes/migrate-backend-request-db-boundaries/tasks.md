## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `migrate-backend-request-db-boundaries`.
- [x] 1.2 Validate backend OpenSpec with `openspec validate --all --strict`.

## 2. Auth And User

- [x] 2.1 Add suspend user repository methods for request paths.
- [x] 2.2 Convert route-facing user service methods.
- [x] 2.3 Verify token cache invalidation behavior.

## 3. Tags

- [x] 3.1 Add suspend tag repository methods for request paths.
- [x] 3.2 Convert route-facing tag service methods.
- [x] 3.3 Verify create, merge, auto-tag, delete, and lookup compatibility.

## 4. Bookshelves

- [x] 4.1 Add suspend bookshelf repository methods for request paths.
- [x] 4.2 Convert route-facing bookshelf service methods.
- [x] 4.3 Verify paging, default shelf, membership, add, and remove behavior.

## 5. Verification

- [x] 5.1 Add or update tests for each migrated domain.
- [x] 5.2 Run backend tests and OpenSpec strict validation.
