# Persistence Contract Design V1

## Purpose

This document defines the backend persistence contract boundary after the Backend Domain Skeleton round.

This round designs persistence boundaries only. It does not create Java persistence code, JPA entities, repository interfaces, repository implementations, mapper classes, application services, controllers, API DTOs, UI, migrations, SQL, seed data, or mock data.

## Inputs Read

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

Backend design and skeleton:

- `docs/backend/DOMAIN_MODEL_DESIGN.md`
- `docs/backend/STATE_MACHINE_DESIGN.md`
- `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md`
- `docs/backend/BACKEND_DESIGN_CHECKLIST.md`
- `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_PLAN.md`
- `docs/backend/BACKEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_REPORT.md`

## Previous Round Confirmation

The Backend Domain Skeleton Implementation report confirms:

- `mvn test` passed.
- 19 tests ran.
- 0 failures.
- 0 errors.
- Repository created: No.
- Controller created: No.
- API implemented: No.
- Migration changed: No.
- Database touched: No.

## Persistence Boundary Overview

The intended persistence direction is:

```text
Domain Object
↓
Persistence Entity
↓
Mapper Boundary
↓
Repository Port
↓
Repository Implementation Later
```

This ordering describes dependency and responsibility boundaries, not an implementation sequence for this round.

## Domain Object vs Persistence Entity

Domain Object:

- Represents business meaning, invariants, lifecycle, and operation intent.
- Uses value objects, status enums, state machines, rules, policies, and validators.
- Must not carry JPA annotations.
- Must not depend on Spring Data, JPA, EntityManager, SQL, or persistence implementation classes.
- Must not be reshaped for database-row convenience.

Persistence Entity:

- Represents one database table mapping unless explicitly documented otherwise.
- Carries persistence columns, database identity, timestamps, soft-delete fields, optimistic version, and persistence relationships needed by the mapper.
- Must not own state machine transition logic.
- Must not run `ReservationAvailabilityRule`, `TableAssignmentRule`, `TableGroupValidationRule`, or cross-scope authorization checks.
- Must not become the public domain model.

Persistence Entity may validate simple structural nullability before persistence implementation, but any business decision belongs to Domain Object, Rule, Policy, Validator, or Application Service.

## Repository Port vs Repository Implementation

Repository Port:

- Is the abstraction needed by Application Service and domain orchestration.
- Is designed around aggregate and use-case capabilities, not around table-by-table CRUD.
- Accepts `TenantScope`, `StoreScope`, or `PlatformScope` according to data level.
- Returns Domain Object, domain projection, or existence/count result.
- Must not expose Persistence Entity to Application Service or Domain Object.
- Must not expose Spring Data repositories or JPA APIs.

Repository Implementation:

- Will be implemented in a later round.
- May depend on Spring Data, JPA, EntityManager, SQL, mapper classes, and persistence entities.
- Converts between Persistence Entity and Domain Object through mapper boundary.
- Enforces scope filters at query boundary before returning data.
- Must not be referenced by Domain Object.

Dependency rule:

```text
Application Service -> Repository Port
Repository Implementation -> Spring Data / JPA / Mapper / Entity
Domain Object -> no Repository dependency
```

## Mapper Boundary

Mapper responsibilities:

- Convert Persistence Entity to Domain Object.
- Convert Domain Object to Persistence Entity or update an existing Persistence Entity.
- Convert database status code strings to status enums.
- Convert `timestamptz` to Java `Instant`.
- Convert `date` business date to Java `LocalDate`.
- Convert nullable source/resource columns into explicit domain references.
- Convert generic target fields into `TargetRef`.
- Convert JSONB fields into a unified metadata or snapshot wrapper.

Mapper must not:

- Decide whether a state transition is legal.
- Decide table availability.
- Choose a table or table group.
- Perform tenant/store authorization.
- Create audit records by itself.
- Call repositories.
- Contain API DTO logic.

## Scope Boundary

All Repository Ports must accept explicit scope.

Scope types:

- `PlatformScope`: platform-owned data without tenant/store ownership. This type is not implemented yet and is only a future design boundary.
- `TenantScope`: tenant-owned data, cross-store customer identity, tenant reason codes, and tenant-scoped catalog overrides.
- `StoreScope`: store operations, table resources, reservations, queue tickets, walk-ins, seatings, cleanings, turnovers, table locks, store policy, and store areas.

Rules:

