## Context

The backend extracts EPUB covers and publishes legacy covers through `/covers/book_<id>.<ext>`. A real corpus file exposed an EPUB `OEBPS/cover.jpg` entry containing `TEST2` rather than image bytes. The old flow wrote that file and stored it as the book cover path, producing a broken image in clients.

## Goals / Non-Goals

**Goals:**
- Prevent undecodable extracted cover bytes from being published as static cover media.
- Keep the existing generated-cover fallback path for books whose embedded cover is absent or invalid.
- Preserve valid EPUB cover extraction and legacy `/covers/*` routing.

**Non-Goals:**
- Do not add new image-processing dependencies.
- Do not change book metadata API response shape or cover URL conversion.
- Do not repair existing database rows automatically during deployment; re-extraction or regeneration can update affected books.

## Decisions

- Decode the temporary cover file with platform image decoding before the atomic publish step. This keeps invalid bytes out of `/covers/*` while reusing the existing temporary-file cleanup behavior.
- Fail the cover extraction path for invalid image data instead of saving a placeholder at the same path. This lets `BookMetadataService` use the existing text-cover generator and store a valid generated cover path.
- Keep validation in legacy cover persistence instead of checking only EPUB entry size or extension. Size and extension are unreliable; successful decoding is the externally meaningful condition.

## Risks / Trade-offs

- [Risk] Some uncommon valid image formats may not be supported by the runtime decoder. -> Mitigation: unsupported formats fall back to generated text covers instead of broken media.
- [Risk] Existing rows that already point to invalid `/covers/*` files remain stale until metadata is refreshed. -> Mitigation: full scan/re-extraction or generated-cover action updates the affected book.
