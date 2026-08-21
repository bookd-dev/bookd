## Context

`ChapterContent.elements` currently contains renderable `ContentElement` values without stable ids. Reading progress stores chapter/page/scroll information, and bookmarks use legacy position type/value fields. This makes restored positions sensitive to parser changes and reader layout changes.

## Goals / Non-Goals

**Goals:**
- Emit stable `anchorId` values for renderable chapter elements.
- Prefer source EPUB ids when available and generate deterministic anchors when they are not.
- Store anchor-aware progress and bookmark positions while keeping existing fallback fields readable.
- Avoid breaking old serialized content, old progress rows, and old bookmark rows.

**Non-Goals:**
- Implement a full EPUB CFI parser.
- Eagerly reparse every existing book.
- Add annotation/highlight storage.
- Remove legacy bookmark/progress fields in this change.

## Decisions

- Add nullable/defaulted `anchorId` fields to every renderable `ContentElement` subtype. Defaults are required so existing JSON stored in `document_contents` remains decodable.
- Generate anchors during parsing:
  - EPUB source id: `epub:<chapterDocumentIdentity>#<sourceId>`.
  - EPUB generated id: `epub:<chapterDocumentIdentity>:<elementType>:<contentHash>:<occurrence>`.
  - TXT generated id: `txt:<chapterIndex>:<elementType>:<contentHash>:<occurrence>`.
- Use normalized text for text-like elements, normalized resource path for images, and stable element type markers for non-text structural elements.
- Use a deterministic compact hash such as truncated SHA-256 hex; do not use random ids or database ids for element anchors.
- Add anchor-aware fields to progress and bookmark DTOs/responses. Existing endpoint paths remain unchanged.
- Preserve legacy progress/bookmark fallback fields and map old rows without anchors into responses the client can still use.

## Risks / Trade-offs

- Generated anchors can still change after large text edits. Fallback chapter/index/offset fields reduce data loss.
- Adding columns requires migrations and compatibility tests. New fields should be nullable.
- Existing cached parsed content will not gain anchors until reparsed. The client must handle missing anchors.
