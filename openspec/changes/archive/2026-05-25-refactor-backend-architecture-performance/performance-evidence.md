# Performance Evidence

## Book List Statistics

Before this refactor, `BookService.getAllBooks`, `getBooksBySourceId`, and `getBooksBySourceIdPaged` enriched each book by calling `BookDocumentRepository.findByBookId(book.id)`, so document statistics required one document query per returned book.

After this refactor, these methods call `BookDocumentRepository.findStatsByBookIds(bookIds)` once for the returned page and enrich each book from the aggregated result. `BookServiceTest` verifies the list path calls the batch statistics method once for two books while preserving chapter count, word count, image count, and cover aspect ratio.

## Chapter Content Resources

Before this refactor, `BookContentService.getChapterContent` called `BookDocumentRepository.findResource(bookId, path)` for each image and footnote image encountered while transforming content.

After this refactor, the service collects image paths from the chapter content and calls `findResourcesByBookIdAndPaths(bookId, paths)` once. `BookContentServiceTest` verifies the batch resource method is called once and the old per-resource lookup is not called while image URL, dimensions, aspect ratio, and missing-resource behavior are preserved.

## Chapter Navigation

Before this refactor, chapter navigation loaded all book documents and computed previous/next indexes in memory.

After this refactor, `BookDocumentRepository.findAdjacentIndexes(bookId, index)` loads only document indexes for the book. `BookContentServiceTest` verifies the lightweight navigation query is used and returned `prevIndex`/`nextIndex` values are preserved.

## Real Library Benchmark

Measured on May 25, 2026 using `/Users/***/ebook` with a temporary H2 in-memory database and temporary image directory. The benchmark imported 262 EPUB/TXT files, parsed content for the first 60 books, and created 3,448 document rows plus 995 resource rows. Each scenario used 5 warmups and 30 measured repeats.

| Scenario | Legacy median | Current median | Improvement | Query model |
|---|---:|---:|---:|---|
| Book list, limit=100 | 4.082 ms | 1.426 ms | 65.1% | legacy 102 queries, current 3 queries |
| Tag-style books, limit=50 | 5.484 ms | 1.867 ms | 66.0% | legacy 100 queries, current 2 queries |
| Chapter content, 24 images | 0.852 ms | 0.566 ms | 33.6% | legacy 27 queries, current 4 queries |

These timings are local H2 measurements, so they understate expected gains for PostgreSQL over a real connection where per-query round trips are more expensive. The query-count reduction is independent of the database runtime.
