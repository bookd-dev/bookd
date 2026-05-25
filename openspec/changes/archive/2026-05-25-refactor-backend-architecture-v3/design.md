## Context

The existing backend now has good coverage for book list statistics, chapter resources, bookshelf aggregation, parsing writes, scan duplicate detection, and token validation. The next highest-value work is operational consistency: task scopes are owned by multiple services, some route files still perform business workflows, tag creation/linking remains per item, and image migration holds database transactions while reading files.

## Goals / Non-Goals

**Goals:**

- Preserve all API paths, request/response shapes, error codes, authentication behavior, and media paths.
- Provide a shared, restart-safe internal task coordination boundary.
- Keep route files limited to request parsing, authentication, and response formatting for touched routes.
- Batch tag association work without changing tag semantics.
- Avoid long database transactions around image/file IO.
- Extend local benchmark evidence using `/Users/***/ebook`.

**Non-Goals:**

- Do not add public endpoints.
- Do not change token format, response DTOs, or parse output semantics.
- Do not rewrite all repositories in one pass.
- Do not introduce a new queue or persistence framework.

## Decisions

- `BookTaskCoordinator` owns content and metadata coroutine scopes and in-flight content parse tracking.
- Services keep existing delay semantics for async scan-triggered tasks to avoid changing user-visible timing.
- Route-level workflows are moved only where this change touches them.
- Tag batch methods use current unique constraints for idempotency and return the same observable results.
- Benchmark output remains opt-in via existing benchmark environment/system properties.

## Risks / Trade-offs

- [Risk] Shared task coordination can make shutdown ownership less obvious. -> Mitigation: register it in Koin and keep service shutdown methods delegating to it.
- [Risk] Service extraction can hide route-specific response behavior. -> Mitigation: result types preserve the same error branches and route tests cover representative behavior.
- [Risk] Tag batch insert behavior can differ under duplicate rows. -> Mitigation: keep idempotent conflict handling and tests for existing associations.

## Migration Plan

Additive index changes are applied by the current schema mechanism. No data migration is required beyond existing image-dimension retry operations.

## Open Questions

None.
