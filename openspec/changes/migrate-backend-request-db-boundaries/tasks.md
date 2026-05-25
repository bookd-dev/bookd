## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `migrate-backend-request-db-boundaries`.
- [x] 1.2 Validate backend OpenSpec with `openspec validate --all --strict`.

## 2. Auth And User

- [ ] 2.1 Add suspend user repository methods for request paths.
- [ ] 2.2 Convert route-facing user service methods.
- [ ] 2.3 Verify token cache invalidation behavior.

## 3. Tags

- [ ] 3.1 Add suspend tag repository methods for request paths.
- [ ] 3.2 Convert route-facing tag service methods.
- [ ] 3.3 Verify create, merge, auto-tag, delete, and lookup compatibility.

## 4. Bookshelves

- [ ] 4.1 Add suspend bookshelf repository methods for request paths.
- [ ] 4.2 Convert route-facing bookshelf service methods.
- [ ] 4.3 Verify paging, default shelf, membership, add, and remove behavior.

## 5. Verification

- [ ] 5.1 Add or update tests for each migrated domain.
- [ ] 5.2 Run backend tests and OpenSpec strict validation.
