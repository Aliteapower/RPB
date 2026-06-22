# Persistence Design Checklist V1

## Purpose

This checklist validates the Persistence Contract Design, Entity Mapping Design, and Repository Port Design boundaries.

This is a documentation checklist only. It does not create Java code, repositories, entities, mappers, APIs, UI, migrations, seed data, or mock data.

## Domain / Entity Boundary

- [x] Domain Object is not changed into Persistence Entity.
- [x] Persistence Entity is defined as a database mapping boundary only.
- [x] Domain Object keeps business meaning, invariants, and lifecycle responsibility.
- [x] Entity does not own state machine transition validation.
- [x] Entity does not own `TableAssignmentRule`.
- [x] Entity does not own `ReservationAvailabilityRule`.
- [x] Entity does not own `TableGroupValidationRule`.
- [x] Entity does not own Customer identity matching.
- [x] Entity does not own audit decision logic.
- [x] Entity does not own idempotency replay decision.

## Repository Boundary

- [x] Domain Object does not depend on Repository.
- [x] Repository Port is separate from Repository Implementation.
- [x] Repository Port is separate from Spring Data Repository.
- [x] Repository Port is designed around domain capabilities, not database table CRUD.
- [x] Repository Port does not expose Persistence Entity to Domain Object.
- [x] Repository Port does not expose Persistence Entity to Application Service.
- [x] Repository Implementation is deferred to a later round.
- [x] Spring Data Repository is deferred to a later round.
- [x] JPA Entity implementation is deferred to a later round.

## CRUD / Method Shape

- [x] Repository Port is not mechanically generated per table as generic CRUD.
- [x] `findAll` is not used as a default method pattern.
- [x] Physical `delete` is not used as a default method pattern.
- [x] Method examples are scoped and business-purpose based.
- [x] Active vs historical lookup is explicitly separated by method intent.

## Scope

- [x] Tenant-level data uses `TenantScope`.
- [x] Store-level operational data uses `StoreScope`.
- [x] Platform-level data uses future `PlatformScope` or an explicit no-tenant boundary.
- [x] Repository Ports require scope.
- [x] No-scope query for Store-level data is forbidden.
- [x] Cross-tenant query is forbidden outside future platform administration design.
- [x] Audit/event/idempotency scope follows contextual target and actor scope.

## Mapping Risks

- [x] Seating source three-way rule is clearly encapsulated as `SeatingSource`.
- [x] Seating does not expose repeated nullable-id checks to Application Service.
- [x] SeatingResource table/group target is clearly encapsulated as `SeatingResourceTarget`.
- [x] Cleaning table/group target is clearly encapsulated as `CleaningResourceTarget`.
- [x] ReservationPreassignment table/group target is clearly encapsulated as a target boundary.
- [x] Customer phone nullable is preserved.
- [x] Customer identity does not rely on phone only.
- [x] Customer uniqueness remains Tenant-scoped.
- [x] Generic target fields are mapped to `TargetRef`.
- [x] Generic targets are not incorrectly forced into hard foreign keys.
- [x] JSONB metadata has a unified strategy with `MetadataPayload` / `SnapshotPayload`.
- [x] Timestamps map to UTC `Instant`.
- [x] Business dates map to `LocalDate`.
- [x] Store timezone/locale display is outside Entity.

## Command / Query / DTO Boundary

- [x] Command is identified as application intent.
- [x] Command should not permanently remain in domain package.
- [x] Query is identified as application read intent.
- [x] Query skeletons are design-only in this round.
- [x] DTO is identified as API boundary, not domain or persistence boundary.
- [x] Mapper is not API DTO mapping.
- [x] Mapper is only Domain Object / Persistence Entity conversion.

## Business Object Separation

- [x] Reservation remains separate from QueueTicket.
- [x] WalkIn remains separate from QueueTicket.
- [x] CheckIn remains a business event, not `CheckInEntity`.
- [x] Seating remains separate from Reservation creation.
- [x] Cleaning remains separate from Turnover.
- [x] Table remains separate from TableGroup.
- [x] Customer remains separate from Member.
- [x] Turnover remains derived from Seating and Cleaning, not Reservation alone.

## Forbidden Artifact Check

- [x] Java persistence code was not created.
- [x] Java Entity was not created.
- [x] Repository interface was not created.
- [x] Spring Data Repository was not created.
- [x] Repository implementation was not created.
- [x] Mapper class was not created.
- [x] Application Service was not created.
- [x] Controller was not created.
- [x] API DTO was not created.
- [x] REST API was not implemented.
- [x] UI was not implemented.
- [x] Migration was not changed.
- [x] SQL file was not created.
- [x] Seed data was not created.
- [x] Mock data was not created.
- [x] Database was not connected.
- [x] Flyway was not run.

## Next Round Gate

- [x] Persistence Contract boundary is clear.
- [x] Entity Mapping boundary is clear.
- [x] Repository Port boundary is clear.
- [x] Scope requirements are explicit.
- [x] Command / Query / DTO boundary is explicit.
- [x] No Java persistence artifact was created in this round.

Next allowed round:

```text
Backend Persistence Skeleton Implementation
```

That later round may create JPA Entity skeletons, Mapper skeletons, and Repository Port interface skeletons if explicitly requested, but should still avoid complex repository queries, Application Service, Controller, API, and UI.

## Implementation Skeleton Round Check

- [x] JPA Entity skeletons were created for V001 tables.
- [x] Mapper skeletons were created.
- [x] Repository Port interface skeletons were created.
- [x] `PlatformScope` was created.
- [x] Repository Ports do not return Persistence Entity types.
- [x] Repository Ports do not extend Spring Data repositories.
- [x] Repository Ports require explicit scope.
- [x] Mapper skeletons do not access repositories or databases.
- [x] Mapper skeletons do not make business decisions.
- [x] Entity skeletons do not contain state machine transition logic.
- [x] Entity skeletons do not contain table assignment logic.
- [x] Entity skeletons do not contain workflow methods for reservation creation, queue calling, seating, cleaning completion, or turnover calculation.
- [x] `CheckInEntity` was not created.
- [x] `MemberEntity`, `PaymentEntity`, `MarketingEntity`, and `PosEntity` were not created.
- [x] Spring Data Repository was not created.
- [x] Repository Implementation was not created.
- [x] Application Service was not created.
- [x] Controller was not created.
- [x] API DTO was not created.
- [x] API was not implemented.
- [x] UI was not implemented.
- [x] Migration was not changed.
- [x] Database was not connected.
- [x] Seed data was not inserted.
- [x] `mvn test` passed for the skeleton implementation round.
