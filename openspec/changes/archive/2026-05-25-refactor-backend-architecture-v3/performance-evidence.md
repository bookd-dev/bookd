# Performance Evidence

Local benchmark command:

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
- Resource dimension samples: 100
- Rows backfilled with precomputed statistics: 202
- Import time: 153 ms
- Parse time: 19567 ms

Median local benchmark results:

| Scenario | Current | Legacy simulated | Speedup |
| --- | ---: | ---: | ---: |
| Book list, limit 100 | 0.754 ms | 6.499 ms | 8.62x |
| Bookshelf page, limit 50 | 1.106 ms | 1.252 ms | 1.13x |
| Book detail | 1.310 ms | 1.439 ms | 1.10x |
| Scan duplicate detection, 262 paths | 1.544 ms | 12.621 ms | 8.17x |
| Token validation, cached x1000 | 0.047 ms | 34.091 ms | 725.34x |
| Tag link, x40 | 0.128 ms | 2.470 ms | 19.30x |
| Resource dimension IO, 100 samples | 1518.337 ms | 1558.165 ms | 1.03x |

Notes:

- The v3 tag-link benchmark compares the new batched tag lookup/link path against a per-tag lookup/link loop.
- The resource-dimension benchmark has similar median IO cost because image decoding dominates, but the current path keeps image reads outside the database transaction and avoids the legacy long-transaction behavior.
