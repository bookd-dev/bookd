## 1. Content Anchors

- [x] 1.1 Add nullable/defaulted `anchorId` support to backend reader content models.
- [x] 1.2 Generate deterministic anchors in EPUB parsing, preferring source HTML ids when available.
- [x] 1.3 Generate deterministic anchors in TXT parsing using normalized text hashes and duplicate occurrence ordinals.
- [x] 1.4 Add tests for anchor stability, uniqueness, legacy content decoding, and chapter API serialization.

## 2. Progress And Bookmark Positions

- [x] 2.1 Extend reading progress DTOs, entities, repositories, and migrations with anchor-aware position fields while preserving existing index fields.
- [x] 2.2 Align bookmark DTOs/responses, entities, repositories, and migrations with chapter, anchor, fallback index, and note fields while preserving existing endpoint paths.
- [x] 2.3 Add compatibility tests for existing progress and bookmark rows without anchors.

## 3. Validation

- [x] 3.1 Run focused backend tests for reader content parsing, reading progress, and bookmarks.
- [x] 3.2 Run `cd bookd && openspec validate enable-reader-navigation-workflows --strict`.
