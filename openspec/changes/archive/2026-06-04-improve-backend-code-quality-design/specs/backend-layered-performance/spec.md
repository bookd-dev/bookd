## ADDED Requirements

### Requirement: Backend code-quality refactors preserve layering
Backend code-quality refactors SHALL reduce concrete duplication or complexity while preserving the established route-service-repository layering.

#### Scenario: Route helpers are introduced
- **WHEN** route parameter or query parsing is moved to shared helpers
- **THEN** each caller SHALL pass or preserve its existing `ErrorCode`
- **AND** successful response behavior SHALL remain unchanged.

#### Scenario: Extension package naming is normalized
- **WHEN** route extension helpers are moved into the normal backend extension package
- **THEN** all call sites SHALL import the normalized package
- **AND** public cover URL conversion SHALL preserve existing behavior.

#### Scenario: Content image transformation is extracted
- **WHEN** image transformation logic is shared between regular images and footnote images
- **THEN** URL construction, dimensions, aspect ratio, and missing-resource behavior SHALL remain compatible.

#### Scenario: Route response mapping is extracted
- **WHEN** response mapping helpers are introduced
- **THEN** routes SHALL continue to parse transport input and delegate business work to services
- **AND** helpers SHALL NOT introduce new business rules.
