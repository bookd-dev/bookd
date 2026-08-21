## Context

The first pass reduced query count for book list statistics, chapter image resources, and chapter navigation. This change goes further while keeping API compatibility by moving repeated read-time work into maintained internal state and SQL-level aggregation.

## Goals / Non-Goals

**Goals:**

- Preserve all current API and business behavior.
- Add additive schema fields for book statistics.
- Batch or join remaining high-frequency repository paths.
- Keep file parsing and image processing outside long transactions.
- Add focused tests for every modified business path.

**Non-Goals:**

- Do not add, remove, or rename endpoints.
- Do not change response DTOs or serialized field names.
- Do not make Redis mandatory.
- Do not replace the existing database schema creation mechanism.

## Decisions

- `books` stores internal statistics, while `Book` keeps the same public fields.
- Startup backfill is idempotent and can be skipped safely if no rows need it.
- Bookshelf paging uses SQL ordering by reading progress first and bookshelf add time for unread books.
- Token cache is in-process and short-lived; it is an accelerator only.

## Risks / Trade-offs

- [Risk] Backfill can add startup cost on very large libraries. -> Mitigation: only update rows with missing stats timestamps and use aggregate queries.
- [Risk] SQL paging behavior can drift from old in-memory sorting. -> Mitigation: repository tests assert ordering and pagination.
- [Risk] Batched parser writes can partially complete if a chapter parse fails. -> Mitigation: preserve current per-chapter failure tolerance and only batch successfully parsed chapters.
