# Seating From Called Queue Application Contract V1

## 1. Purpose

This document defines the minimum application contract for seating a called QueueTicket that was created from an arrived Reservation.

Business flow:

```text
Reservation arrived -> QueueTicket waiting
-> QueueTicket called
-> Store staff selects exactly one DiningTable or TableGroup
-> Seating is created with source = QueueTicket
-> SeatingResource is created for the selected resource
-> selected table or TableGroup member tables become occupied
-> QueueTicket status changes from called to seated
-> Reservation status changes from arrived to seated
-> BusinessEvent, StateTransitionLog, AuditLog, and Idempotency are handled
```

This is a contract-only round. It does not create Java code, repository implementation, controller, API DTO, Vue UI, router entry, App Gate metadata, Flyway migration, SQL, seed data, production configuration, or database data.

## 2. Read Inputs

Reservation Arrived To Queue baseline:

- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`

Queue Call baseline:

- `docs/backend/QUEUE_CALL_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`

Direct Seating baseline:

- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`

Queue, seating, table, schema, and persistence inputs:

- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/domain/QueueGroup.java`
- `src/main/java/com/rpb/reservation/queue/status/QueueTicketStatus.java`
- `src/main/java/com/rpb/reservation/queue/state/QueueTicketStateMachine.java`
- `src/main/java/com/rpb/reservation/queue/application/port/out/QueueTicketRepositoryPort.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueTicketEntity.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/adapter/QueueTicketPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/queue/persistence/repository/QueueTicketJpaRepository.java`
- `src/main/java/com/rpb/reservation/queue/domain/QueueGroup.java`
- `src/main/java/com/rpb/reservation/seating/domain/Seating.java`
- `src/main/java/com/rpb/reservation/seating/domain/SeatingResource.java`
- `src/main/java/com/rpb/reservation/seating/status/SeatingStatus.java`
- `src/main/java/com/rpb/reservation/seating/state/SeatingStateMachine.java`
- `src/main/java/com/rpb/reservation/seating/application/port/out/SeatingRepositoryPort.java`
- `src/main/java/com/rpb/reservation/seating/persistence/entity/SeatingEntity.java`
- `src/main/java/com/rpb/reservation/seating/persistence/entity/SeatingResourceEntity.java`
- `src/main/java/com/rpb/reservation/seating/persistence/mapper/DefaultSeatingMapper.java`
- `src/main/java/com/rpb/reservation/seating/persistence/mapper/DefaultSeatingResourceMapper.java`
- `src/main/java/com/rpb/reservation/seating/persistence/adapter/SeatingPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/seating/validator/SeatingSourceValidator.java`
- `src/main/java/com/rpb/reservation/seating/validator/SeatingResourceValidator.java`
- `src/main/java/com/rpb/reservation/table/domain/DiningTable.java`
- `src/main/java/com/rpb/reservation/table/domain/TableGroup.java`
- `src/main/java/com/rpb/reservation/table/domain/TableGroupMember.java`
- `src/main/java/com/rpb/reservation/table/domain/TableLock.java`
- `src/main/java/com/rpb/reservation/table/status/DiningTableStatus.java`
- `src/main/java/com/rpb/reservation/table/status/TableGroupStatus.java`
- `src/main/java/com/rpb/reservation/table/status/TableLockStatus.java`
- `src/main/java/com/rpb/reservation/table/state/DiningTableStateMachine.java`
- `src/main/java/com/rpb/reservation/table/application/port/out/DiningTableRepositoryPort.java`
- `src/main/java/com/rpb/reservation/table/application/port/out/TableGroupRepositoryPort.java`
- `src/main/java/com/rpb/reservation/table/application/port/out/TableLockRepositoryPort.java`
- `src/main/java/com/rpb/reservation/table/rule/DefaultTableAvailabilityRule.java`
- `src/main/java/com/rpb/reservation/table/rule/DefaultTableCapacityRule.java`
- `src/main/java/com/rpb/reservation/table/rule/DefaultTableAssignmentRule.java`
- `src/main/java/com/rpb/reservation/table/rule/DefaultTableLockRule.java`
- `src/main/java/com/rpb/reservation/table/group/rule/DefaultTableGroupValidationRule.java`
- `src/main/java/com/rpb/reservation/reservation/domain/Reservation.java`
- `src/main/java/com/rpb/reservation/reservation/status/ReservationStatus.java`
- `src/main/java/com/rpb/reservation/reservation/state/ReservationStateMachine.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/BusinessEventRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/StateTransitionLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/AuditLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/idempotency/application/port/out/IdempotencyRepositoryPort.java`
- `src/main/java/com/rpb/reservation/idempotency/rule/DefaultIdempotencyRule.java`

App Gate inputs:

- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`

Governance, architecture, and skill inputs:

- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`

Confirmed baseline:

- Reservation Arrived To Queue has passed and can create `QueueTicket.status = waiting`.
- Queue Call has passed and can move `QueueTicket.status` from `waiting` to `called`.
- Queue Call keeps the related Reservation status as `arrived`.
- QueueTicket enum and V001 schema contain `seated`.
- QueueTicket enum and V001 schema do not contain `completed`.
- QueueTicket state machine allows `called -> seated`.
- Reservation enum and V001 schema contain `seated`.
- Reservation state machine allows `arrived -> seated`.
- Seating and SeatingResource already support a QueueTicket source through `seatings.queue_ticket_id`.
- SeatingResource already supports exactly one selected `dining_table` or `table_group` target.
- Direct Seating has reusable table, TableGroup, seating, resource, event, transition, audit, and idempotency patterns.
- App Gate currently contains `queue.call` but does not contain `queue.seat`; this round must not modify it.

## 3. Selected Vertical Slice

Selected slice:

```text
Seating From Called Queue
```

Input preconditions:

```text
queue_ticket.status = called
queue_ticket.reservation_id is present
reservation.status = arrived
staff selected exactly one tableId or tableGroupId
```

Required output:

```text
queue_ticket.status = seated
reservation.status = seated
seating created with queue_ticket_id = queueTicketId
seating_resource created for exactly one selected table or tableGroup
selected table or TableGroup member tables occupied
business events written
state transitions written
audit written
idempotency completed
```

This slice is manual seating only. Staff must explicitly select a resource. It does not auto-assign or recommend tables.

## 4. Scope

In scope:

- Application command contract for seating a called QueueTicket.
- Store-scoped QueueTicket lookup by `tenantId`, `storeId`, and `queueTicketId`.
- Validation that QueueTicket belongs to the Store scope.
- Validation that QueueTicket source is an arrived Reservation for V1.
- Validation that QueueTicket status is `called` for fresh seating.
- Validation that related Reservation exists and is `arrived`.
- Manual resource selection through exactly one of `tableId` or `tableGroupId`.
- Validation that selected resource belongs to the Store scope.
- Validation that selected resource can be occupied.
- Creation of a `seatings` row with source = QueueTicket.
- Creation of a `seating_resources` row for exactly one selected resource.
- DiningTable or TableGroup member-table occupancy.
- QueueTicket transition:

```text
called -> seated
```

- Reservation transition:

```text
arrived -> seated
```

- BusinessEvent boundary.
- StateTransitionLog boundary.
- AuditLog boundary.
- Idempotency boundary for `seat_called_queue_ticket`.
- AlreadySeated duplicate behavior.
- App Gate boundary for a later API round.
- Failure and test contract for later implementation.

## 5. Non-Scope

Out of scope:

- Controller.
- REST API implementation.
- API DTO.
- Vue page.
- Vue component.
- Vue Router entry.
- Staff Home entry.
- App Gate Java registry modification.
- App Gate permission metadata modification.
- Flyway migration.
- SQL file change.
- Database structure change.
- Queue Skip.
- Queue Rejoin.
- Queue Display Screen.
- Queue list/workbench.
- WalkIn queue seating.
- Direct Reservation seating.
- Auto assignment.
- Table recommendation.
- Table ranking.
- Drag-and-drop table map.
- No-show.
- Cancellation.
- Cleaning start.
- Cleaning completion.
- Turnover creation or BI.
- Production configuration.
- Production database connection.
- Seed data.

## 6. QueueTicket Status Decision

Fresh success transition:

```text
called -> seated
```

Decision:

- V1 uses `QueueTicket.status = seated` after successful seating from called queue.
- Current schema and enum contain `seated`.
- Current schema and enum do not contain `completed`.
- V1 must not use, require, or write QueueTicket `completed`.
- If a future migration adds QueueTicket `completed`, a later contract must decide how it relates to this slice.

Rules:

- QueueTicket must already exist.
- QueueTicket must belong to the current Tenant and Store scope.
- QueueTicket must be source-linked to a Reservation for this V1.
- Fresh seating is allowed only from `called`.
- `waiting`, `skipped`, `rejoined`, `cancelled`, and `expired` are rejected for fresh seating.
- `seated` is handled only by the success-like AlreadySeated branch when evidence is complete.
- QueueTicket state machine must validate `called -> seated`.

Persisted value:

```text
queue_tickets.status = seated
```

Call evidence:

- `called_at` and `expires_at` are not modified by this slice.
- A called ticket missing durable call evidence should return a consistency error rather than silently seating it.

## 7. Reservation Status Decision

Fresh success transition:

```text
arrived -> seated
```

Rules:

- The related Reservation is loaded from `queue_ticket.reservation_id`.
- The command must not accept `reservationId` from the client.
- Reservation must belong to the same Tenant and Store scope.
- Reservation must be `arrived` for fresh success.
- Reservation state machine must validate `arrived -> seated`.
- Queue seating must not introduce a Reservation `called` status.
- Queue seating must not modify Reservation state machine definitions.

AlreadySeated consistency:

- If QueueTicket is already `seated`, the related Reservation should already be `seated`.
- If QueueTicket is already `seated` but related Reservation is not `seated`, return an application-level consistency error.
- If related Reservation is already `seated` while QueueTicket is still `called`, return an application-level consistency error instead of creating another seating record.

## 8. Seating Source Boundary

Seating must use the existing `seatings` table.

For this slice:

```text
seatings.reservation_id = null
seatings.queue_ticket_id = queueTicketId
seatings.walk_in_id = null
```

Reason:

- V001 requires exactly one Seating source through `ck_seatings_source`.
- Queue seating source is the QueueTicket, not the Reservation.
- The related Reservation is updated through `QueueTicket.reservationId`, not by setting both `reservation_id` and `queue_ticket_id` on the Seating.

Seating fields:

- `tenant_id` and `store_id` must match QueueTicket, Reservation, and selected resource.
- `party_size_snapshot` must equal QueueTicket party size for this slice.
- `status` should be `occupied` for the accepted V1 result.
- `seated_at` should use the application current UTC instant.
- `manual_override_reason_code` may store `overrideReasonCode` when supplied.
- `note` may store `note` or `overrideNote` according to the later implementation mapping, without losing audit metadata.

Forbidden:

- new seating table.
- `reservation_seatings`.
- `queue_seatings`.
- setting both `reservation_id` and `queue_ticket_id`.
- setting `walk_in_id` in this V1.

## 9. Resource Selection Boundary

Command resource selection must satisfy XOR:

```text
exactly one of tableId or tableGroupId
```

DiningTable target:

```text
seating_resources.resource_type = dining_table
seating_resources.table_id = tableId
seating_resources.table_group_id = null
seating_resources.status = active
```

TableGroup target:

```text
seating_resources.resource_type = table_group
seating_resources.table_id = null
seating_resources.table_group_id = tableGroupId
seating_resources.status = active
```

DiningTable rules:

- Table must exist in the same Tenant and Store.
- Table must not be deleted.
- Table must be usable for seating.
- Table capacity must fit QueueTicket party size.
- Table must not be inactive, cleaning, occupied, or blocked by active occupancy.
- Table must not have an active conflicting lock.
- Table becomes `occupied` after successful seating.

TableGroup rules:

- TableGroup must exist in the same Tenant and Store.
- TableGroup must not be deleted or inactive.
- Fixed TableGroup must be `active`.
- TableGroup capacity must fit QueueTicket party size.
- TableGroup must have valid active members.
- Every member DiningTable must belong to the same Tenant and Store.
- Every member DiningTable must be usable for seating.
- Group and member tables must not have active conflicting locks.
- Group must not have active SeatingResource occupancy.
- Member DiningTables become `occupied` after successful seating.
- Fixed TableGroup configuration status should not be mutated solely to represent occupancy.
- Temporary TableGroup may move to `occupied` only if the current status model and implementation support that lifecycle.

## 10. Command Contract

Command:

```text
SeatCalledQueueTicketCommand
```

Fields:

| Field | Source | Required | Notes |
| --- | --- | ---: | --- |
| `tenantId` | actor/server context | Yes | Trusted Tenant scope. |
| `storeId` | path/server context | Yes | Store operation boundary. |
| `queueTicketId` | caller/path/application input | Yes | Existing QueueTicket id. |
| `tableId` | caller/application input | Conditional | Required when `tableGroupId` is absent. |
| `tableGroupId` | caller/application input | Conditional | Required when `tableId` is absent. |
| `idempotencyKey` | caller/header/application input | Yes | Required for this critical command. |
| `actorId` | actor/server context | Yes | Staff actor id. |
| `actorType` | actor/server context | Yes | Expected value aligns with `staff` for this slice. |
| `overrideReasonCode` | caller/application input | No | Optional audit context only in V1. Does not bypass validation. |
| `overrideNote` | caller/application input | No | Optional audit context only in V1. Does not bypass validation. |
| `note` | caller/application input | No | Optional staff note for Seating and audit metadata. |

Structural rules:

- `tenantId` is required.
- `storeId` is required.
- `queueTicketId` is required.
- `idempotencyKey` is required.
- `actorId` is required.
- `actorType` is required.
- Exactly one of `tableId` or `tableGroupId` is required.
- `tableId` and `tableGroupId` must not both be present.
- `tableId` and `tableGroupId` must not both be absent.
- `tenantId`, `actorId`, `actorType`, role, permission, and Store scope must come from trusted server context in a later API layer.
- Override fields must never bypass Store scope, QueueTicket status, Reservation status, resource availability, capacity, lock, active occupancy, or TableGroup validity checks.

Forbidden command fields:

- `reservationId`
- `walkInId`
- `checkInAt`
- `noShowAt`
- `cancelledAt`
- `cleaningId`
- `turnoverId`
- `queueSkipReason`
- `queueRejoinReason`
- `reservationStatus`
- `queueTicketStatus`
- `seatingId`
- trusted `role`
- trusted `permission`

## 11. Application Result Contract

Recommended result:

```text
SeatingFromCalledQueueResult
```

Success fields should include at least:

- `success = true`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = seated`
- `reservationId`
- `reservationCode`
- `reservationStatus = seated`
- `seatingId`
- `seatingStatus = occupied`
- `seatingResourceStatus = active`
- `resourceType = dining_table | table_group`
- `resourceId`
- `partySizeSnapshot`
- `seatedAt`
- `tableStatus` when target is a DiningTable
- `groupMemberStatuses` when target is a TableGroup
- `occupiedTableIds`
- `alreadySeated`
- `events`
- `idempotency.status`
- `idempotency.replayed`

