## Why

Backend EPUB and TXT parsing accepts untrusted local files, but the existing pipeline allowed external XML entities, unbounded archive reads, partial chapter publication, and high memory retention for image-heavy books. Real-library regression testing also exposed EPUB 2 NCX compatibility, large-book memory, TXT encoding, and CRLF offset gaps that need an explicit behavioral contract.

## What Changes

- Secure EPUB XML parsing while retaining compatible EPUB 2 NCX document type declarations.
- Bound archive, XML, chapter, image, and TXT reads and fail with an explicit parse failure instead of exhausting process memory.
- Parse EPUB chapters in batches and stream image resources instead of reopening the archive per chapter or retaining all image bytes.
- Publish documents, contents, resources, aggregate statistics, and completed parse state atomically only after every chapter and resource succeeds.
- Decode common TXT encodings and preserve exact chapter offsets across LF, CRLF, and CR input.
- Add focused security, failure-state, transaction rollback, encoding, and real-corpus regression coverage.

## Capabilities

### New Capabilities

- `backend-ebook-parsing-pipeline`: Defines secure bounded input handling, complete parse-state transitions, atomic publication, streaming resource behavior, TXT compatibility, and regression evidence for backend EPUB/TXT parsing.

### Modified Capabilities

None.

## Impact

- Affected backend areas: parser contracts, EPUB package/TOC/content/resource parsing, TXT decoding and chapter detection, image metadata probing, content orchestration, and parsed-content persistence.
- Public HTTP response shapes and supported formats remain unchanged; EPUB and TXT continue to be supported while other formats fail as unsupported.
- Database schema and external dependencies remain unchanged.
