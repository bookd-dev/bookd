# backend-ebook-parsing-pipeline Specification

## Purpose

Define the backend contract for securely and reliably parsing EPUB and TXT books, including bounded untrusted-input handling, complete state transitions, atomic publication, large-book resource processing, text compatibility, and regression verification.

## Requirements

### Requirement: Untrusted ebook input is parsed within explicit safety bounds
The backend SHALL treat EPUB XML, archive entries, images, chapters, and TXT bytes as untrusted input and SHALL enforce finite parsing limits before or while reading them.

#### Scenario: EPUB XML references an external entity
- **WHEN** an EPUB package, metadata, or NCX XML document declares an external entity, external DTD, or external schema
- **THEN** the backend SHALL NOT resolve or read the external resource
- **AND** malicious XML SHALL NOT expose local or network data.

#### Scenario: EPUB 2 NCX uses a standard document type declaration
- **WHEN** an EPUB 2 NCX document contains a document type declaration without requiring external entity expansion
- **THEN** the backend SHALL parse the NCX table of contents without discarding compatible navigation entries.

#### Scenario: An ebook exceeds a configured parser limit
- **WHEN** archive entry count, declared expansion size, XML size, chapter size, image size, aggregate image size, or TXT size exceeds its parser limit
- **THEN** parsing SHALL fail explicitly
- **AND** the backend SHALL NOT mark the book as completely parsed.

### Requirement: Parsed content is published only as a complete version
The backend SHALL publish a new parsed version only after every declared chapter and required resource has been processed successfully.

#### Scenario: One chapter cannot be parsed
- **WHEN** any declared spine chapter is missing, unreadable, or fails content parsing
- **THEN** the parse result SHALL be failed
- **AND** no partial replacement SHALL be published
- **AND** parsed cache state SHALL NOT be written as successful.

#### Scenario: Parsed structure is invalid
- **WHEN** a parsed book has no chapters, duplicate chapter indexes, negative chapter indexes, or content indexes that do not match the declared structure
- **THEN** the backend SHALL fail parsing before publishing documents.

#### Scenario: All content and resources succeed
- **WHEN** all chapters and resources have been processed and persistence succeeds
- **THEN** documents, contents, resources, aggregate statistics, and completed parse state SHALL become visible together
- **AND** the document count and content count SHALL remain consistent.

#### Scenario: Persistence fails while replacing a parsed version
- **WHEN** any document, content, resource, statistics, or completed-state write fails
- **THEN** the replacement transaction SHALL roll back
- **AND** the previous parsed version SHALL remain intact.

### Requirement: Large EPUB resources are processed with bounded residency
The backend SHALL process EPUB chapter content and image resources without retaining all book image bytes in memory or reopening and reparsing the package for every chapter.

#### Scenario: EPUB contains many chapters
- **WHEN** an EPUB contains multiple spine documents
- **THEN** the backend SHALL reuse a bounded number of archive/package reads for the chapter batch
- **AND** each declared chapter SHALL still receive its own parsed content result.

#### Scenario: EPUB contains a large image collection
- **WHEN** an EPUB contains image resources whose aggregate size is much larger than an individual image
- **THEN** resources SHALL be consumed incrementally
- **AND** the complete image collection SHALL NOT be retained as one in-memory byte map.

#### Scenario: Image dimensions are collected
- **WHEN** the backend records dimensions for a supported raster image
- **THEN** it SHALL obtain dimensions without requiring full pixel-raster decoding
- **AND** unsupported dimension formats MAY retain null dimensions without failing otherwise valid content.

### Requirement: TXT parsing preserves common encodings and exact chapter ranges
The backend SHALL decode supported TXT encodings and SHALL preserve source-character chapter boundaries for common newline conventions.

#### Scenario: TXT contains a Unicode byte order mark
- **WHEN** a TXT file begins with a UTF-8, UTF-16 little-endian, or UTF-16 big-endian byte order mark
- **THEN** the backend SHALL decode the text using the declared Unicode encoding
- **AND** the byte order mark SHALL NOT appear in chapter content.

#### Scenario: Chinese TXT is encoded as GB18030 or GBK
- **WHEN** a TXT file is not valid UTF-8 and is valid GB18030-compatible text
- **THEN** the backend SHALL decode it without replacing Chinese content with mojibake.

#### Scenario: TXT uses CRLF or CR newlines
- **WHEN** chapter headings and content are separated by CRLF or CR newlines
- **THEN** detected chapter start and end offsets SHALL match the original decoded text
- **AND** adjacent chapter content SHALL NOT overlap or truncate.

### Requirement: Ebook parsing behavior has focused and real-corpus regression evidence
The backend SHALL maintain automated coverage for security boundaries, parse failure state, atomic rollback, TXT compatibility, and representative local EPUB/TXT parsing.

#### Scenario: Focused parser regression tests run
- **WHEN** the backend test suite is executed
- **THEN** tests SHALL cover external-entity rejection, bounded entry reads, partial chapter failure, atomic rollback, TXT encoding, and newline offsets.

#### Scenario: A local ebook corpus is configured
- **WHEN** an operator explicitly supplies a local EPUB/TXT corpus to the conditional regression benchmark
- **THEN** each selected book SHALL report parse success or failure
- **AND** the benchmark SHALL report parsed count, document/content counts, parse time, and peak heap usage
- **AND** absence of a private corpus SHALL NOT fail the ordinary CI test suite.