Failure fields should include at least:

- `success = false`
- `error`
- `idempotency.status`
- `retryLater` when applicable

Terminology note:

- Product wording "seated queue ticket" maps to `queue_tickets.status = seated`.
- Product wording "active seating" maps to `seatings.status = occupied` and `seating_resources.status = active`.

## 12. Application Service Boundary

Application service:

```text
SeatingFromCalledQueueApplicationService
```

Method:

```text
seatCalledQueueTicket(SeatCalledQueueTicketCommand command)
```

Responsibilities:

1. Validate command presence and required fields.
2. Validate `tableId` / `tableGroupId` exactly-one rule.
3. Build `StoreScope` from `tenantId` and `storeId`.
4. Check idempotency using action `seat_called_queue_ticket`.
5. Validate Store exists and actor can access the Store.
6. Load QueueTicket by `StoreScope + queueTicketId`.
7. Validate QueueTicket belongs to Store scope.
8. Validate QueueTicket source is a Reservation for this V1.
9. Load related Reservation through QueueTicket source.
10. Detect AlreadySeated duplicate behavior.
11. Validate QueueTicket status is `called` for fresh seating.
12. Validate durable call evidence exists.
13. Validate related Reservation status is `arrived` for fresh seating.
14. Resolve selected DiningTable or TableGroup by Store scope.
15. Validate resource availability.
16. Validate resource capacity fits QueueTicket party size.
17. Validate active lock conflict does not exist.
18. Validate active SeatingResource occupancy does not already exist for the selected resource.
19. Validate TableGroup membership and member table availability when `tableGroupId` is used.
20. Validate Seating source XOR: QueueTicket present, Reservation absent, WalkIn absent.
21. Validate SeatingResource target XOR: exactly one table or group target.
22. Create Seating with source = QueueTicket.
23. Create SeatingResource for exactly one selected resource.
24. Update selected DiningTable or TableGroup member tables to `occupied`.
25. Transition QueueTicket from `called` to `seated`.
26. Transition Reservation from `arrived` to `seated`.
27. Write required BusinessEvent records.
28. Write required StateTransitionLog records.
29. Write required AuditLog record.
30. Complete idempotency with a replayable response snapshot.
31. Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI message text.
- Queue Skip.
- Queue Rejoin.
- Queue Display Screen.
- Queue list/workbench.
- WalkIn queue seating.
- Auto assignment.
- Table recommendation.
- No-show.
- Cancellation.
- Cleaning start.
- Cleaning completion.
- Turnover.
- Reservation list/search/calendar.
- Migration or SQL design.
- App Gate metadata modification.

