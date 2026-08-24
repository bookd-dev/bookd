## ADDED Requirements

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

## MODIFIED Requirements

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
