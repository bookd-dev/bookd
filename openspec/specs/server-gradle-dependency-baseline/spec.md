## Purpose
Record the backend Gradle dependency baseline and compatibility changes required by upgraded server libraries.

## Requirements

### Requirement: Server Gradle dependency baseline
The server project SHALL use a verified Gradle dependency baseline compatible with JDK 21 and Ktor server runtime.

#### Scenario: Server dependency baseline is applied
- **WHEN** the server project is built after dependency updates
- **THEN** Gradle wrapper SHALL use Gradle 9.4.1
- **AND** Kotlin SHALL use 2.3.21
- **AND** Ktor SHALL use 3.5.0
- **AND** Exposed SHALL use 1.3.0 with `org.jetbrains.exposed.v1.*` imports
- **AND** backend tests SHALL pass with `./gradlew test`.

### Requirement: Server test fixtures match domain models
Server tests SHALL construct domain response models with all required fields introduced by the current production model definitions.

#### Scenario: Reading progress response is used in tests
- **WHEN** a test creates `ReadingProgressResponse`
- **THEN** it SHALL include chapter page and scroll progress fields required by the model.