Transaction guidance:

- Fresh successful seating should commit QueueTicket save, Reservation save, Seating save, SeatingResource save, resource status updates, BusinessEvent, StateTransitionLog, AuditLog, and idempotency completion atomically.
- Validation should happen before mutation whenever possible.
- If event, transition, audit, QueueTicket persistence, Reservation persistence, resource persistence, Seating persistence, or idempotency completion fails, the application must return a stable application-level error and must not expose raw database exceptions.
- Audit write failure is blocking because seating from queue is a critical operation.
- Completed idempotency replay must not append duplicate events, transitions, audit logs, Seating, SeatingResource, QueueTicket changes, Reservation changes, or resource status changes.

## 13. Required Ports

Required ports:

| Port | Required capability for this slice |
| --- | --- |
| `StoreRepositoryPort` | Load Store by `StoreScope`, validate Store existence and operational scope. |
| `QueueTicketRepositoryPort` | Load QueueTicket by Store scope and id, save `called -> seated`. |
| `ReservationRepositoryPort` | Load related Reservation from QueueTicket source and save `arrived -> seated`. |
| `DiningTableRepositoryPort` | Load selected table, save table status, and load/save member tables for TableGroup targets. |
| `TableGroupRepositoryPort` | Load selected group, load active members, validate group scope and status, save temporary group status only if policy requires it. |
| `TableLockRepositoryPort` | Check active lock conflict for selected resource and member tables. |
| `SeatingRepositoryPort` | Find active seating by QueueTicket source, check active resource occupancy, save Seating, and save SeatingResource. |
| `BusinessEventRepositoryPort` | Append queue, reservation, seating, and table events. |
| `StateTransitionLogRepositoryPort` | Append QueueTicket, Reservation, Seating, table, and TableGroup/member table transition evidence. |
| `AuditLogRepositoryPort` | Append success and required failure audit records. |
| `IdempotencyRepositoryPort` | Start, replay, complete, fail, and detect conflict for `seat_called_queue_ticket`. |

Forbidden ports:

- `CleaningRepositoryPort`
- `TurnoverRepositoryPort`
- No-show repositories or policies
- Cancellation repositories or policies
- broad BI/reporting repositories
- mechanical CRUD ports unrelated to this command

## 14. Required Rules / Policies / Validators

Reuse existing rules and validators where possible:

