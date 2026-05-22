## ADDED Requirements

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
