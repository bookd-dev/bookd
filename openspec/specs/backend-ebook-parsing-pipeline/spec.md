# backend-ebook-parsing-pipeline Specification

## Purpose

Define the backend contract for securely and reliably parsing EPUB and TXT books, including bounded untrusted-input handling, complete state transitions, atomic publication, large-book resource processing, text compatibility, and regression verification.
## Requirements
### Requirement: Untrusted ebook input is parsed within explicit safety bounds
The backend SHALL treat EPUB XML and archive entries, TXT bytes, PDF objects and pages, MOBI records and normalized output, images, chapters, and external parser processes as untrusted input and SHALL enforce finite parsing limits before or while reading them.

#### Scenario: EPUB XML references an external entity
- **WHEN** an EPUB package, metadata, or NCX XML document declares an external entity, external DTD, or external schema
- **THEN** the backend SHALL NOT resolve or read the external resource
- **AND** malicious XML SHALL NOT expose local or network data.

#### Scenario: EPUB 2 NCX uses a standard document type declaration
- **WHEN** an EPUB 2 NCX document contains a document type declaration without requiring external entity expansion
- **THEN** the backend SHALL parse the NCX table of contents without discarding compatible navigation entries.

#### Scenario: An ebook exceeds a configured parser limit
- **WHEN** archive entry count, declared expansion size, XML size, chapter size, image size, aggregate image size, TXT size, PDF file size, page count, extracted page or document text, MOBI record count, normalized output size, or parser deadline exceeds its limit
- **THEN** parsing SHALL fail explicitly
- **AND** the backend SHALL NOT mark the book as completely parsed.

#### Scenario: An external normalization process fails or times out
- **WHEN** a required format-normalization process exits unsuccessfully, exceeds its deadline, or produces an invalid or oversized result
- **THEN** parsing SHALL fail explicitly
- **AND** temporary output SHALL be cleaned up
- **AND** no partial replacement SHALL be published.

#### Scenario: Ebook content references an external resource
- **WHEN** normalized PDF or MOBI content contains a reference to a local path or remote network resource outside the source book
- **THEN** the backend SHALL NOT fetch or expose the referenced resource.

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
The backend SHALL maintain automated coverage for security boundaries, parse failure state, atomic rollback, TXT compatibility, PDF/MOBI normalization, and representative local EPUB/TXT/PDF/MOBI parsing.

#### Scenario: Focused parser regression tests run
- **WHEN** the backend test suite is executed
- **THEN** tests SHALL cover external-entity rejection, bounded entry reads, partial chapter failure, atomic rollback, TXT encoding, newline offsets, PDF outline and fallback partitioning, PDF protection and missing-text handling, MOBI metadata and navigation, MOBI protection rejection, and normalization limit failures.

#### Scenario: A local ebook corpus is configured
- **WHEN** an operator explicitly supplies a local EPUB/TXT/PDF/MOBI corpus to the conditional regression benchmark
- **THEN** each selected book SHALL report parse success or an expected unsupported/failure reason
- **AND** the benchmark SHALL report parsed count, document/content counts, parse time, and peak heap usage
- **AND** absence of a private corpus SHALL NOT fail the ordinary CI test suite.

#### Scenario: Private regression books are used
- **WHEN** operator-provided PDF or MOBI samples are used for local regression
- **THEN** the test harness SHALL reference them only through explicit local configuration
- **AND** it SHALL NOT copy those ebook files into repository fixtures or build artifacts.

### Requirement: Supported PDF and MOBI files are discovered and normalized through existing reader contracts
The backend SHALL discover valid `.pdf` and `.mobi` files, SHALL confirm that their content signature matches the claimed format, and SHALL expose successfully parsed content through the existing manifest and chapter-content response shapes.

#### Scenario: A valid PDF or MOBI is scanned
- **WHEN** a book source contains a regular file with a supported PDF or MOBI extension and matching content signature
- **THEN** the backend SHALL import the file with its normalized `pdf` or `mobi` format
- **AND** metadata extraction and on-demand content parsing SHALL be available through the existing book workflows.

#### Scenario: A file uses a supported extension with the wrong signature
- **WHEN** a file is named with a PDF or MOBI extension but its content signature does not match that format
- **THEN** the backend SHALL NOT treat it as a valid supported ebook
- **AND** it SHALL NOT invoke an incompatible parser on the file.

