## ADDED Requirements

### Requirement: Backend emits stable reader content anchors
The backend SHALL provide a stable `anchorId` for each renderable `ContentElement` returned by chapter content APIs.

#### Scenario: EPUB element has a source id
- **WHEN** an EPUB paragraph, heading, image, list, quote, code block, divider, or footnote has a source HTML id or href fragment
- **THEN** the backend SHALL use a deterministic anchor derived from that source id and chapter document identity.

#### Scenario: EPUB element has no source id
- **WHEN** an EPUB element has no source HTML id
- **THEN** the backend SHALL generate a deterministic anchor from chapter href, element type, normalized text or resource path hash, and duplicate occurrence ordinal.

#### Scenario: TXT element is parsed
- **WHEN** a TXT chapter line is parsed into a renderable content element
- **THEN** the backend SHALL generate a deterministic anchor from chapter index, element type, normalized text hash, and duplicate occurrence ordinal.

#### Scenario: Existing cached content lacks anchors
- **WHEN** older serialized chapter content does not contain `anchorId`
- **THEN** the backend SHALL still decode and serve the content without failing
- **AND** newly parsed content SHALL include anchors.

### Requirement: Backend supports anchor-aware reader progress
The backend SHALL accept and return anchor-aware reader progress while preserving existing index-based progress fields.

#### Scenario: Client saves anchored progress
- **WHEN** the client saves reading progress with chapter index, anchor id, fallback index, and offset
- **THEN** the backend SHALL persist those fields
- **AND** it SHALL return them on subsequent progress reads.

#### Scenario: Existing progress has no anchor
- **WHEN** existing progress was saved before anchor fields existed
- **THEN** the backend SHALL return the legacy chapter/page/scroll fields
- **AND** it SHALL leave anchor fields empty so the client can use fallback mapping.

### Requirement: Backend supports anchor-aware bookmarks
The backend SHALL support bookmarks that identify a chapter and stable content anchor while keeping existing bookmark endpoint paths.

#### Scenario: Client creates an anchored bookmark
- **WHEN** the client creates a bookmark with chapter index, anchor id, fallback index, and optional note
- **THEN** the backend SHALL persist and return those fields
- **AND** the bookmark SHALL be listable through the existing book bookmarks endpoint.

#### Scenario: Existing bookmark has legacy position fields
- **WHEN** an existing bookmark was stored with legacy position type/value fields
- **THEN** the backend SHALL return enough fallback position data for the client to display and jump when possible.
