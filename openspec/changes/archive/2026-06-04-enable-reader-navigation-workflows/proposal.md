## Why

Reader progress and bookmarks currently depend on fragile chapter/page/paragraph indices because parsed chapter content is returned as a plain ordered `List<ContentElement>` without stable element anchors. Reparse, HTML structure changes, inserted images/footnotes, or reader relayout can move indices and restore the wrong position.

This backend change adds stable content anchors and anchor-aware position APIs so the client can restore progress and bookmarks more reliably.

## What Changes

- Add optional stable `anchorId` fields to renderable reader content elements.
- Generate deterministic anchors in EPUB and TXT parsers.
- Persist and return anchor-aware reading progress positions while preserving existing fields.
- Persist and return anchor-aware bookmark positions while preserving existing endpoint paths and legacy fallbacks.

## Capabilities

### New Capabilities

- `backend-reader-content-anchors`: stable content anchors and anchor-aware reader positions.

### Modified Capabilities

## Impact

- Affects content parsing, serialized chapter content, reader content API responses, reading progress DTOs/entities/repositories, bookmark DTOs/entities/repositories, migrations, and tests.
- Client compatibility requires nullable/defaulted fields and fallback behavior for existing progress/bookmark data.
