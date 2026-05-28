## Overview

This is a targeted backend stability cleanup. It keeps existing public behavior intact while reducing runtime retention risks that can appear as Docker memory growth after transient work.

## Design

- Token cache pruning remains local to `UserService`. A validation call prunes expired entries on a bounded cadence and then proceeds with the existing cache lookup/repository fallback flow.
- Background parse settings use a small environment-reader abstraction so tests can cover `PARSE_BACKGROUND_*` precedence and legacy fallback without mutating process environment.
- `DatabaseConfig` stores the active `HikariDataSource` after a successful `Database.connect` call and exposes idempotent close behavior for lifecycle cleanup.
- Redis module wiring checks the boolean `ping()` result. If health check fails after creation, it closes the Redis service and returns null so cache behavior remains optional.

## Compatibility

- Token cache TTL and logout/user-delete invalidation semantics stay intact.
- Existing `BACKGROUND_PARSE_*` variables still work if the documented `PARSE_BACKGROUND_*` names are absent.
- Shutdown cleanup remains idempotent and continues closing background parsing, task coordination, Redis, and now database resources.

## Validation

- Unit tests cover cache pruning, env precedence/fallback, database close callback through lifecycle cleanup, and Redis ping-failure cleanup.
- Backend tests run with the repository Gradle command.
- Backend OpenSpec change validates before archive and strict validation passes after archive.
