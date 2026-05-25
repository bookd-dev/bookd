## Context

Existing specs already require routes to delegate business work, repositories to use a database execution boundary when touched, and background tasks to have coordinated lifecycle behavior. This pass applies those principles to the remaining low-risk areas discovered after the fourth backend refactor.

## Goals

- Stop and close long-lived resources when the application shuts down.
- Keep request-triggered filesystem and selected background status work off request coroutine execution.
- Keep public behavior and management API authentication exactly as-is.
- Cover the cleanup with focused tests.

## Non-Goals

- No management API authorization hardening in this change.
- No broad conversion of all repositories to suspend APIs.
- No database migration framework adoption.
- No decomposition of large services unless directly required by the touched behavior.

## Approach

- Add a small application lifecycle cleanup helper and call it from `Application.module()` after Koin is installed.
- Make `BackgroundParseService.stop()` and `BookTaskCoordinator.close()` safe to call repeatedly.
- Add suspend service methods for filesystem operations and selected repository wrappers using existing coroutine/database boundaries.
- Preserve existing synchronous methods for callers not touched by this change.
- Update tests for stop/start, closed coordinator behavior, and route/service response compatibility.

## Risks

- Cleanup must not throw during shutdown, so each resource close should be guarded and logged independently.
- Changing service methods to suspend requires route call-site updates and test updates.
- Exposed startup DDL warning remains known technical debt and is intentionally out of this change.
