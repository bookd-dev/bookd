## MODIFIED Requirements

### Requirement: Routes delegate business work to services
Backend routes SHALL only parse request inputs, perform authentication checks, and return standardized responses; business work and repository access SHALL be delegated to domain services.

#### Scenario: Route currently accesses a repository
- **WHEN** a route is touched by the architecture performance refactor
- **THEN** direct repository access in that route SHALL be moved behind a service method that preserves the existing response behavior.

#### Scenario: Touched route owns multipart, import, or filesystem workflow
- **WHEN** cover upload, registration bookshelf initialization, TXT rule import, or filesystem browsing behavior is touched
- **THEN** the business workflow SHALL be moved behind a service method or small service result type.
- **AND** the route SHALL preserve the same HTTP status and `ErrorCode` behavior for representative success and failure cases.

#### Scenario: Service raises business errors
- **WHEN** service logic detects invalid input, missing resources, or forbidden access
- **THEN** it SHALL preserve existing `ErrorCode` semantics through the established response or exception handling path.

### Requirement: Blocking database work uses a consistent execution boundary
Repository operations using Exposed/JDBC SHALL run behind a consistent database execution boundary so blocking database work does not run directly on request coroutine execution.

#### Scenario: Repository method is added or refactored
- **WHEN** a repository method is introduced or substantially changed
- **THEN** it SHALL use the backend database execution boundary for Exposed transactions.

#### Scenario: Service coordinates multiple database reads
- **WHEN** a service coordinates multiple repository reads for one API response
- **THEN** the service SHALL avoid holding a long transaction across file parsing, network cache access, image processing, or other blocking non-database work.

#### Scenario: Image dimensions are migrated
- **WHEN** image dimension migration reads image files from storage
- **THEN** file IO SHALL happen outside long database transactions.
- **AND** database updates SHALL be written in short transactions.

### Requirement: Backend background tasks have a coordinated lifecycle
Backend asynchronous parsing and metadata tasks SHALL use a coordinated lifecycle boundary instead of each touched service owning unrelated coroutine state.

#### Scenario: Content parsing is queued
- **WHEN** a book content parse is queued asynchronously
- **THEN** duplicate parse launches for the same book SHALL be skipped while one is already in flight.
- **AND** the task SHALL remove the in-flight marker after success or failure.

#### Scenario: Background parse service is restarted
- **WHEN** the background parse service is stopped and then started again
- **THEN** future scheduled parsing attempts SHALL still be able to launch.

### Requirement: Refactored backend behavior is covered by tests
Backend refactor tasks SHALL add or update tests for each modified business path.

#### Scenario: Route delegation changes
- **WHEN** a route is changed to delegate repository work through a service
- **THEN** route or service tests SHALL verify the same success and error response outcomes for representative requests.

#### Scenario: Task coordination changes
- **WHEN** parsing or metadata background task coordination changes
- **THEN** tests SHALL verify duplicate suppression, completion cleanup, and restart behavior where applicable.