- Tenant-level data uses `TenantScope`.
- Store-level operational data uses `StoreScope`.
- Platform-level data uses `PlatformScope` or an explicitly documented no-tenant boundary.
- No Store-level Repository Port may offer a no-scope query.
- No Repository Port may allow cross-tenant access unless it is a platform administration port designed in a later identity/platform round.
- Audit, event, idempotency, and state transition records use contextual scope that matches their target and actor.

## Command / Query Position

The previous round created Command skeletons inside feature packages as domain intent placeholders.

Long-term decision:

- Command should not stay in domain package permanently.
- Command belongs to application intent.
- Query belongs to application read intent.
- DTO belongs to API boundary, not application or domain.

Recommended later package targets:

```text
com.rpb.reservation.application.command
com.rpb.reservation.application.query
```

Domain package should keep:

- Domain Object.
- Value Object.
- Status Enum.
- StateMachine.
- Rule / Policy / Validator.
- Domain Result / Decision.

Query skeletons are design-only in this round. No query code is created.

## Future Package Boundary Recommendation

This round does not create Java packages. Future implementation may use:

```text
com.rpb.reservation.<module>.domain
com.rpb.reservation.<module>.value
com.rpb.reservation.<module>.status
com.rpb.reservation.<module>.state
com.rpb.reservation.<module>.rule
com.rpb.reservation.<module>.policy
com.rpb.reservation.<module>.validator

com.rpb.reservation.application.command
com.rpb.reservation.application.query
com.rpb.reservation.application.port

com.rpb.reservation.infrastructure.persistence.entity
com.rpb.reservation.infrastructure.persistence.mapper
com.rpb.reservation.infrastructure.persistence.repository
```

Alternative package grouping by feature is acceptable later if the same dependency direction is preserved.

## Mechanical CRUD Is Forbidden

Repository Ports must not be generated mechanically as:

```text
findAll
save
delete
update
```

Minimum capabilities must follow business use cases. Examples:

- Reservation needs conflict checks, lookup by scoped id/code, active schedule lookup, and save.
- QueueTicket needs active queue lookup, next callable lookup, scoped id lookup, and save.
- Seating needs source lookup, active occupancy lookup, and save.
- TableGroup needs active membership lookup and validity support.

Delete operations should be explicit domain operations such as cancel, archive, release, end, or mark inactive. Physical delete is not the default for business history.

## Entity Must Not Carry Business Rules

Persistence Entity must not contain:

- State machine transition validation.
- Reservation availability calculation.
- Queue ordering decision.
- Table assignment decision.
- TableGroup semantic validation.
- Customer identity matching.
- E.164 validation beyond structural persistence mapping.
- Store timezone display conversion.
- Audit decision logic.
- Idempotency replay decision.

The entity may expose persistence fields needed by mapper and repository implementation, but behavior must remain outside the entity.

## Domain Must Not Depend on Repository

Domain Object must not:

- Inject Repository Port.
- Call Repository Implementation.
- Call Spring Data repository.
- Open database transactions.
- Perform JPA lazy loading.
- Resolve relationships through persistence.

Any operation requiring storage lookup belongs to Application Service orchestration through Repository Port plus domain rules/policies.

## Critical Mapping Boundaries

### Seating Source

Database columns:

```text
reservation_id / queue_ticket_id / walk_in_id exactly one
```

Domain mapping target:

```text
SeatingSource
```

Application Service must not repeatedly inspect three nullable ids. Mapper converts the database XOR shape to an explicit source type.

### SeatingResource Target

Database columns:

```text
resource_type + table_id / table_group_id exactly one
```

Domain mapping target:

```text
SeatingResourceTarget
```

### Cleaning Target

Database columns:

```text
resource_type + table_id / table_group_id exactly one
```

Domain mapping target:

```text
CleaningResourceTarget
```

### Generic Target

Tables:

- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`

Database columns:

```text
target_type
target_id
```

Domain mapping target:

```text
TargetRef
```

These generic targets must not be forced into incorrect foreign keys.

### JSONB Metadata

JSONB fields must not become raw `Map<String, Object>` everywhere in domain code.

Recommended later wrapper:

```text
MetadataPayload
SnapshotPayload
```

Persistence implementation can choose a JSON library later, but Repository Port and Domain Object should see typed wrappers or purpose-specific snapshot objects.

## Not Implemented In This Round

- No Java Entity.
- No Repository interface.
- No Spring Data Repository.
- No Repository implementation.
- No Mapper class.
- No Application Service.
- No Controller.
- No API DTO.
- No REST API.
- No Vue UI.
- No migration.
- No SQL file.
- No seed data.
- No mock data.
- No database connection.
