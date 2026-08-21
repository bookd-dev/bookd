## MODIFIED Requirements

### Requirement: Blocking database work uses a consistent execution boundary
Repository operations using Exposed/JDBC SHALL run behind a consistent database execution boundary so blocking database work does not run directly on request coroutine execution.

#### Scenario: Repository method is added or refactored
- **WHEN** a repository method is introduced or substantially changed
- **THEN** it SHALL use the backend database execution boundary for Exposed transactions.

#### Scenario: Service coordinates multiple database reads
- **WHEN** a service coordinates multiple repository reads for one API response
- **THEN** the service SHALL avoid holding a long transaction across file parsing, network cache access, or other blocking non-database work.

#### Scenario: Touched request work performs blocking operational access
- **WHEN** a route-touched service operation lists directories, validates filesystem paths, reads filesystem roots, scans book sources, or computes background status using blocking database work
- **THEN** the operation SHALL run behind a coroutine IO or database execution boundary.
- **AND** route responses SHALL preserve the same success and error semantics.

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

#### Scenario: Backend application stops
- **WHEN** the Ktor application stops
- **THEN** background parsing, shared book task coordination, and optional Redis resources SHALL be closed or stopped without leaking long-lived executors or connections.
- **AND** cleanup SHALL be safe to invoke more than once.

### Requirement: Refactored backend behavior is covered by tests
Backend refactor tasks SHALL add or update tests for each modified business path.

#### Scenario: Route delegation changes
- **WHEN** a route is changed to delegate repository work through a service
- **THEN** route or service tests SHALL verify the same success and error response outcomes for representative requests.

#### Scenario: Task coordination changes
- **WHEN** parsing or metadata background task coordination changes
- **THEN** tests SHALL verify duplicate suppression, completion cleanup, closed-state behavior, and restart behavior where applicable.

#### Scenario: Blocking boundary changes
- **WHEN** a filesystem or background status operation is moved to an IO-safe boundary
- **THEN** tests SHALL verify representative success and failure responses remain compatible.