#### Scenario: A client reads normalized PDF or MOBI content
- **WHEN** PDF or MOBI parsing completes successfully
- **THEN** the backend SHALL return the existing book manifest, document list, table of contents, and chapter `ContentElement` representation
- **AND** existing clients SHALL NOT require a PDF or MOBI-specific rendering contract.

### Requirement: Text-layer PDF content is converted into deterministic reflowable chapters
The backend SHALL extract readable PDF text into deterministic chapters and textual `ContentElement` values while preserving source-page identity for stable navigation and progress.

#### Scenario: PDF contains a usable outline
- **WHEN** a readable PDF contains outline entries that resolve to valid pages
- **THEN** the backend SHALL use the resolved outline order and hierarchy to create deterministic chapter ranges
- **AND** it SHALL convert text from each range into the existing textual content elements.

#### Scenario: PDF contains text but no usable outline
- **WHEN** a readable PDF has a usable text layer but no outline entries that resolve to pages
- **THEN** the backend SHALL partition pages into deterministic bounded ranges
- **AND** it SHALL create synthetic chapter titles without exposing every page as a visible table-of-contents item.

#### Scenario: PDF contains blank pages among readable pages
- **WHEN** some PDF pages contain no extractable text but the document contains usable text elsewhere
- **THEN** the backend SHALL preserve deterministic page boundaries and continue parsing the readable content
- **AND** blank pages alone SHALL NOT cause an otherwise readable PDF to fail.

#### Scenario: PDF has no usable text layer
- **WHEN** no page in a PDF produces usable text after bounded extraction
- **THEN** parsing SHALL fail explicitly as unsupported image-only PDF content
- **AND** the backend SHALL NOT perform OCR or publish an empty completed version.

#### Scenario: PDF is protected
- **WHEN** a PDF requires an unavailable password or its effective permissions prohibit content extraction
- **THEN** parsing SHALL fail explicitly as protected PDF content
- **AND** the backend SHALL NOT attempt to bypass the protection.

#### Scenario: A client requests chapters from a protected PDF
- **WHEN** on-demand parsing identifies a PDF as password-required or extraction-prohibited
- **THEN** the chapter-list endpoint SHALL return the existing error envelope with a dedicated protected-PDF error code and HTTP 422
- **AND** it SHALL NOT report the expected content restriction as a generic HTTP 500 failure.

#### Scenario: PDF content is reparsed
- **WHEN** the same unchanged PDF is parsed again
- **THEN** chapter identities, page-derived anchors, ordering, and synthetic fallback ranges SHALL remain stable.

### Requirement: Unencrypted MOBI content is converted into deterministic reflowable chapters
The backend SHALL extract metadata, navigation, HTML text, internal links, and supported image resources from unencrypted MOBI books and SHALL normalize them into the existing reader content representation.

#### Scenario: MOBI contains navigation and HTML content
- **WHEN** an unencrypted MOBI contains readable navigation and HTML records
- **THEN** the backend SHALL preserve navigation order and hierarchy as deterministic chapters
- **AND** it SHALL convert supported HTML content and styles into the existing `ContentElement` and `TextSpan` values.

#### Scenario: MOBI contains no usable navigation
- **WHEN** an unencrypted MOBI has readable content but no usable navigation structure
- **THEN** the backend SHALL create deterministic fallback chapters from available structural markers
- **AND** it SHALL fall back to one bounded chapter only when no stronger source boundary exists.

#### Scenario: MOBI contains images and internal links
- **WHEN** an unencrypted MOBI references supported embedded images or internal destinations
- **THEN** the backend SHALL persist image resources through the existing book-resource workflow
- **AND** internal links and content anchors SHALL resolve deterministically within the normalized book.

#### Scenario: MOBI metadata is available
- **WHEN** an unencrypted MOBI contains title, author, publisher, description, ISBN, subject, or cover metadata
- **THEN** the backend SHALL map available values into the existing book metadata and cover fields
- **AND** missing optional metadata SHALL NOT prevent content parsing.

#### Scenario: MOBI is encrypted or DRM protected
- **WHEN** a MOBI declares a non-zero encryption mode or cannot be read without protected credentials
- **THEN** parsing SHALL fail explicitly as protected MOBI content
- **AND** the backend SHALL NOT attempt DRM circumvention.

#### Scenario: MOBI content is reparsed
- **WHEN** the same unchanged MOBI is parsed again
- **THEN** chapter identities, normalized internal destinations, content anchors, and resource paths SHALL remain stable.
