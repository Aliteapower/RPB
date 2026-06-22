# Vertical Slice Checklist V1

## Purpose

This checklist validates that the first backend vertical-slice application contract stays limited to:

```text
WalkIn Direct Seating
```

It is documentation only. It does not create Java code, Repository Implementation, Spring Data Repository, API, UI, migration, SQL, seed data, mock data, configuration, tests, or database connections.

## Read Inputs

- [x] `docs/governance/BUSINESS_GLOSSARY.md` was read.
- [x] `docs/governance/BUSINESS_RULES.md` was read.
- [x] `docs/governance/DATA_STANDARD.md` was read.
- [x] `docs/governance/DATA_CHECKLIST.md` was read.
- [x] `docs/architecture/ARCHITECTURE.md` was read.
- [x] `docs/skills/reservation-system/SKILL_OVERVIEW.md` was read.
- [x] `docs/skills/reservation-system/SKILL.md` was read.
- [x] `docs/database/DATABASE_DESIGN.md` was read.
- [x] `docs/database/ERD.md` was read.
- [x] `docs/database/SCHEMA_DESIGN.md` was read.
- [x] `docs/database/MIGRATION_REVIEW_REPORT.md` was read.
- [x] `docs/database/MIGRATION_VALIDATION_REPORT.md` was read.
- [x] `docs/database/migrations/V001__reservation_platform_bootstrap.sql` was read.
- [x] `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql` was read.
- [x] `docs/backend/DOMAIN_MODEL_DESIGN.md` was read.
- [x] `docs/backend/STATE_MACHINE_DESIGN.md` was read.
- [x] `docs/backend/RULE_POLICY_VALIDATOR_DESIGN.md` was read.
- [x] `docs/backend/BACKEND_DESIGN_CHECKLIST.md` was read.
- [x] `docs/backend/BACKEND_SKELETON_IMPLEMENTATION_REPORT.md` was read.
- [x] `docs/backend/PERSISTENCE_CONTRACT_DESIGN.md` was read.
- [x] `docs/backend/ENTITY_MAPPING_DESIGN.md` was read.
- [x] `docs/backend/REPOSITORY_PORT_DESIGN.md` was read.
- [x] `docs/backend/PERSISTENCE_DESIGN_CHECKLIST.md` was read.
- [x] `docs/backend/PERSISTENCE_SKELETON_IMPLEMENTATION_REPORT.md` was read.

## Phase Boundary

- [x] Only `WalkIn Direct Seating` is selected as the vertical slice.
- [x] The contract does not expand into full Reservation application design.
- [x] The contract does not expand into full Queue application design.
- [x] The contract does not expand into Cleaning completion.
- [x] The contract does not expand into Turnover calculation.
- [x] The contract does not expand into Customer full search or merge.
- [x] The contract does not define API endpoint paths.
- [x] The contract does not define API DTOs.
- [x] The contract does not define UI pages or components.
- [x] The contract does not create Repository Implementation.
- [x] The contract does not create Java Application Service code.
- [x] The contract does not create Controller code.
- [x] The contract does not create Mapper Implementation.
- [x] The contract does not create Spring Data Repository.
- [x] The contract does not create tests.
- [x] The contract does not change migration or SQL.
- [x] The contract does not connect to a database.

## Business Object Boundary

- [x] WalkIn is kept separate from QueueTicket.
- [x] WalkIn can be seated directly without queue number.
- [x] QueueTicket is not created by this flow.
- [x] Reservation is not created or updated by this flow.
- [x] CheckIn is not used as a primary entity.
- [x] Seating is created as occupancy and is separate from WalkIn.
- [x] Seating source is exactly WalkIn for this flow.
- [x] SeatingResource target is exactly one DiningTable or TableGroup.
- [x] DiningTable is kept separate from TableGroup.
- [x] Cleaning is not performed by this flow.
- [x] Turnover is not calculated by this flow.
- [x] Customer remains Tenant-scoped and is not Member.
- [x] No-phone Customer or temporary guest remains supported.

## Command and Application Service Boundary

