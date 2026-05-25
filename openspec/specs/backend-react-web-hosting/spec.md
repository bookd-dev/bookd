# backend-react-web-hosting Specification

## Purpose
Define backend requirements for serving the React web application, packaging its build output, and preserving metadata API compatibility required by the web migration.
## Requirements
### Requirement: Backend serves React web routes
The backend SHALL serve the React application document for known web document routes.

#### Scenario: React route is requested
- **WHEN** a request targets `/`, `/login`, `/setup`, `/reader`, `/admin`, or `/admin/*`
- **THEN** the backend SHALL return the React `index.html` with `text/html`.

#### Scenario: Backend API route is requested
- **WHEN** a request targets `/api/*`
- **THEN** the request SHALL be handled by API routes and not the React document fallback.

#### Scenario: Media route is requested
- **WHEN** a request targets `/covers/*` or `/book_images/*`
- **THEN** the request SHALL be handled by existing static media routing.

### Requirement: Backend packages web build output
The backend build SHALL package the `bookd-web` production build output into backend resources.

#### Scenario: Backend jar is built
- **WHEN** the backend resource processing task runs
- **THEN** the React app SHALL be built and copied into generated backend resources.

### Requirement: Book metadata title updates are supported
The book metadata API SHALL support updating a book title while preserving existing optional metadata updates.

#### Scenario: Metadata request includes title
- **WHEN** `PUT /api/books/{id}/metadata` receives a non-blank `title`
- **THEN** the backend SHALL persist the title and return the updated book.

#### Scenario: Metadata request includes blank title
- **WHEN** `PUT /api/books/{id}/metadata` receives a blank `title`
- **THEN** the backend SHALL reject the request with a bad-request book parameter error.

### Requirement: Legacy cover replacement is safe for static media reads
The backend SHALL replace existing legacy cover files served through `/covers/*` without exposing partially written or invalid file contents to static media responses.

#### Scenario: Existing legacy cover file is replaced
- **WHEN** EPUB metadata extraction replaces an existing `/covers/book_<id>.*` file
- **THEN** the served `/covers/*` path SHALL continue to reference a complete image file
- **AND** the URL path and static media route behavior SHALL remain unchanged.

#### Scenario: EPUB cover entry is invalid image data
- **WHEN** EPUB metadata extraction finds a cover entry whose bytes cannot be decoded as an image
- **THEN** the backend SHALL NOT publish those bytes as a legacy `/covers/*` file
- **AND** metadata extraction SHALL allow generated cover fallback behavior to provide a valid cover path.

#### Scenario: Cover replacement preparation fails
- **WHEN** EPUB metadata extraction cannot finish preparing a replacement cover file
- **THEN** the existing `/covers/*` cover file SHALL remain available unchanged
- **AND** temporary files SHALL NOT become public cover URLs.

