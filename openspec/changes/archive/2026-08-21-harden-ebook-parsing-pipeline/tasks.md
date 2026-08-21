## 1. Secure input boundaries

- [x] 1.1 Add shared secure EPUB XML parsing with external entity, external DTD, XInclude, and external schema access disabled.
- [x] 1.2 Add explicit archive entry, XML, chapter, image, aggregate image, and TXT read limits.
- [x] 1.3 Preserve compatible EPUB 2 NCX parsing when a standard document type declaration is present.

## 2. Parsing pipeline

- [x] 2.1 Parse EPUB chapters in a batch with a per-book parser instance instead of reopening the package per chapter.
- [x] 2.2 Stream image resources one at a time and probe dimensions from metadata without full raster decoding.
- [x] 2.3 Decode BOM-tagged Unicode and GB18030-compatible TXT files while preserving CRLF, CR, and LF chapter offsets.

## 3. Atomic publication

- [x] 3.1 Parse and validate every declared chapter and resource before publishing a completed version.
- [x] 3.2 Atomically replace documents, contents, resources, statistics, and completed parse state in one transaction.
- [x] 3.3 Serialize chapter content outside the database transaction and preserve the previous version on rollback.

## 4. Verification

- [x] 4.1 Add unit tests for XML security, bounded reads, TXT encodings, and newline offsets.
- [x] 4.2 Add service and repository tests for partial chapter failure, invalid structures, atomic replacement, and rollback.
- [x] 4.3 Run the focused parser tests, full backend test suite, and whitespace validation.
- [x] 4.4 Run the conditional real-corpus benchmark and record parse coverage, document/content parity, elapsed time, and peak heap.
- [x] 4.5 Validate the root and backend OpenSpec trees in strict mode.
