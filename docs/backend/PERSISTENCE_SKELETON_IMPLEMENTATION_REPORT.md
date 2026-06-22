# Persistence Skeleton Implementation Report V1

## 1. Read Documents

Governance:

- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`

Architecture and skill:

- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/reservation-system/SKILL.md`

Database and migration:

- `docs/database/DATABASE_DESIGN.md`
- `docs/database/ERD.md`
- `docs/database/SCHEMA_DESIGN.md`
- `docs/database/MIGRATION_REVIEW_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `docs/database/migrations/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

Backend design and skeleton:

- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/BACKEND_DESIGN_CHECKLIST.md`
- `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_REPORT.md`
- `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md`
- `docs/backend/ENTITY_MAPPING_DESIGN.md`
- `docs/backend/REPOSITORY_PORT_DESIGN.md`
- `docs/backend/PERSISTENCE_DESIGN_CHECKLIST.md`

## 2. Previous Round Confirmation

- Domain Object vs Entity: separated.
- Repository Port vs Implementation: separated.
- Mapper: conversion only.
- Scope: required for persistence access.
- Java Entity created: No.
- Repository created: No.
- Mapper created: No.
- Application Service created: No.
- Migration changed: No.
- Database touched: No.

## 3. Entity Skeletons Created

Tenant / Store:

- `TenantEntity`
- `StoreEntity`
- `StorePolicyEntity`
- `StoreAreaEntity`
- `DiningTableEntity`

Table management:

- `TableGroupEntity`
- `TableGroupMemberEntity`
- `TableLockEntity`

Customer:

- `CustomerEntity`

Reservation / Queue / WalkIn:

- `ReservationEntity`
- `ReservationPreassignmentEntity`
- `QueueGroupEntity`
- `QueueTicketEntity`
- `WalkInEntity`

Seating / Cleaning / Turnover:

- `SeatingEntity`
- `SeatingResourceEntity`
- `CleaningEntity`
- `TurnoverEntity`

Governance / Audit:

- `BusinessEventEntity`
- `StateTransitionLogEntity`
- `AuditLogEntity`
- `IdempotencyRecordEntity`
- `ReasonCodeEntity`
- `I18nMessageEntity`

Entity skeleton decisions:

- Entities use `@Entity`, `@Table`, `@Column`, `@Id`, and `@Version` where the migration has `version`.
- Database `uuid` maps to `java.util.UUID`.
- Database `timestamptz` maps to `java.time.OffsetDateTime`.
- Database `date` maps to `java.time.LocalDate`.
- JSONB fields use `String` placeholders with `columnDefinition = "jsonb"`.
- Status fields remain `String` codes.
- Entities use protected no-args constructors and simple getters.
- Entities do not contain state machine, assignment, workflow, repository, controller, or API logic.

## 4. Mapper Skeletons Created

Domain mappers:

- `TenantMapper`
- `StoreMapper`
- `StorePolicyMapper`
- `StoreAreaMapper`
- `DiningTableMapper`
- `TableGroupMapper`
- `TableLockMapper`
- `CustomerMapper`
- `ReservationMapper`
- `ReservationPreassignmentMapper`
- `QueueGroupMapper`
- `QueueTicketMapper`
- `WalkInMapper`
- `SeatingMapper`
- `SeatingResourceMapper`
- `CleaningMapper`
- `TurnoverMapper`
- `BusinessEventMapper`
- `StateTransitionLogMapper`
- `AuditLogMapper`
- `IdempotencyMapper`
- `ReasonCodeMapper`
- `I18nMessageMapper`

Mapper boundary placeholders:

- `SeatingSourceMapping`
- `SeatingResourceTargetMapping`
- `CleaningResourceTargetMapping`
- `TargetRef`
- `MetadataPayload`
- `SnapshotPayload`

Mapper skeleton decisions:

- Mapper skeletons expose `toDomain(entity)` and `toEntity(domain)` only.
- XOR and generic-target conversion boundaries are visible as mapper signatures.
- Mapper skeletons do not access databases or repositories.
- Mapper skeletons do not make business decisions.

## 5. Repository Port Interfaces Created

- `TenantRepositoryPort`
- `StoreRepositoryPort`
- `DiningTableRepositoryPort`
- `TableGroupRepositoryPort`
- `TableLockRepositoryPort`
- `CustomerRepositoryPort`
- `ReservationRepositoryPort`
- `QueueTicketRepositoryPort`
- `WalkInRepositoryPort`
- `SeatingRepositoryPort`
- `CleaningRepositoryPort`
- `TurnoverRepositoryPort`
- `BusinessEventRepositoryPort`
- `StateTransitionLogRepositoryPort`
- `AuditLogRepositoryPort`
- `IdempotencyRepositoryPort`
- `ReasonCodeRepositoryPort`
- `I18nMessageRepositoryPort`

Repository Port decisions:

- Ports are Java interfaces only.
- Ports accept explicit `TenantScope`, `StoreScope`, or `PlatformScope`.
- Ports return Domain Object, domain collection, `Optional`, or boolean values.
- Ports do not return JPA Entity.
- Ports do not extend Spring Data repositories.
- Ports avoid mechanical full CRUD and do not define `findAll`, `delete`, `update`, `saveAll`, or `getOne`.

## 6. Scope Skeleton Created

- `PlatformScope`

Existing scope types confirmed:

- `TenantScope`
- `StoreScope`

## 7. Seating Source Three-Way Handling

`SeatingEntity` keeps database fields:

- `reservationId`
- `queueTicketId`
- `walkInId`

`SeatingMapper` reserves conversion to:

- `SeatingSourceMapping`

Application Service should not inspect the three nullable ids directly.

## 8. SeatingResource Target Two-Way Handling

`SeatingResourceEntity` keeps database fields:

- `resourceType`
- `tableId`
- `tableGroupId`

`SeatingResourceMapper` reserves conversion to:

- `SeatingResourceTargetMapping`

## 9. Cleaning Target Two-Way Handling

`CleaningEntity` keeps database fields:

- `resourceType`
- `tableId`
- `tableGroupId`

`CleaningMapper` reserves conversion to:

- `CleaningResourceTargetMapping`

## 10. TargetRef / Metadata Handling

Generic target:

- `business_events.target_type + target_id`
- `state_transition_logs.target_type + target_id`
- `audit_logs.target_type + target_id`
- `idempotency_records.target_type + target_id`

Mapper boundary:

- `TargetRef`

JSONB placeholder:

- `MetadataPayload`
- `SnapshotPayload`

JSONB fields are mapped as `String` placeholders in Entity skeletons. No scattered `Map<String, Object>` is used.

## 11. Forbidden Artifact Check

- Spring Data Repository created: No.
- Repository Implementation created: No.
- Application Service created: No.
- Controller created: No.
- API DTO created: No.
- API implemented: No.
- UI implemented: No.
- Migration changed: No.
- Database touched: No.
- Seed data inserted: No.

Boundary note:

- `TableGroupMemberEntity` was created because `table_group_members` is a required table-group membership table. It is not `MemberEntity` and does not represent membership, loyalty, marketing, payment, or POS.

## 12. Validation

TDD red run:

- Initial persistence skeleton tests failed because `PlatformScope`, Entity classes, Mapper classes, and Repository Port interfaces did not exist.

Green run:

- Command: `mvn test`
- Result: Success.
- Tests run: 29.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Static checks:

- No `@Repository`.
- No Spring Data `JpaRepository`, `CrudRepository`, or `PagingAndSortingRepository`.
- No Repository implementation.
- No Application Service.
- No Controller.
- No API DTO / Request / Response.
- No Mapper implementation.
- No additional migration.
- Migration documentation and runtime V001 hashes match.
- No seed/mock/insert data in migration files.

## 13. Open Questions

- Query intent skeleton remains deferred to Application Query Design.
- Command package migration remains deferred to Application Layer Refactor.
- Mapper implementation detail, including JSON library and domain construction strategy, remains deferred to a Mapper Implementation round.
- Repository implementation and complex queries remain deferred.

## 14. Open Conflicts

- None.

## 15. Next Step Recommendation

- Proceed to Backend Application Contract Design.
- Next round should design Application Service, Command Handler, and Query Handler boundaries only.
- Do not jump directly to Controller, REST API, UI, repository implementation, complex SQL queries, or business workflow implementation.
