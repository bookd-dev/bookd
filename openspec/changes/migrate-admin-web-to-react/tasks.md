## 1. Backend Web Hosting

- [x] 1.1 Add Gradle tasks to build `../bookd-web` and copy `dist` into generated resources.
- [x] 1.2 Update Ktor web routes to serve React `index.html` for document routes.
- [x] 1.3 Preserve `/api`, `/covers`, and `/book_images` routing behavior.

## 2. Metadata API Compatibility

- [x] 2.1 Add optional `title` to `UpdateMetadataRequest`.
- [x] 2.2 Validate non-null title values and forward title to `BookRepository.updateMetadata`.
- [x] 2.3 Preserve existing metadata fields and response shape.

## 3. Tests

- [x] 3.1 Add backend test coverage for metadata title update behavior.
- [x] 3.2 Add backend test coverage for React web route fallback.
- [x] 3.3 Run `./gradlew test`.
