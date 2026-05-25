## MODIFIED Requirements

### Requirement: Parse, scan, and auth hot paths reduce repeated work
The backend SHALL batch or cache repeated internal operations that do not change API behavior.

#### Scenario: A directory is scanned
- **WHEN** many supported book files are discovered
- **THEN** existing books SHALL be detected using batched file-path lookups rather than one lookup per file.

#### Scenario: Parsed documents and resources are persisted
- **WHEN** a parser produces multiple documents, contents, stats, or resources
- **THEN** database writes SHALL be batched in short transactions while file parsing and image processing remain outside database transactions.

#### Scenario: Authenticated requests validate a token
- **WHEN** the same valid token is checked repeatedly within a short interval
- **THEN** validation MAY use an in-process TTL cache.
- **AND** logout or user deletion SHALL invalidate affected cached credentials.

#### Scenario: Tags are linked to books in batches
- **WHEN** metadata extraction, auto-tagging, or tag merging links many tags or books
- **THEN** repository operations SHOULD batch tag lookup, creation, or book-tag association where equivalent behavior can be preserved.
- **AND** existing duplicate association semantics SHALL remain idempotent.

### Requirement: V2 hot-path behavior is covered by tests
Every modified v2 hot path SHALL have focused tests that verify preserved behavior and reduced repeated work.

#### Scenario: V2 implementation is completed
- **WHEN** the implementation is finished
- **THEN** backend tests SHALL pass and OpenSpec validations SHALL pass in strict mode.

#### Scenario: V3 benchmark evidence is recorded
- **WHEN** v3 backend refactor tasks are completed
- **THEN** local benchmark evidence SHOULD include before/after scenarios using `/Users/***/ebook` when the local corpus is configured.
- **AND** benchmark output SHALL include sample size and sanitized path information.
