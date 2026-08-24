## Why

Bookd currently discovers and parses only EPUB and TXT files, so PDF and MOBI books in an otherwise valid library are invisible to the catalog and cannot use the existing reader workflow. The backend needs to normalize text-layer PDF and unencrypted MOBI content into the same `BookStructure` and `ContentElement` contract already consumed by existing clients, without introducing format-specific client rendering.

## What Changes

- Discover PDF and MOBI files during source scans and validate their content signatures before importing them.
- Extract PDF and MOBI metadata and covers with bounded, format-specific readers.
- Parse text-layer PDF outlines, pages, and text blocks into the existing chapter and textual `ContentElement` representation.
- Parse unencrypted MOBI metadata, navigation, HTML content, links, and image resources through a bounded normalization pipeline, then reuse the existing EPUB-to-`ContentElement` behavior where practical.
- Preserve the existing manifest, chapter-content, bookmark, and reading-progress APIs so current clients render PDF and MOBI content without native PDF/MOBI engines.
- Fail explicitly for scanned/image-only PDFs without a usable text layer, password-protected or extraction-prohibited PDFs, DRM/encrypted MOBI files, malformed inputs, and configured safety-limit violations.
- Extend conditional corpus and focused regression coverage to the supplied PDF and MOBI formats while keeping private ebook files outside the repository.
- Keep OCR, PDF inline page-graphics extraction, DRM circumvention, original fixed-layout PDF fidelity, AZW/AZW3 file discovery, and client-specific PDF/MOBI rendering out of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backend-ebook-parsing-pipeline`: Extend the secure atomic ebook parsing contract from EPUB/TXT to text-layer PDF and unencrypted MOBI while preserving the existing normalized content and API model.

## Impact

- Affected backend areas: source scanning, supported-format registration, file signature validation, metadata and cover extraction, parser factories, PDF/MOBI parsers or normalization adapters, stable content anchors, resource persistence, parse status reporting, and corpus benchmarks.
- Existing database columns and public reader response shapes remain compatible; no client rendering engine or schema migration is required for the normalized-content scope.
- PDF support uses the existing Apache PDFBox/Tika dependency family with explicit parser configuration rather than the generic full-body metadata fallback.
- MOBI support adds a bounded conversion/parsing dependency and requires reproducible Linux amd64/arm64 container packaging.
- Deployment validation must cover the repository's scripted Docker workflow and the existing Alpine-based runtime constraints.
