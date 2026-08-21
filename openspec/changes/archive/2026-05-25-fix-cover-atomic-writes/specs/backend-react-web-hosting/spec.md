## ADDED Requirements

### Requirement: Legacy cover replacement is safe for static media reads
The backend SHALL replace existing legacy cover files served through `/covers/*` without exposing partially written file contents to static media responses.

#### Scenario: Existing legacy cover file is replaced
- **WHEN** EPUB metadata extraction replaces an existing `/covers/book_<id>.*` file
- **THEN** the served `/covers/*` path SHALL continue to reference a complete image file
- **AND** the URL path and static media route behavior SHALL remain unchanged.

#### Scenario: Cover replacement preparation fails
- **WHEN** EPUB metadata extraction cannot finish preparing a replacement cover file
- **THEN** the existing `/covers/*` cover file SHALL remain available unchanged
- **AND** temporary files SHALL NOT become public cover URLs.
