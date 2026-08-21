## Context

The current codebase has clear candidates for code-quality cleanup:

- `RouteExtension.kt` package name is abnormal.
- Route integer parsing and error response patterns are repeated across route files.
- Book cover URL mapping is shared but imported through the abnormal package.
- Regular image and footnote image transformation in `BookContentService` duplicate URL and aspect ratio logic.

## Goals

- Improve elegance through concrete simplification, not cosmetic churn.
- Keep the route-service-repository responsibility split intact.
- Reduce repeated parsing and transformation logic.
- Preserve every existing business outcome.

## Non-Goals

- No broad file reshuffling.
- No format-only rewrite.
- No new design framework.
- No public contract changes.

## Approach

- Move route extension declarations into `com.bookd.extension`.
- Add `ApplicationCall` helpers for required integer parameters and typed query defaults.
- Apply helpers in high-duplication routes first.
- Extract image transformation into a small private helper or internal collaborator that returns URL, width, height, and aspect ratio.
- Extract response mapping helpers only when the same mapping is repeated or route readability clearly improves.

## Risks

- Each route currently maps invalid parameters to different `ErrorCode` values; helpers must accept the error code explicitly.
- Missing image resources must still leave original content unchanged.
- Package normalization must update all imports in one commit.