| Component | Purpose |
| --- | --- |
| `StoreAccessPolicy` | Actor must have Tenant and Store scope access. |
| `QueueTicketStateMachine` | Validate `called -> seated`. |
| `ReservationStateMachine` | Validate `arrived -> seated`. |
| `DiningTableStateMachine` | Validate table movement into `occupied` through the existing table transition style. |
| `TableAvailabilityRule` | Selected resource and member tables must be available for seating. |
| `TableCapacityRule` | QueueTicket party size must fit selected resource capacity. |
| `TableLockRule` | Existing active lock must block conflicting seating. |
| `TableAssignmentRule` | Reuse only manual resource selection validation helpers. Do not use automatic assignment. |
| `TableGroupValidationRule` | Validate group status, active members, member Store scope, and member availability. |
| `SeatingSourceValidator` | Accept exactly QueueTicket source for this slice; reject Reservation and WalkIn source. |
| `SeatingResourceValidator` | Accept exactly one DiningTable or TableGroup target. |
| `AuditRule` | Require and shape `queue.seat` audit. |
| `BusinessEventRule` | Require and validate event codes and targets. |
| `StateTransitionRule` | Require and validate transition evidence. |
| `IdempotencyRule` | Apply replay, in-progress, failed-key, and conflict behavior. |

New or supplemented rules:

| Component | Purpose |
| --- | --- |
| `QueueTicketSeatRule` | Accept fresh seating only from called, define AlreadySeated behavior, validate call evidence, and reject non-seat statuses. |
| `SeatingFromCalledQueueRule` | Validate Reservation source requirement and cross-object consistency for QueueTicket, Reservation, Seating, and resource selection. |

Forbidden components:

- Queue Skip rule.
- Queue Rejoin rule.
- Queue Display rule.
- NoShowPolicy.
- CancellationPolicy.
- AutoAssignmentPolicy.
- CleaningReleasePolicy.
- TurnoverPolicy.

## 15. State Boundary

QueueTicket:

```text
called -> seated
```

Reservation:

```text
arrived -> seated
```

Seating:

```text
none -> occupied
```

or, if following the existing state-machine style:

```text
none -> planned -> locked -> occupied
```

The V1 persisted successful Seating status must be:

```text
occupied
```

SeatingResource:

```text
none -> active
```

DiningTable business outcome:

```text
available -> occupied
```

An implementation may record an internal lock path when it uses a lock as an in-transaction concurrency guard:

```text
available -> locked -> occupied
```

No state transition in this slice:

- skips QueueTicket.
- rejoins QueueTicket.
- expires QueueTicket.
- cancels QueueTicket.
- creates Queue Display state.
- starts Cleaning.
- creates Turnover.
- marks Reservation no-show.
- cancels Reservation.

## 16. Event / Audit Boundary

Minimum required BusinessEvent codes:

| Event type | Target type | Target id | Notes |
| --- | --- | --- | --- |
| `queue_ticket.seated` | `queue_ticket` | QueueTicket id | Called ticket has been seated. |
| `reservation.seated` | `reservation` | Reservation id | Related arrived Reservation has been seated from queue. |
| `seating.created` | `seating` | Seating id | Seating source is QueueTicket. |
| `table.occupied` | `dining_table` or `table_group` | Selected resource id | Resource is occupied by the Seating. |

Minimum required AuditLog operation:

```text
queue.seat
```

Recommended failure audit operation:

```text
queue.seat.failed
```

Required audit metadata:

- `tenantId`
- `storeId`
- `queueTicketId`
- `queueTicketNumber`
- `reservationId`
- `reservationCode`
- `seatingId`
- `resourceType`
- `tableId` or `tableGroupId`
- `partySizeSnapshot`
- previous QueueTicket status
- new QueueTicket status
- previous Reservation status
- new Reservation status
- previous table/member statuses
- new table/member statuses
- `overrideReasonCode`
- `overrideNote`
- `note`
- `actorId`
- `actorType`
- `idempotencyKey`

Failure audit is best effort and must not mask the original application error, except that audit write failure for an accepted business mutation remains blocking.

## 17. StateTransitionLog Boundary

Fresh success must record at least:

| Target type | From | To | Transition code |
| --- | --- | --- | --- |
| `queue_ticket` | `called` | `seated` | `queue_ticket.seat` |
| `reservation` | `arrived` | `seated` | `reservation.seat` |
| `seating` | `planned` or `none` | `occupied` | `seating.occupy` |
| `dining_table` | `available` | `occupied` | `dining_table.occupy` |

When the implementation uses an internal lock path, table transition evidence may be split into:

```text
dining_table: available -> locked, transition_code = dining_table.lock
dining_table: locked -> occupied, transition_code = dining_table.occupy
```

For `tableGroupId`:

- Record a `table_group` transition when the group status changes or when a temporary group is moved to `occupied`.
- Record member `dining_table` transitions for every member table whose status changes to `occupied`.
- If fixed TableGroup status is not mutated, the fixed group must still be represented by `seating_resources`, `table.occupied` event target, and member table transition logs.

Do not record:

- QueueTicket `called -> skipped`.
- QueueTicket `skipped -> rejoined`.
- QueueTicket `called -> cancelled`.
- Reservation `arrived -> no_show`.
- Reservation `arrived -> cancelled`.
- Cleaning transitions.
- Turnover transitions.

## 18. Idempotency Boundary

Action:

```text
seat_called_queue_ticket
```

