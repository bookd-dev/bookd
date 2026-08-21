## 1. Format registration and dependencies

- [ ] 1.1 Add one centralized supported-format registry for EPUB, TXT, PDF, and MOBI extension, signature, metadata-extractor, and parser selection.
- [ ] 1.2 Implement bounded PDF and Palm/MOBI signature probes and unit tests for valid, truncated, malformed, and extension-spoofed files.
- [ ] 1.3 Update source scanning and conditional corpus selection to use the registry, with service tests proving valid PDF/MOBI import and invalid-signature rejection.
- [ ] 1.4 Declare and resolve one compatible PDFBox runtime version, record dependency insight, and keep the generic Tika full-body extractor out of the PDF path.
- [ ] 1.5 Pin the libmobi source/version and add reproducible Alpine amd64/arm64 build packaging without relying on a host-installed executable.

## 2. PDF metadata and normalized content

- [ ] 2.1 Implement bounded PDF metadata extraction for title, author, publisher/subject, description/keywords, and other existing fields, with filename fallback left to the current metadata service.
- [ ] 2.2 Implement first-page PDF cover rendering with configured pixel and output-size limits and tests for successful, blank, malformed, and oversized covers.
- [ ] 2.3 Implement deterministic PDF outline flattening, destination validation, duplicate-boundary handling, and non-overlapping chapter ranges.
- [ ] 2.4 Implement deterministic 20-page fallback chapter ranges for PDFs without a usable outline, including stable range identities and synthetic titles.
- [ ] 2.5 Implement one-open-document batch text extraction into heading, paragraph, and page-boundary content elements with stable page-derived anchors.
- [ ] 2.6 Enforce PDF file, page-count, per-page text, total-text, cover-pixel, and deadline limits with stable internal failure reasons.
- [ ] 2.7 Reject password-required and extraction-prohibited PDFs, accept permitted no-password encrypted PDFs, and fail image-only documents without invoking OCR.
- [ ] 2.8 Add PDF parser unit tests covering outline and fallback structures, blank pages, partial text, missing text layers, protection modes, malformed input, limits, and reparse anchor stability.

## 3. MOBI metadata and normalization

- [ ] 3.1 Implement bounded Palm database, PalmDOC, MOBI, and EXTH header parsing for signature validation, encoding, encryption mode, metadata, cover reference, and record bounds.
- [ ] 3.2 Implement a killable `MobiNormalizer` process adapter with unique temporary directories, sanitized arguments, no network dependency, deadline enforcement, output validation, and `finally` cleanup.
- [ ] 3.3 Implement atomic SHA-256 and converter-version keyed normalized-artifact caching so metadata and content parsing can share valid output without making correctness cache-dependent.
- [ ] 3.4 Integrate normalized MOBI output with the existing EPUB metadata, HTML content, navigation fallback, internal-link, image-resource, and stable-anchor behavior.
- [ ] 3.5 Enforce MOBI source size, record count, normalized expansion, image, aggregate resource, and deadline limits and reject every non-zero encryption mode without DRM circumvention.
- [ ] 3.6 Add MOBI unit tests for MOBI6/PalmDOC headers, EXTH metadata and cover mapping, malformed offsets, unsupported encoding/protection, missing normalizer, timeout, oversized output, and cache validity.
- [ ] 3.7 Add a licensed non-private MOBI normalization smoke fixture or deterministic fake-normalizer integration fixture and verify navigation, text, images, links, and reparse anchor stability.

## 4. Parsing orchestration and publication

- [ ] 4.1 Register PDF and MOBI metadata/content handlers in the existing services without changing public manifest, chapter-content, bookmark, or reading-progress response shapes.
- [ ] 4.2 Apply bounded PDF/MOBI parse concurrency and ensure all blocking file/process work remains outside database transactions.
- [ ] 4.3 Extend parser-service failure mapping and logs for invalid signature, image-only PDF, protected PDF, protected MOBI, missing normalizer, normalization failure, timeout, and limit violation without exposing local paths.
- [ ] 4.4 Add service/repository tests proving PDF/MOBI partial failures do not publish content, successful replacements remain atomic, rollback preserves the previous version, and parsed-cache success is written only after commit.
- [ ] 4.5 Verify no database migration, client format-specific renderer, or new reader endpoint is introduced for normalized PDF/MOBI content.

## 5. Regression and deployment verification

- [ ] 5.1 Extend the conditional local ebook benchmark to PDF/MOBI and report per-format success or expected failure, document/content parity, elapsed time, and peak heap without copying private files.
- [ ] 5.2 Run the conditional benchmark against `/Users/***/ebook/EBook`, including the unencrypted MOBI6 sample, outlined PDF, and outline-free permitted encrypted PDF, and record the evidence without repository-local absolute user paths.
- [ ] 5.3 Run focused PDF/MOBI parser, metadata, scan, orchestration, atomic rollback, and anchor-stability tests.
- [ ] 5.4 Run the full backend test suite and backend build, reporting test, build, container, and runtime evidence separately.
- [ ] 5.5 Build and smoke-test the native normalizer in both linux/amd64 and linux/arm64 images, then use `bookd/deploy.sh` option 2 for any authorized local container update.
- [ ] 5.6 Validate the root and backend OpenSpec trees in strict mode and review the final diff for API/schema/client-scope drift and unrelated worktree changes.
