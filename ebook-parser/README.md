# eBook Parser Service

A microservice for parsing eBook files (currently supporting EPUB via [ebooklib](https://github.com/aerkalov/ebooklib)).

## Features

- Extract metadata from EPUB files (title, author, publisher, description, ISBN, tags)
- Extract and save cover images with dimension information
- RESTful API using FastAPI
- Docker containerization
- Shared volume support for file access
- Extensible architecture for future format support (PDF, MOBI, etc.)

## Architecture

This service is part of the Bookd e-book management system:
- Receives requests from Bookd Kotlin backend via HTTP
- Accesses eBook files through shared Docker volumes
- Saves cover images to shared `/app/covers` volume
- Returns structured metadata compatible with Kotlin `BookMetadata` model

## API Endpoints

### Health Check
```
GET /health
Response: {"status": "healthy", "version": "0.1.0"}
```

### Extract Metadata
```
POST /api/parse/metadata
Request: {"file_path": "/path/to/book.epub", "book_id": 123}
Response: {
  "title": "Book Title",
  "author": "Author Name",
  "publisher": "Publisher",
  "description": "Book description...",
  "isbn": "1234567890",
  "tags": ["Fiction", "Adventure"]
}
```

### Extract Cover
```
POST /api/parse/cover
Request: {"file_path": "/path/to/book.epub", "book_id": 123}
Response: {
  "cover_path": "/covers/book_123.jpg",
  "width": 800,
  "height": 1200,
  "aspect_ratio": 0.667,
  "success": true,
  "error": null
}
```

## Development

### Prerequisites
- Python 3.11+
- Poetry

### Setup
```bash
cd epub-parser-service
poetry install
```

### Run Locally
```bash
poetry run python -m app.main
# or
poetry run uvicorn app.main:app --reload
```

### Run Tests
```bash
poetry run pytest
```

## Docker Deployment

The service is deployed as part of the Bookd `docker-compose.yml`:

```yaml
epub-parser:
  build: ./epub-parser-service
  ports:
    - "8001:8000"
  volumes:
    - covers_data:/app/covers
    - /Users:/Users:ro  # Shared book files
  environment:
    - LOG_LEVEL=info
```

## Configuration

Environment variables:
- `HOST`: Server host (default: 0.0.0.0)
- `PORT`: Server port (default: 8000)
- `LOG_LEVEL`: Logging level (default: INFO)
- `COVERS_DIR`: Directory for cover images (default: /app/covers)
- `BOOK_IMAGES_DIR`: Directory for book images (default: /app/book_images)

## Integration with Bookd Backend

The Kotlin backend uses `EpubParserClient` to call this service:
1. Checks service availability via `/health`
2. Calls `/api/parse/metadata` for metadata extraction
3. Falls back to local `EpubMetadataExtractor` if service fails
4. Calls `/api/parse/cover` for cover extraction

## License

Part of the Bookd project.
