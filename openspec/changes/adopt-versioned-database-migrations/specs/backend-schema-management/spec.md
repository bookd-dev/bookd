## ADDED Requirements

### Requirement: Backend schema migration is versioned
The backend SHALL manage production database schema changes with explicit versioned migrations.

#### Scenario: Production startup initializes schema
- **WHEN** the backend starts in production or production-like configuration
- **THEN** it SHALL run versioned schema migrations after connecting to the database
- **AND** it SHALL NOT rely on `SchemaUtils.createMissingTablesAndColumns`.

#### Scenario: Migration fails
- **WHEN** schema migration fails during startup
- **THEN** the backend SHALL fail startup before routes and background services are started
- **AND** it SHALL not continue with a partially initialized application.

#### Scenario: Tests create transient schemas
- **WHEN** tests require an isolated database schema
- **THEN** test-only setup MAY create tables directly
- **AND** that behavior SHALL remain isolated from production startup.

#### Scenario: Existing database is baselined
- **WHEN** an existing Bookd database already has the current schema
- **THEN** the migration strategy SHALL provide a safe baseline path
- **AND** it SHALL preserve existing rows.
