## Context

See proposal.md for motivation. The existing service coordinates file parsing, image storage, document persistence, aggregate statistics, and cache state. EPUB is a ZIP container with XML package/navigation files, HTML chapters, and potentially hundreds of megabytes of images; TXT requires full decoded text for rule-based chapter ranges. Existing public APIs and database schema must remain compatible.

## Goals / Non-Goals

**Goals:**

- Make malicious or oversized input fail deterministically without external entity access or unbounded byte aggregation.
- Make successful and failed parse states match the data that is actually readable.
- Keep blocking file work outside database transactions and publish parsed rows atomically.
- Reduce repeated EPUB package parsing and image-memory residency.
- Verify behavior against focused synthetic files and the configured real ebook corpus.

**Non-Goals:**

- Add PDF, MOBI, AZW3, comic archive, DRM, or network book parsing.
- Change book or reader HTTP response shapes.
- Add database columns or external parser dependencies.
- Guarantee successful parsing beyond the documented safety limits.

## Decisions

### Secure XML allows compatible DOCTYPE declarations but no external access

Use one hardened XML boundary for EPUB package, metadata, and NCX parsing. External general entities, external parameter entities, external DTD loading, XInclude, and external schema access are disabled. A blanket DOCTYPE ban was rejected because real EPUB 2 NCX documents use standard declarations and lost their table of contents under that policy.

### Archive and text reads use explicit limits

Validate entry count and declared expansion size, then enforce actual per-read limits so unknown or dishonest ZIP metadata cannot bypass checks. The selected limits are 10,000 entries, 2 GiB declared expansion, 2 MiB XML, 8 MiB chapter, 20 MiB individual image, 2 GiB aggregate streamed images, and 128 MiB TXT. Unlimited reads were rejected because the parser operates on administrator-selected but still untrusted files.

### Parser contracts support batch chapters and streamed resources

Keep structure, chapter content, and resources as separate parser phases. EPUB opens the archive once for structure, once for the chapter batch, and once for incremental resources. Resource callbacks hold only the current image byte array. Retaining a map of every image was rejected because a verified 597 MiB EPUB contained 595.4 MiB of image bytes.

Each book receives an independent EPUB parser instance because inline footnote parsing carries chapter-local state. Sharing that state across concurrent books was rejected.

### Parse before publishing, then atomically replace

Parse every chapter and prepare resource files outside the database transaction. Serialize chapter JSON before entering the database boundary. In one transaction, replace document/resource rows, insert every content row, update aggregate statistics, and set completed state. Partial per-chapter persistence was rejected because later failures could expose missing content while reporting completion.

Content-addressed resource files can be prepared before the transaction. A failed database transaction may leave deduplicated files, but it does not expose resource records or corrupt the previous parsed version.

### TXT decoding is deterministic

Honor UTF-8/UTF-16 byte order marks, otherwise require valid UTF-8 before falling back to strict GB18030 decoding. Calculate line starts from original decoded CRLF, CR, or LF separators instead of assuming every newline consumes one character.

### Image dimensions use metadata probing

Read raster width and height through image-reader metadata without decoding the full pixel raster. Full image decoding was rejected because compressed images can create disproportionate allocation and CPU cost.

## Risks / Trade-offs

- [Risk] Legitimate books larger than a configured limit fail parsing. → Mitigation: return failed parse state, log the violated boundary, and change limits only with measured corpus evidence.
- [Risk] Resource files written before a failed database commit can be orphaned. → Mitigation: content-addressed names make retries idempotent; a future bounded cleanup job can remove unreferenced files.
- [Risk] Full TXT content and parsed chapter elements still require heap proportional to text/content size. → Mitigation: enforce the TXT size limit and report peak heap in the conditional corpus benchmark.
- [Risk] Aggregate image limits permit substantial disk writes. → Mitigation: process one bounded image at a time and retain the explicit total ceiling.
- [Risk] Real-corpus benchmarks depend on private local files. → Mitigation: keep them conditional and pair them with deterministic synthetic unit tests.

## Migration Plan

1. Deploy the parser, service, repository, and tests without a schema migration.
2. Existing completed books remain readable; reparsing replaces them through the new atomic path.
3. Run the ordinary backend suite and strict OpenSpec validation.
4. When the private corpus is available, run the conditional benchmark and compare parsed count, document/content parity, elapsed time, and heap peak.
5. Roll back by reverting the code commit; no data migration is required because table formats are unchanged.
