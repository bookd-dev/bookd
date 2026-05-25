## 1. OpenSpec

- [x] 1.1 Create backend OpenSpec artifacts for `adopt-versioned-database-migrations`.
- [x] 1.2 Validate backend OpenSpec with `openspec validate --all --strict`.

## 2. Migration Mechanism

- [ ] 2.1 Select and add the migration tool or runner.
- [ ] 2.2 Add baseline migration resources for the current schema.
- [ ] 2.3 Replace production startup auto-DDL.
- [ ] 2.4 Keep test schema setup isolated from production startup.

## 3. Verification

- [ ] 3.1 Add startup migration tests for empty databases.
- [ ] 3.2 Add compatibility tests or documented validation for existing initialized databases.
- [ ] 3.3 Run backend tests and OpenSpec strict validation.
