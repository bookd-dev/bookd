## Context

See proposal.md for motivation. The existing backend scans only EPUB/TXT extensions and selects only EPUB/TXT `BookParser` implementations, but its persistence pipeline already accepts a format-neutral `BookStructure`, batches chapter content as `ContentElement` lists, streams resources, generates stable anchors, and atomically replaces parsed rows. Public clients consume only the manifest and chapter-content APIs; they do not parse source files.

The repository already includes Apache Tika and a transitive Apache PDFBox runtime, although the generic Tika metadata fallback parses body content unnecessarily and does not extract covers. No current JVM dependency parses MOBI content. Deployment uses a JDK 21 Alpine image with amd64/arm64 builds and a 4 GiB container limit, so a native converter must be reproducible in that environment and may not assume a developer-machine installation.

The supplied corpus establishes two relevant baselines: an unencrypted UTF-8 MOBI 6 book with PalmDOC compression, navigation markers, images, and EXTH metadata; and two text-layer PDFs exceeding 1,300 pages, including one outline-free document and one document with an encryption flag that remains readable without a password. These samples are local conditional evidence, not repository fixtures.

## Goals / Non-Goals

**Goals:**

- Normalize PDF and MOBI into the same server-side chapter and `ContentElement` contract as EPUB/TXT.
- Preserve the existing public reader APIs, persistence schema, reader rendering path, bookmarks, and progress model.
- Make PDF page/range identities and MOBI destinations deterministic across reparses.
- Isolate and bound format-specific memory, CPU, expansion, resource, and temporary-file behavior.
- Reuse the hardened EPUB HTML/resource pipeline after MOBI normalization rather than maintaining a second HTML renderer.

**Non-Goals:**

- OCR or successful parsing of image-only/scanned PDFs.
- Preservation of PDF fixed layout, typography, columns, vector graphics, forms, annotations, or inline page graphics.
- Password prompting, DRM removal, or bypassing extraction permissions.
- AZW/AZW3 extension discovery or a general ebook conversion service.
- Any PDF/MOBI-specific client renderer, endpoint shape, or database schema.

## Decisions

### Use one centralized format registry with extension and signature checks

Replace duplicated format sets with a backend format registry used by scanning, metadata selection, parser selection, and benchmarks. A candidate must have both a supported extension and a matching signature: `%PDF-` for PDF and a valid Palm database header containing the `BOOKMOBI` creator/type marker for MOBI. Extension-only dispatch was rejected because it sends arbitrary input into complex parsers and lets scan support drift from parser support.

The registry returns normalized `epub`, `txt`, `pdf`, or `mobi` identifiers. Existing book rows require no migration because the format column already accommodates these values.

### Parse PDF directly with PDFBox into textual content elements

Declare PDFBox as a direct backend dependency while retaining one compatible resolved version with Tika. Use format-specific metadata and content readers instead of `TikaMetadataExtractor`: metadata extraction reads the document information/catalog only, and cover extraction renders only the first page under a pixel cap.

The PDF parser opens a document once per batch using file-backed/random-access caching. It flattens valid outline destinations, removes invalid and duplicate page boundaries deterministically, and partitions the page sequence into non-overlapping chapter ranges. When no usable outline exists, it uses fixed 20-page ranges. Each chapter identity includes its inclusive page range, such as `pdf:pages:000001-000020`.

For each page, ordered extracted text is normalized into paragraphs. The chapter title becomes a heading, and page boundaries receive deterministic generated anchors so progress can be restored without exposing a new page object in the public model. Blank pages remain valid boundaries; the book fails only when no page yields usable text. Font-size heading inference was rejected because it is unstable across producers. PDF images and coordinates are not extracted in this change because the chosen product contract is reflowable text, not fixed-layout reconstruction.

Readable documents with an encryption dictionary are accepted only when they open without an unavailable password and the effective access permission allows content extraction. All other protected PDFs fail with a stable reason. OCR is never invoked.

### Normalize MOBI with a bounded libmobi adapter and reuse EPUB parsing

Package a pinned libmobi tool for Linux amd64/arm64 and invoke it behind a `MobiNormalizer` process adapter. The adapter validates the MOBI/PalmDOC header and rejects non-zero encryption before conversion, executes without network access in a unique temporary directory, and produces a normalized EPUB or equivalent EPUB directory that passes the ordinary EPUB signature and archive validation.