Idempotency scope:

```text
tenant_id + store_id + source + action + idempotency_key
```

For this slice:

- `source = staff`.
- `action = seat_called_queue_ticket`.

Request hash must include normalized command intent:

- `tenantId`
- `storeId`
- `queueTicketId`
- `tableId`
- `tableGroupId`
- `actorId`
- `actorType`
- `overrideReasonCode`
- `overrideNote`
- `note`

Request hash must not include the application-generated `seatedAt`, random IDs, or current time.

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| missing | Start command and execute once. | Not applicable. |
| `completed` | Replay stored result with `replayed = true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` or in progress | Return retry-later application error. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| `expired` | Require new key unless a later retention policy explicitly allows reuse. | `IDEMPOTENCY_CONFLICT`. |

Completed replay must not create duplicate QueueTicket mutations, Reservation mutations, Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, table status updates, or group member status updates.

Failure behavior:

- Validation failures before idempotency start do not need an IdempotencyRecord.
- Failures after idempotency start should mark the record as `failed`.
- V1 failed idempotency requires a new key.

## 19. AlreadySeated Boundary

AlreadySeated is success-like only when durable seating evidence exists.

Condition:

```text
queue_ticket.status = seated
and active seating exists with source_type = queue_ticket and source_id = queueTicketId
and active seating_resource exists for that seating
and related reservation.status = seated
```

Behavior:

- Return success-like result with `alreadySeated = true`.
- Return the existing Seating and SeatingResource details.
- Return actual occupied resource details from the existing active SeatingResource.
- Do not update QueueTicket.
- Do not update Reservation.
- Do not create another Seating.
- Do not create another SeatingResource.
- Do not update table or group member statuses again.
- Do not append duplicate BusinessEvent records.
- Do not append duplicate StateTransitionLog records.
- Do not append duplicate success AuditLog.
- Complete the new idempotency record with the already-seated response snapshot when duplicate detection happens under a new idempotency key.

If QueueTicket is `seated` but evidence is incomplete:

- Return an application-level consistency error.
- Do not create a compensating duplicate Seating in this slice.
- Do not append success events, transition logs, or success audit.

Recommended consistency errors:

- `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING`
- `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE`
- `QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED`

## 20. App Gate Future Boundary

This section is for a later API implementation round. It does not authorize a Controller, DTO, route, permission registry change, migration, seed data, or frontend entry in this round.