- [x] `SeatWalkInDirectlyCommand` is defined as application command intent.
- [x] Command includes `tenantId`.
- [x] Command includes `storeId`.
- [x] Command includes `partySize`.
- [x] Command supports optional `customerId`.
- [x] Command supports optional `customerName` / `nickname`.
- [x] Command supports optional `phoneE164`.
- [x] Command supports optional `tableId`.
- [x] Command supports optional `tableGroupId`.
- [x] Command includes `idempotencyKey`.
- [x] Command includes `actorId`.
- [x] Command includes `actorType`.
- [x] API DTO names such as `SeatWalkInRequest` and `SeatWalkInResponse` are not used.
- [x] `WalkInDirectSeatingApplicationService` is defined as orchestration boundary only.
- [x] Application Service does not own SQL details.
- [x] Application Service does not own table assignment algorithm internals.
- [x] Application Service does not own business rule internals.
- [x] Application Service does not own UI copy or API parsing.

## Required Port Boundary

- [x] Required Store port methods are identified.
- [x] Required Customer port methods are identified.
- [x] Required DiningTable port methods are identified.
- [x] Required TableGroup port methods are identified.
- [x] Required TableLock port methods are identified.
- [x] Required WalkIn port methods are identified.
- [x] Required Seating port methods are identified.
- [x] Required BusinessEvent port methods are identified.
- [x] Required StateTransitionLog port methods are identified.
- [x] Required AuditLog port methods are identified.
- [x] Required Idempotency port methods are identified.
- [x] Required methods are scoped by TenantScope, StoreScope, or contextual scope.
- [x] Required methods are not mechanical CRUD.
- [x] Repository Ports do not expose Persistence Entity.
- [x] Spring Data Repository is not introduced.

## Rule / Policy / Validator Boundary

- [x] `StoreAccessPolicy` is included.
- [x] `CustomerIdentityRule` is included.
- [x] `TableAvailabilityRule` is included.
- [x] `TableCapacityRule` is included.
- [x] `TableLockRule` is included.
- [x] `TableAssignmentRule` is included.
- [x] `TableGroupValidationRule` is included for group resource validation.
- [x] `SeatingSourceValidator` is included.
- [x] `SeatingResourceValidator` is included.
- [x] `DiningTableStateMachine` is included.
- [x] `AuditRule` is included.
- [x] `BusinessEventRule` is included.
- [x] `StateTransitionRule` is included.
- [x] `IdempotencyRule` is included.
- [x] Supporting Store timezone/date policy is acknowledged.
- [x] Supporting nullable E.164 phone validation is acknowledged.
- [x] Rule failures return stable violation codes, not display text.

## Transaction Boundary

- [x] One command equals one transaction.
- [x] Idempotency start is inside the command boundary.
- [x] Resource lock decision is inside the command boundary.
- [x] WalkIn creation is inside the command boundary.
- [x] Seating creation is inside the command boundary.
- [x] SeatingResource creation is inside the command boundary.
- [x] Table or TableGroup occupancy transition is inside the command boundary.
- [x] BusinessEvent append is inside the command boundary.
- [x] StateTransitionLog append is inside the command boundary.
- [x] AuditLog append is inside the command boundary.
- [x] Idempotency completion is inside the command boundary.
- [x] Lock release, cancellation, rollback, or expiry behavior is described for failures.
- [x] Idempotency failed behavior is described for failures.
- [x] Audit write failure is treated as blocking.

## Idempotency Boundary

- [x] Action code `seat_walk_in_directly` is defined.
- [x] Scope uses Store-level idempotency boundary.
- [x] Migration-compatible scope is documented as `tenant_id + store_id + source + action + idempotency_key`.
- [x] Request hash is required.
- [x] Repeated same completed request returns the same application result.
- [x] In-progress repeated request does not duplicate occupancy.
- [x] Failed request behavior is defined.
- [x] Different hash with same key returns `idempotency_conflict`.
- [x] Same request must not create duplicate WalkIn, Seating, lock, or resource occupancy.