The normalized artifact is keyed by the source file SHA-256 and written atomically into an optional local cache. Metadata extraction and on-demand content parsing share that artifact when present; cache absence or eviction affects performance only. The existing EPUB metadata, HTML-to-`ContentElement`, internal-link, image-resource, and atomic publication behavior remains the source of truth. Native library/JNA embedding and a new Kotlin MOBI decoder were rejected because they increase crash coupling or duplicate complex MOBI6/KF8/index reconstruction logic. Calibre was rejected for the main Alpine image because of its substantially larger glibc/Qt runtime; it remains a possible future isolated worker, not an implementation fallback.

### Keep normalized parsing compatible with the existing atomic publication boundary

Format-specific work finishes before `replaceParsedBook`: PDF text/range extraction and MOBI normalization/EPUB parsing must produce a complete validated chapter map and bounded resource set. Existing document/content/resource/statistics replacement then publishes the result atomically. Conversion caches and content-addressed resource files may exist before publication, but no database row or parsed-cache success marker becomes visible until the replacement transaction commits.

Stable anchors use source-specific identities: PDF page number plus normalized element fingerprint and occurrence, and MOBI normalized document path/source id after deterministic conversion. Reparse tests verify identity stability.

### Apply explicit initial limits and stable failure categories

Extend the shared parser limits with conservative initial boundaries, adjusted only with recorded corpus evidence:

- PDF: 512 MiB source file, 10,000 pages, 2 MiB extracted text per page, 128 MiB extracted text per book, 20 megapixels for cover rendering, and a 120-second parse deadline.
- MOBI: 512 MiB source file, 100,000 Palm records, 2 GiB normalized expansion, the existing 20 MiB per-image and 2 GiB aggregate-image limits, and a 120-second normalization deadline.

The service records stable internal reasons for invalid signature, unsupported image-only PDF, protected PDF, protected MOBI, limit violation, normalization failure, and missing normalizer. Existing public error envelopes remain unchanged. Unlimited parsing and relying solely on the 4 GiB container limit were rejected because malformed files could starve unrelated requests.

### Keep private books conditional and add synthetic focused fixtures

Unit tests generate small PDFs for outline, no-outline, blank-page, protection, and bounds behavior. MOBI unit tests cover header/EXTH parsing and process-adapter behavior with deterministic small fixtures or fake normalizer output; any distributable MOBI fixture must have explicit compatible licensing. The supplied private corpus is referenced only through the existing conditional benchmark setting extended to PDF/MOBI, and is never copied into the repository.

Container verification builds the native dependency for both supported architectures and runs a small non-private conversion smoke test. Local service deployment continues to use `deploy.sh` as required by repository policy.

## Risks / Trade-offs

- [Risk] PDF text extraction can reorder columns, tables, formulas, or unusual glyph encodings. → Mitigation: define PDF support as reflowable text, use deterministic extraction, reject empty results, and retain source page anchors for diagnosis.
- [Risk] A 20-page fallback chapter can be large or semantically arbitrary. → Mitigation: cap per-page and total text, batch one open document, and keep the range size as one centralized tested constant.
- [Risk] Native libmobi packaging can diverge across amd64 and arm64 or be unavailable during local tests. → Mitigation: pin the source/version, build it in the container pipeline, inject a process adapter for unit tests, and expose a stable missing-normalizer failure.
- [Risk] Normalized MOBI output may differ after a converter upgrade and invalidate anchors. → Mitigation: pin the converter, include its compatibility version in the cache namespace, and require anchor-stability regression before upgrading.
- [Risk] Conversion or PDF parsing may consume CPU, heap, native memory, or temporary disk before a coroutine deadline can cancel it. → Mitigation: use a killable child process for MOBI, file-backed PDF access, explicit size/page/output limits, bounded concurrency, and cleanup in `finally`.
- [Risk] Cache artifacts can become orphaned after failed parses. → Mitigation: use content-addressed atomic writes and a bounded best-effort cleanup policy; correctness never depends on cache retention.

## Migration Plan

1. Add the format registry and parsers behind the existing on-demand parse workflow while leaving EPUB/TXT behavior unchanged.
2. Package the pinned MOBI normalizer in the multi-architecture Docker build and verify the non-private smoke fixture.
3. Run focused parser/service tests, the full backend suite, strict OpenSpec validation, and the conditional local PDF/MOBI corpus benchmark.
4. Deploy through the repository `deploy.sh` update path. Existing completed EPUB/TXT rows remain unchanged; newly discovered PDF/MOBI rows parse on demand.
5. Roll back the application image if required. PDF/MOBI rows may remain cataloged but will return unsupported parsing on the old version; no schema rollback is needed.