Future API must use:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")
```

or the project-equivalent annotation shape.

Rules:

- `app_key` is fixed as `reservation_queue`.
- `permission` is fixed as `queue.seat`.
- Do not create a new app key.
- Do not use `reservation.seat` for this queue-specific seating action.
- Do not use `seating.create` for this queue-specific API.
- Do not use `queue.call`.
- `queue.seat` is not currently present in `AppGateRequiredPermission`.
- Adding `queue.seat` to App Gate permission metadata is a future API/App Gate metadata round, not this contract round.
- This contract does not modify `AppGateRequiredPermission`.
- `tenantId` must come from trusted actor/server context.
- `storeId` must come from path or trusted server context.
- Body `tenantId` must not be trusted.
- App Gate deny happens before the application service runs.
- App Gate deny must not change QueueTicket, Reservation, Seating, SeatingResource, Table, TableGroup, BusinessEvent, StateTransitionLog, AuditLog, or Idempotency business records for this command.
- App Gate deny must write `app_gate_audit_logs`.
- Deny action must be `APP_GATE_DENIED`.

Future `/api/me/apps` note:

- Staff Home button-level visibility for Queue Seat requires a separate approved UI/API capability contract if needed.
- The current app-entry model must not be stretched in this contract.

## 21. Failure Cases

Failures must return stable application-level errors. Raw database exceptions must not cross the application service boundary.

Required failure coverage:

| Case | Application error |
| --- | --- |
| Command is null or missing required fields | `INVALID_COMMAND` |
| Missing idempotency key | `MISSING_IDEMPOTENCY_KEY` |
| Both `tableId` and `tableGroupId` provided | `RESOURCE_SELECTION_CONFLICT` |
| Neither `tableId` nor `tableGroupId` provided | `RESOURCE_SELECTION_REQUIRED` |
| Store not found | `STORE_NOT_FOUND` |
| Store scope mismatch | `STORE_SCOPE_MISMATCH` |
| Store access denied | `STORE_ACCESS_DENIED` |
| QueueTicket not found | `QUEUE_TICKET_NOT_FOUND` |
| QueueTicket belongs to another Store | `STORE_SCOPE_MISMATCH` |
| QueueTicket has no Reservation source | `QUEUE_TICKET_SOURCE_NOT_RESERVATION` |
| QueueTicket status is `called` with complete call evidence | Success path. |
| QueueTicket status is `called` missing call evidence | `QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE` |
| QueueTicket status is `waiting` | `QUEUE_TICKET_STATUS_NOT_CALLED` |
| QueueTicket status is `skipped` | `QUEUE_TICKET_STATUS_NOT_CALLED` |
| QueueTicket status is `rejoined` | `QUEUE_TICKET_STATUS_NOT_CALLED` |
| QueueTicket status is `seated` with complete seating evidence | Success-like `alreadySeated = true`. |
| QueueTicket status is `seated` without matching active Seating | `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING` |
| QueueTicket status is `cancelled` | `QUEUE_TICKET_CANNOT_SEAT_CANCELLED` |
| QueueTicket status is `expired` | `QUEUE_TICKET_CANNOT_SEAT_EXPIRED` |
| Related Reservation not found | `RESERVATION_NOT_FOUND` |
| Related Reservation not `arrived` for fresh seating | `RESERVATION_STATUS_NOT_ARRIVED` |
| Related Reservation already seated while QueueTicket is not seated | `RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED` |
| Table not found | `TABLE_NOT_FOUND` |
| Table not available | `TABLE_NOT_AVAILABLE` |
| Table inactive | `TABLE_NOT_AVAILABLE` or `TABLE_INACTIVE` if a public API later needs specificity |
| Table capacity insufficient | `TABLE_CAPACITY_INSUFFICIENT` |
| Table locked | `TABLE_LOCK_CONFLICT` |
| Table already occupied | `TABLE_NOT_AVAILABLE` |
| Table in cleaning | `TABLE_NOT_AVAILABLE` |
| Active SeatingResource already exists for selected table | `TABLE_NOT_AVAILABLE` |
| TableGroup not found | `TABLE_GROUP_NOT_FOUND` |
| TableGroup invalid | `TABLE_GROUP_INVALID` |
| TableGroup member unavailable | `TABLE_GROUP_MEMBER_UNAVAILABLE` |
| TableGroup capacity insufficient | `TABLE_GROUP_CAPACITY_INSUFFICIENT` |
| Active SeatingResource already exists for selected group | `TABLE_GROUP_INVALID` or `TABLE_NOT_AVAILABLE` according to later error mapper |
| Seating source invalid | `INVALID_SEATING_SOURCE` |
| Seating resource invalid | `INVALID_SEATING_RESOURCE` |
| Idempotency conflict | `IDEMPOTENCY_CONFLICT` |
| Idempotency in progress | `IDEMPOTENCY_IN_PROGRESS` |
| Failed idempotency reused | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| Illegal QueueTicket state transition | `ILLEGAL_STATE_TRANSITION` |
| Illegal Reservation state transition | `ILLEGAL_STATE_TRANSITION` |
| Illegal DiningTable state transition | `ILLEGAL_STATE_TRANSITION` |
| BusinessEvent write failure | `EVENT_WRITE_FAILED` |
| StateTransitionLog write failure | `STATE_TRANSITION_WRITE_FAILED` |
| AuditLog write failure | `AUDIT_WRITE_FAILED` |
| QueueTicket save failure | `PERSISTENCE_ERROR` |
| Reservation save failure | `PERSISTENCE_ERROR` |
| Seating save failure | `PERSISTENCE_ERROR` |
| SeatingResource save failure | `PERSISTENCE_ERROR` |
| Table or group member save failure | `PERSISTENCE_ERROR` |
| Idempotency save/complete failure | `PERSISTENCE_ERROR` |

Failure side-effect rules:

- No failure path performs Queue Skip.
- No failure path performs Queue Rejoin.
- No failure path starts Cleaning.
- No failure path creates Turnover.
- No failure path performs No-show.
- No failure path performs Cancellation.
- No failure path creates API, UI, migration, SQL, App Gate metadata, or seed artifacts.

## 22. Test Contract

Future implementation tests should cover the following. This round does not write tests.

Success:

- Called QueueTicket seats to a single DiningTable.
- Called QueueTicket seats to a TableGroup.
- QueueTicket status becomes `seated`.
- Reservation status becomes `seated`.
- Seating is created with source QueueTicket.
- Seating does not set `reservation_id`.
- SeatingResource is created with status `active`.
- DiningTable becomes `occupied`.
- TableGroup member tables become `occupied`.
- `queue_ticket.seated`, `reservation.seated`, `seating.created`, and `table.occupied` events are written.
- QueueTicket `called -> seated` transition is written.
- Reservation `arrived -> seated` transition is written.
- Seating occupancy transition is written.
- Table or member table occupancy transition is written.
- AuditLog operation `queue.seat` is written.
- Idempotency is completed.

AlreadySeated:

- QueueTicket already `seated` with active Seating source QueueTicket returns `alreadySeated = true`.
- AlreadySeated requires active SeatingResource.
- AlreadySeated requires related Reservation already `seated`.
- AlreadySeated does not duplicate Seating.
- AlreadySeated does not duplicate SeatingResource.
- AlreadySeated does not duplicate events, transitions, audit, QueueTicket changes, Reservation changes, or table changes.

Idempotency:

- Completed replay returns stored result.
- Completed replay does not duplicate Seating.
- Completed replay does not duplicate SeatingResource.
- Completed replay does not duplicate events, transitions, audit, QueueTicket changes, Reservation changes, or table changes.
- In-progress same hash returns retry later.
- Failed same hash requires a new key.
- Same key with different hash returns conflict.

Failure:

- QueueTicket not found.
- QueueTicket not called.
- QueueTicket waiting.
- QueueTicket skipped.
- QueueTicket rejoined.
- QueueTicket cancelled.
- QueueTicket expired.
- QueueTicket called but missing call evidence.
- QueueTicket seated without active Seating.
- QueueTicket source not Reservation.
- Related Reservation not found.
- Related Reservation not arrived.
- Table not found.
- Table not available.
- Table capacity insufficient.
- Table locked.
- TableGroup not found.
- TableGroup invalid.
- TableGroup member unavailable.
- TableGroup capacity insufficient.
- Both `tableId` and `tableGroupId`.
- Neither `tableId` nor `tableGroupId`.
- Event write failure.
- Transition write failure.
- Audit write failure.
- Persistence save failure.

Boundary:

- No Queue Skip behavior.
- No Queue Rejoin behavior.
- No Queue Display Screen behavior.
- No WalkIn queue seating behavior.
- No Auto assignment behavior.
- No table recommendation behavior.
- No Cleaning created.
- No Turnover created.
- No No-show behavior.
- No Cancellation behavior.
- No API implementation.
- No UI implementation.
- No migration change.
- No App Gate metadata change.

Future App Gate API tests:

- Allowed request uses `app_key = reservation_queue` and `permission = queue.seat`.
- Tenant not entitled is denied before business handler.
- Store app disabled is denied before business handler.
- Missing `queue.seat` permission is denied before business handler.
- Deny writes `APP_GATE_DENIED`.
- Deny does not mutate QueueTicket, Reservation, Seating, SeatingResource, Table, TableGroup, events, transitions, audit, or idempotency for this business command.

## 23. Implementation Prerequisites

No code is changed in this contract round. A future implementation round should account for these current gaps:

- `QueueTicket` domain currently has `call(...)` but does not yet expose a `seat(...)` domain helper.
- `DefaultSeatingSourceValidator` has a generic placeholder, but its convenience overload currently accepts only `walk_in`; implementation must supplement it for QueueTicket source without breaking Reservation and WalkIn seating.
- Direct Seating uses `seatings.reservation_id`; this slice must use `seatings.queue_ticket_id`.
- `AppGateRequiredPermission` does not currently include `queue.seat`; this is expected because this round must not modify App Gate metadata.
- Future API path and DTO shape must be designed separately before controller implementation.

## 24. Next Implementation Notes

Recommended next allowed round:

```text
Seating From Called Queue Application Implementation
```

Implementation should:

- Add application tests first.
- Add `SeatCalledQueueTicketCommand`.
- Add `SeatingFromCalledQueueApplicationService`.
- Add result and error types.
- Add or supplement `QueueTicketSeatRule`.
- Add or supplement `SeatingFromCalledQueueRule`.
- Add a QueueTicket domain helper for `called -> seated`.
- Reuse `QueueTicketStateMachine`, `ReservationStateMachine`, `DiningTableStateMachine`, table rules, seating validators, event/audit/transition/idempotency rules, and existing repository ports.
- Keep Queue Skip, Queue Rejoin, Queue Display, Queue list/workbench, WalkIn queue seating, Auto assignment, No-show, Cancellation, Cleaning, Turnover, API, UI, App Gate registry change, migration, SQL, seed data, and production data outside the implementation round unless separately approved.

Future API round should:

- Design endpoint path separately.
- Use `@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")`.
- Add `queue.seat` to App Gate permission metadata only in an approved API/App Gate metadata round.
- Continue ignoring `body.tenantId` for scope.