## State / Audit / Event Boundary

- [x] WalkIn state boundary is defined.
- [x] Seating state boundary is defined.
- [x] DiningTable state boundary is defined.
- [x] Temporary TableGroup state boundary is acknowledged.
- [x] TableLock state boundary is defined.
- [x] IdempotencyRecord state boundary is defined.
- [x] `walk_in.created` event is required.
- [x] `seating.created` event is required.
- [x] `table.locked` event is required.
- [x] `table.occupied` event is required.
- [x] Event fields include target, actor, scope, time, reason, idempotency, and metadata.
- [x] Audit fields include target, actor, scope, before/after state, reason, failure, idempotency, and metadata.
- [x] StateTransitionLog is required for critical accepted transitions.

## Failure Case Coverage

- [x] Store not found is covered.
- [x] StoreScope mismatch is covered.
- [x] Invalid party size is covered.
- [x] Customer identity failure is covered.
- [x] No available table is covered.
- [x] Party size over capacity is covered.
- [x] Table lock conflict is covered.
- [x] Invalid TableGroup is covered.
- [x] Invalid Seating source is covered.
- [x] Invalid SeatingResource target is covered.
- [x] Idempotency hash conflict is covered.
- [x] Illegal state transition is covered.
- [x] Audit write failure is covered.
- [x] Repository save failure is covered.
- [x] Each failure includes application error, audit behavior, lock behavior, transaction behavior, and retry guidance.

## Test Contract Coverage

- [x] WalkIn direct seating success is covered.
- [x] No-phone Customer success is covered.
- [x] Existing Customer success is covered.
- [x] Specified table success is covered.
- [x] Automatic table assignment success is covered.
- [x] Specified TableGroup success is covered.
- [x] No available table failure is covered.
- [x] Party size capacity failure is covered.
- [x] Seating source XOR failure is covered.
- [x] SeatingResource target XOR failure is covered.
- [x] Idempotency repeated request replay is covered.
- [x] Idempotency request hash conflict is covered.
- [x] TableLock conflict failure is covered.
- [x] Table state illegal transition failure is covered.
- [x] Audit, Event, and StateTransition expected writes are covered.

## OOD and Reuse Boundary

- [x] The flow starts from business objects, not API endpoints.
- [x] Existing Domain Objects are reused.
- [x] Existing StateMachine boundaries are reused.
- [x] Existing Rule / Policy / Validator boundaries are reused.
- [x] TenantScope and StoreScope are reused.
- [x] Audit, Event, StateTransition, and Idempotency boundaries are reused.
- [x] Table availability, capacity, assignment, and lock rules are reusable.
- [x] Customer identity rule supports no-phone and temporary guests.
- [x] The contract avoids one large service for Reservation, Queue, WalkIn, Seating, Cleaning, and Turnover.
- [x] The contract avoids controller-centric or page-centric design.

## Forbidden Artifact Check

- [x] Reservation implemented: No.
- [x] Queue implemented: No.
- [x] Repository implementation created: No.
- [x] Spring Data Repository created: No.
- [x] Mapper implementation created: No.
- [x] Java Application Service created: No.
- [x] Controller created: No.
- [x] API DTO created: No.
- [x] API implemented: No.
- [x] UI implemented: No.
- [x] Migration changed: No.
- [x] SQL changed: No.
- [x] Database touched: No.
- [x] Seed data inserted: No.
- [x] Mock data inserted: No.
- [x] Production database touched: No.

## Final Gate

- [x] Actual modified files are limited to `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md` and `docs/backend/VERTICAL_SLICE_CHECKLIST.md`.
- [x] The selected vertical slice is clear.
- [x] Required Port subset is clear.
- [x] Required Rule / Policy / Validator subset is clear.
- [x] Transaction boundary is clear.
- [x] Idempotency boundary is clear.
- [x] Audit / Event / StateTransition boundary is clear.
- [x] Failure cases are clear.
- [x] Test contract is clear.

Next allowed round:

```text
WalkIn Direct Seating Persistence Implementation
```
