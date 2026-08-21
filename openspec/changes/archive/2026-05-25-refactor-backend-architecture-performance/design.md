## Context

The backend currently follows a broad `routes -> domain/service -> data/repository` convention, but several endpoints bypass it or perform expensive repeated queries. Examples found during planning include book list enrichment calling document lookup per book, chapter content image transformation querying resources per image, and routes that fetch repositories directly.

The admin web UI is being migrated away from the current static HTML implementation under the separate `migrate-admin-web-to-react` OpenSpec change. Backend performance work must protect API and media routes used by the new React admin app, but it must not preserve the old HTML document as the target UI behavior.

Most repositories use synchronous Exposed `transaction {}` calls. Because Ktor handlers are suspendable and request concurrency matters, future refactor work needs a consistent execution boundary for blocking database access without changing business behavior.

## Goals / Non-Goals

**Goals:**

- Preserve all current public API contracts and business semantics.
- Move touched route-level repository work behind services.
- Add repository methods for batch statistics, resource maps, and lightweight navigation.
- Add safe indexes for existing lookup paths.
- Keep Redis optional and database-backed behavior authoritative.
- Keep `/api/*`, `/covers`, and `/book_images` compatible for the React admin migration.
- Add focused tests for every modified business path.

**Non-Goals:**

- Do not rewrite the backend framework, replace Ktor, replace Exposed, or introduce a new persistence library.
- Do not change response DTOs, error codes, auth token format, static file paths, or parsing output semantics.
- Do not migrate the frontend or restore legacy static admin HTML behavior.

## Decisions

- Introduce a small database execution abstraction before broad repository rewrites.
  - Rationale: it gives all refactored repositories a single place for `transaction` and dispatcher policy.
  - Alternative considered: convert every repository directly to `newSuspendedTransaction`. That is larger, riskier, and harder to review in one pass.
- Optimize the first hot paths through batch methods, not cached response blobs.
  - Rationale: batched SQL preserves database truth and works when Redis is disabled.
  - Alternative considered: cache full API responses. That adds invalidation complexity and risks stale user-specific data.
- Keep data-model changes to additive indexes.
  - Rationale: indexes improve existing predicates without changing response behavior or requiring destructive migrations.
  - Alternative considered: denormalize statistics onto `books`. That can be faster but requires backfill and invalidation rules, so it is out of scope for this change.
- Refactor route boundaries only where files are touched by the performance work.
  - Rationale: this keeps implementation scoped while improving the most problematic areas.
  - Alternative considered: move every route to new service APIs immediately. That would be broader than required to reduce endpoint cost.
- Defer web document route behavior to the React admin migration.
  - Rationale: future admin pages will not use the current HTML implementation, so backend performance work should not optimize or preserve that UI shell.
  - Alternative considered: treat old HTML and React shell as equal compatibility targets. That would make route behavior ambiguous.

## Risks / Trade-offs

- [Risk] Exposed nested transactions can behave differently when wrapped. -> Mitigation: add the execution boundary first, update methods in focused groups, and run backend tests after each group.
- [Risk] Index additions differ between H2 and PostgreSQL. -> Mitigation: use Exposed table index definitions compatible with existing `SchemaUtils.createMissingTablesAndColumns`.
- [Risk] Batch statistics can diverge from old enrichment logic. -> Mitigation: tests compare returned statistics and cover empty-document cases.
- [Risk] Resource batch lookup misses nested content image paths. -> Mitigation: transform traversal must collect image paths from top-level images, paragraph spans when relevant, list blocks, quotes, and footnotes before querying.
- [Risk] Backend routing changes accidentally restore or depend on legacy admin HTML. -> Mitigation: route tests should assert API/media route preservation and leave document fallback behavior to `migrate-admin-web-to-react`.

## Migration Plan

1. Add tests and representative fixtures for current behavior.
2. Introduce the database execution boundary and update the first repository group.
3. Implement batch statistics and chapter resource/navigation queries.
4. Move touched route-level repository access to services.
5. Add indexes to existing Exposed table definitions.
6. Run `./gradlew test` and both OpenSpec validations.

Rollback is file-level: because this change is additive and API-compatible, revert the refactored service/repository/index changes if tests or runtime verification show incompatible behavior.

## Open Questions

None.