Future UI round should:

- Design Queue Seat UI separately.
- Keep Queue Skip, Rejoin, Display, list/workbench, and auto assignment out of scope unless explicitly approved.
- Display backend `error.code` and `error.messageKey` if exposed through future API.

## 25. Open Questions

- Should a future WalkIn-sourced called QueueTicket use the same command with `walkInId` evidence, or should it enter through a separate WalkIn Queue Seating contract?
- Should future API expose the queue source as `queueTicketId` only, or also return a read-only `reservationId` for staff diagnostics?
- Should a future Queue Display or workbench call this command by explicit `queueTicketId`, or should it introduce a separate "seat next called ticket" API?

## 26. Open Conflicts

- No schema conflict: current `queue_tickets.status` contains `seated` and does not contain `completed`, so V1 selects `seated`.
- No Seating source conflict: current `seatings` supports `queue_ticket_id`, and the contract avoids setting `reservation_id` at the same time.
- App Gate future conflict: `queue.seat` is the required future permission, but it is not yet present in `AppGateRequiredPermission`. This is an expected future API/App Gate metadata prerequisite, not a contract-round code change.

## 27. Not Created In This Round

- No Java Application Service.
- No repository implementation.
- No Controller.
- No API DTO.
- No Vue page.
- No Vue component.
- No Router entry.
- No Staff Home entry.
- No Flyway migration.
- No SQL file.
- No App Gate Java registry change.
- No permission metadata change.
- No database structure change.
- No Queue Skip.
- No Queue Rejoin.
- No Queue Display Screen.
- No Queue list/workbench.
- No WalkIn queue seating.
- No Auto assignment.
- No No-show.
- No Cancellation.
- No Cleaning.
- No Turnover.
- No seed data.
- No production configuration.
- No production database access.
