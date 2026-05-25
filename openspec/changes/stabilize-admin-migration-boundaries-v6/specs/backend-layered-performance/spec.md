## ADDED Requirements

### Requirement: Admin image migration data access is repository-owned
Image-dimension migration database access SHALL be delegated to a repository while the service coordinates image probing and result counting.

#### Scenario: Resource dimension candidates are migrated
- **WHEN** resource image candidates are loaded or updated
- **THEN** the repository SHALL execute database work through `DatabaseExecutor.dbQuery`
- **AND** image file probing SHALL happen outside the database transaction.

#### Scenario: Cover dimension candidates are migrated
- **WHEN** book cover candidates are loaded or updated
- **THEN** the repository SHALL execute database work through `DatabaseExecutor.dbQuery`
- **AND** legacy `/covers/` and `/book_images/` path handling SHALL preserve existing behavior.

#### Scenario: Admin route executes migration
- **WHEN** an administrator calls an image-dimension migration endpoint
- **THEN** the route SHALL await the migration service result
- **AND** it SHALL preserve the current success response fields.
