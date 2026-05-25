# Performance Evidence

Measured evidence for this change includes a local benchmark against `/Users/shenchao/ebook`, query-model reasoning, unit tests, and `./gradlew test`.

## Local Ebook Benchmark

Command:

```bash
env GRADLE_USER_HOME=/private/tmp/codex-gradle-home \
  BOOKD_BENCHMARK_EBOOK_DIR=/Users/***/ebook \
  BOOKD_BENCHMARK_PARSE_LIMIT=60 \
  ./gradlew test --tests "com.bookd.performance.LocalEbookBenchmarkTest"
```

Corpus and setup:

- Path: `/Users/***/ebook`
- Files imported: 262 EPUB/TXT files
- Books created: 262
- Bookshelves created: 25
- Books parsed: 60
- Parsed documents/content rows: 1181 / 1181
- Rows backfilled with precomputed statistics: 202
- Import time: 159 ms
- Parse time: 24542 ms

Median hot-path results:

| Scenario | Optimized | Legacy simulated | Speedup |
| --- | ---: | ---: | ---: |
| Book list, limit 100 | 1.266 ms | 11.583 ms | 9.15x |
| Bookshelf page, limit 50 | 1.646 ms | 2.178 ms | 1.32x |
| Book detail | 2.327 ms | 2.272 ms | neutral in H2 |
| Scan duplicate detection, 262 paths | 2.409 ms | 21.053 ms | 8.74x |
| Token validation, cached x1000 | 0.091 ms | 60.296 ms | 662.59x |

Book detail query count is still reduced by the implementation, but this H2 in-memory benchmark does not show a latency win for that path.

## Book Statistics

Before v2, book list/detail enrichment always called `BookDocumentRepository.findStatsByBookIds(...)` after loading books. After v2, rows with `books.stats_updated_at` use internal `toc_chapter_count`, `total_word_count`, and `total_image_count` directly.

- Backfilled list page: removes the document aggregation query from the hot path.
- Backfilled detail page: removes the per-detail document aggregation query.
- Old rows still fall back to aggregation until startup backfill updates them.

`BookServiceTest` verifies the aggregation repository is not called for precomputed rows and is still used for fallback rows.

## Bookshelf Queries

Before v2, `getUserBookshelves` counted each shelf separately, and `getBookshelvesForBook` counted each returned shelf separately.

After v2:

- User shelf list loads counts with one grouped query.
- Book detail membership loads shelves, counts, and default membership through a single service call backed by batched repository methods.
- Shelf book paging is pushed to SQL with `bookshelf_items + books + reading_progress`, avoiding full shelf ID loading and full progress-map loading for every page.

`BookshelfServiceTest` verifies batch count usage, SQL-page delegation, pagination metadata, and membership summary behavior.

## Parse Persistence

Before v2, parsing persisted documents, content, stats, and resources through repeated repository calls. For a book with N chapters and R resources, this caused repeated short transactions during parsing.

After v2:

- Document replacement is one transaction.
- Parsed content and document stats are one transaction for successfully parsed chapters.
- Resource metadata persistence is one transaction after image files are stored.
- File parsing and image processing remain outside database transactions.

`BookDocumentRepositoryBatchTest` verifies batched document, content/stat, and resource persistence.

## Scan Duplicate Detection

Before v2, scan import checked `findByFilePath` per discovered file. After v2, scan loads existing books by file path in chunks via `findByFilePaths(...)`, reducing duplicate detection from N queries to `ceil(N / 500)` queries.

`BookScanServiceTest` verifies scan uses the batched file-path lookup and does not call per-file lookup for scanned files.

## Token Validation

Before v2, every authenticated request queried the sessions/users join. After v2, repeated validation of the same token within the short TTL is served from an in-process cache. Logout and user deletion invalidate cached credentials.

`UserServiceTest` verifies repeated validation hits the repository once and logout invalidates the cache.
