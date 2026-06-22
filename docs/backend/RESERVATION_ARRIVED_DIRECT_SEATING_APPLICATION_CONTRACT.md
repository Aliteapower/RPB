# Reservation Arrived Direct Seating Application Contract V1

## 1. Purpose

This document defines the minimum application contract for seating an already arrived Reservation by manual staff resource selection.

Business flow:

```text
Reservation confirmed
-> Reservation CheckIn
-> Reservation status = arrived
-> Store staff selects exactly one DiningTable or TableGroup
-> Seating is created with source = Reservation
-> SeatingResource is created for the selected resource
-> table or TableGroup member tables become occupied
-> Reservation status changes from arrived to seated
-> BusinessEvent, StateTransitionLog, AuditLog, and Idempotency are handled
```

This is a contract-only round. It does not create Java code, repository implementation, controller, API DTO, Vue UI, Flyway migration, SQL, seed data, production configuration, or database data.

## 2. Read Inputs

Reservation Create and CheckIn inputs:

- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CREATE_UI_VALIDATION_REPORT.md`
- `docs/frontend/STORE_STAFF_RESERVATION_CREATE_HANDOFF.md`
- `docs/backend/RESERVATION_CHECKIN_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_CHECKIN_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_CONTRACT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CHECKIN_UI_VALIDATION_REPORT.md`
- `docs/backend/RESERVATION_CHECKIN_LOCAL_RUNTIME_SECURITY_FIX_REPORT.md`

WalkIn Direct Seating and Cleaning inputs:

- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`

App Gate inputs:

- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/backend/APP_GATE_NEW_SLICE_TEMPLATE.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`

Governance, architecture, schema, and migration inputs:

- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

Confirmed baseline:

- Reservation Create has passed implementation and UI validation.
- Reservation Create creates `status = confirmed` without Queue, Seating, TableLock, or ReservationPreassignment side effects.
- Reservation CheckIn has passed API, UI, and local runtime security validation.
- Reservation can currently reach `status = arrived`.
- WalkIn Direct Seating has reusable seating, seating resource, table occupancy, event, transition, audit, and idempotency patterns.
- Cleaning Complete can release an occupied resource back to available.
- App Gate currently protects Reservation Create, Reservation CheckIn, WalkIn Direct Seating, Cleaning Start, and Cleaning Complete under `app_key = reservation_queue`.
- `reservation.seat` is recommended in App Gate handoff material but is not currently present in `AppGateRequiredPermission`.
- This round does not modify migration or App Gate metadata.

## 3. Selected Vertical Slice

Selected slice:

```text
Reservation Arrived Direct Seating
```

Input precondition:

```text
reservation.status = arrived
```

Required output:

```text
reservation.status = seated
seating created
seating_resource created
selected table or TableGroup member tables occupied
business event written
state transition written
audit written
idempotency completed
```

This slice is manual direct seating only. Staff must explicitly choose either a DiningTable or a TableGroup. There is no automatic table assignment, no recommendation ranking, and no queue fallback in this contract.

## 4. Scope

In scope:

- Application command contract for seating an arrived Reservation.
- Store-scoped Reservation lookup by `tenantId`, `storeId`, and `reservationId`.
- Manual resource selection through exactly one of `tableId` or `tableGroupId`.
- Validation that the Reservation belongs to the Store scope.
- Validation that Reservation status is `arrived`.
- Validation that the selected DiningTable or TableGroup is available, in scope, capacity-compatible, not locked, not actively occupied, and not in cleaning.
- Creation of a `seatings` row with source = Reservation.
- Creation of a `seating_resources` row for exactly one selected resource.
- Table or TableGroup member table occupancy.
- Reservation transition:

```text
arrived -> seated
```

- BusinessEvent boundary.
- StateTransitionLog boundary.
- AuditLog boundary.
- App Gate boundary for a later API round.
- Idempotency boundary for `seat_arrived_reservation`.
- Already-seated duplicate behavior.
- Failure and test contract for a later implementation round.

## 5. Non-Scope

Out of scope:

- Queue.
- Reservation Arrived To Queue.
- QueueTicket creation.
- Queue calling.
- Queue rejoin.
- Queue skip.
- Queue group behavior.
- Automatic table assignment.
- Recommended table.
- Table ranking.
- Capacity optimization.
- Drag-and-drop table map.
- No-show.
- Cancellation.
- Reservation list/search/calendar.
- CheckIn creation or CheckInEntity.
- Cleaning start.
- Cleaning completion.
- Turnover creation or BI.
- ReservationPreassignment creation.
- New seating table.
- `reservation_seatings`.
- `reservation_seating_logs`.
- API implementation.
- Controller.
- API DTO.
- Vue page.
- Vue component.
- Repository implementation.
- Java Application Service implementation.
- Flyway migration or SQL change.
- App Gate Java registry modification.
- Production configuration.
- Production database connection.
- Seed data.

## 6. Command Contract

Command:

```text
SeatArrivedReservationCommand
```

Fields:

| Field | Source | Required | Notes |
| --- | --- | ---: | --- |
| `tenantId` | actor/server context | Yes | Trusted Tenant scope. |
| `storeId` | path/server context | Yes | Store operation boundary. |
| `reservationId` | caller/path/application input | Yes | Existing Reservation id. |
| `tableId` | caller/application input | Conditional | Required when `tableGroupId` is absent. |
| `tableGroupId` | caller/application input | Conditional | Required when `tableId` is absent. |
| `idempotencyKey` | caller/header/application input | Yes | Required for this critical command. |
| `actorId` | actor/server context | Yes | Staff actor id. |
| `actorType` | actor/server context | Yes | Expected value aligns with `staff` for this slice. |
| `overrideReasonCode` | caller/application input | No | Optional audit context only in V1. Does not bypass validations. |
| `overrideNote` | caller/application input | No | Optional audit context only in V1. Does not bypass validations. |
| `note` | caller/application input | No | Optional staff note for Seating/audit metadata. |

Structural rules:

- `tenantId` is required.
- `storeId` is required.
- `reservationId` is required.
- `idempotencyKey` is required.
- `actorId` is required.
- `actorType` is required.
- Exactly one of `tableId` or `tableGroupId` is required.
- `tableId` and `tableGroupId` must not both be present.
- `tableId` and `tableGroupId` must not both be absent.
- `tenantId`, `actorId`, `actorType`, role, permission, and Store scope must come from trusted server context in a later API layer.
- `overrideReasonCode` and `overrideNote` are optional because this slice has no automatic recommendation to override. If a future recommendation/preassignment override path is added, an override reason or note must become mandatory for that future path.
- Override fields must never bypass Store scope, Reservation status, resource availability, capacity, lock, active occupancy, or TableGroup validity checks.

Forbidden command fields:

- `queueTicketId`
- `walkInId`
- `checkInAt`
- `noShowAt`
- `cancelledAt`
- `cleaningId`
- `turnoverId`
- trusted `role`
- trusted `permission`
- trusted `status`

## 7. Application Result Contract

Recommended application result:

```text
ReservationArrivedDirectSeatingResult
```

Success fields should include at least:

- `success = true`
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
- `events`
- `idempotency.status`
- `idempotency.replayed`
- `alreadySeated`

Failure fields should include at least:

- `success = false`
- `error`
- `idempotency.status`
- `retryLater` when applicable

Terminology note:

- The persisted `seatings.status` in V001 is `occupied` for an active occupancy.
- The persisted `seating_resources.status` is `active`.
- Product wording "seating active" maps to this pair: Seating occupancy is `occupied`, and SeatingResource is `active`.

## 8. Application Service Boundary

Application service:

```text
ReservationArrivedDirectSeatingApplicationService
```

Method:

```text
seatArrivedReservation(SeatArrivedReservationCommand command)
```

Responsibilities:

1. Validate command presence and required fields.
2. Validate `tableId` / `tableGroupId` exactly-one rule.
3. Build `StoreScope` from `tenantId` and `storeId`.
4. Check idempotency using action `seat_arrived_reservation`.
5. Validate Store exists and actor can access the Store.
6. Load Reservation by `StoreScope + reservationId`.
7. Validate Reservation belongs to Store scope.
8. Validate Reservation status is `arrived`.
9. Detect already-seated duplicate behavior.
10. Resolve selected DiningTable or TableGroup by Store scope.
11. Validate resource availability.
12. Validate resource capacity fits `reservation.partySize`.
13. Validate active lock conflict does not exist.
14. Validate active SeatingResource occupancy does not already exist for the selected resource.
15. Validate active Cleaning does not block the selected resource.
16. Validate TableGroup membership and member table availability when `tableGroupId` is used.
17. Validate Seating source XOR: Reservation present, QueueTicket absent, WalkIn absent.
18. Validate SeatingResource target XOR: exactly one table or group target.
19. Create Seating with source = Reservation and party size snapshot from Reservation.
20. Create SeatingResource for exactly one selected resource.
21. Update selected DiningTable or TableGroup member tables to `occupied`.
22. Transition Reservation from `arrived` to `seated`.
23. Write required BusinessEvent records.
24. Write required StateTransitionLog records.
25. Write required AuditLog record.
26. Complete idempotency with a replayable response snapshot.
27. Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI message text.
- Queue creation.
- Queue ordering.
- CheckIn creation.
- Cleaning start.
- Cleaning completion.
- No-show.
- Cancellation.
- Automatic table assignment.
- Reservation list/search/calendar.
- Migration or SQL design.
- App Gate metadata modification.

Transaction guidance:

- Fresh successful seating should commit Reservation save, Seating save, SeatingResource save, resource status updates, BusinessEvent, StateTransitionLog, AuditLog, and idempotency completion atomically.
- Validation should happen before mutation whenever possible.
- If event, transition, audit, resource persistence, Reservation persistence, Seating persistence, or idempotency completion fails, the application must return a stable application-level error and must not expose raw database exceptions.
- Audit write failure is blocking because seating is a critical operation.
- Completed idempotency replay must not append duplicate events, transitions, audit logs, Seating, SeatingResource, or resource status changes.

## 9. Required Ports

Required ports:

| Port | Required capability for this slice |
| --- | --- |
| `StoreRepositoryPort` | Load Store by `StoreScope`, validate Store existence and operational scope. |
| `ReservationRepositoryPort` | Load Reservation by `StoreScope + reservationId`, save `arrived -> seated`, and support source lookup for already-seated detection. |
| `DiningTableRepositoryPort` | Load selected table, save table status, and load member tables for TableGroup targets. |
| `TableGroupRepositoryPort` | Load selected group, load active members, validate group scope and status, save temporary group status only if policy requires it. |
| `TableLockRepositoryPort` | Check active lock conflict for the selected resource and member tables. |
| `SeatingRepositoryPort` | Find active seating by Reservation source, check active resource occupancy, save Seating, and save SeatingResource. |
| `BusinessEventRepositoryPort` | Append `reservation.seated`, `seating.created`, and `table.occupied`. |
| `StateTransitionLogRepositoryPort` | Append Reservation, Seating, table, and TableGroup/member table transition evidence. |
| `AuditLogRepositoryPort` | Append success and required failure audit records. |
| `IdempotencyRepositoryPort` | Start, replay, complete, fail, and detect conflict for `seat_arrived_reservation`. |

Forbidden ports:

- `QueueTicketRepositoryPort`
- `CleaningRepositoryPort`
- `TurnoverRepositoryPort`
- broad BI/reporting repositories
- mechanical CRUD ports unrelated to this command

Cleaning note:

- This contract does not use `CleaningRepositoryPort` as an application dependency.
- If a future implementation needs to detect active cleaning for resource availability, that detection should be exposed through an existing reusable table availability or seating/resource occupancy boundary rather than turning this slice into Cleaning orchestration.

## 10. Required Rules / Policies / Validators

Reuse existing rules and validators where possible:

| Component | Purpose |
| --- | --- |
| `StoreAccessPolicy` | Actor must have Tenant and Store scope access. |
| `TableAvailabilityRule` | Selected resource and member tables must be available for seating. |
| `TableCapacityRule` | Reservation party size must fit selected resource capacity. |
| `TableLockRule` | Existing active lock must block conflicting seating. |
| `TableAssignmentRule` | Reuse only resource selection validation helpers. Do not use automatic assignment. |
| `TableGroupValidationRule` | Validate group status, active members, member Store scope, and member availability. |
| `SeatingSourceValidator` | Accept exactly Reservation source, reject QueueTicket and WalkIn source for this slice. |
| `SeatingResourceValidator` | Accept exactly one DiningTable or TableGroup target. |
| `DiningTableStateMachine` | Validate table movement into `occupied` through the existing table transition style. |
| `AuditRule` | Require and shape `reservation.seat` audit. |
| `BusinessEventRule` | Require and validate event codes and targets. |
| `StateTransitionRule` | Require and validate transition evidence. |
| `IdempotencyRule` | Apply replay, in-progress, failed-key, and conflict behavior. |

New or supplemented rules:

| Component | Purpose |
| --- | --- |
| `ReservationArrivedSeatingRule` | Accept only `arrived` for fresh seating, define already-seated behavior, and reject terminal states. |
| `ReservationStateMachine` | Validate `arrived -> seated`. |

Forbidden components:

- `QueueCallingRule`
- `NoShowPolicy`
- `CancellationPolicy`
- `AutoAssignmentPolicy`
- new Queue ordering policy
- new Turnover calculation rule

## 11. State Boundary

Reservation:

```text
arrived -> seated
```

Fresh success must not allow:

- `draft -> seated`
- `confirmed -> seated`
- `cancelled -> seated`
- `no_show -> seated`
- `completed -> seated`

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

An implementation may record the existing WalkIn-style internal evidence:

```text
available -> locked -> occupied
```

when it uses a lock as an in-transaction concurrency guard. The final business outcome remains `occupied`.

TableGroup:

- Fixed TableGroup remains a configuration resource and should not be mutated to pretend configuration equals occupancy.
- Occupancy for fixed TableGroup is represented by SeatingResource, member table statuses, and transition evidence.
- Temporary TableGroup may move toward `occupied` when its status model requires it.
- Member DiningTables used by a TableGroup must become `occupied` and must have transition evidence.

No state transition in this slice starts Cleaning or Turnover.

## 12. Event / Audit Boundary

Minimum required BusinessEvent codes:

| Event type | Target type | Target id | Notes |
| --- | --- | --- | --- |
| `reservation.seated` | `reservation` | Reservation id | Reservation arrived direct seating succeeded. |
| `seating.created` | `seating` | Seating id | Seating source is Reservation. |
| `table.occupied` | `dining_table` or `table_group` | Selected resource id | Resource is occupied by the Seating. |

Optional implementation evidence, only if the reused seating flow acquires a durable lock:

- `table.locked`

Minimum required AuditLog operation:

```text
reservation.seat
```

Required audit metadata:

- `tenantId`
- `storeId`
- `reservationId`
- `reservationCode`
- `seatingId`
- `resourceType`
- `tableId` or `tableGroupId`
- `partySizeSnapshot`
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

Failure audit operation:

```text
reservation.seat.failed
```

Failure audit is best effort and must not mask the original application error, except that audit write failure for an accepted business mutation remains blocking.

## 13. StateTransitionLog Boundary

Fresh success must record at least:

| Target type | From | To | Transition code |
| --- | --- | --- | --- |
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

## 14. Seating Source / Resource Boundary

Seating source must use the existing `seatings` table:

```text
reservation_id = reservationId
queue_ticket_id = null
walk_in_id = null
```

Forbidden:

- `reservation_seatings`
- `reservation_seating_logs`
- new seating table
- Seating sourced from QueueTicket in this slice
- Seating sourced from WalkIn in this slice

Seating fields:

- `tenant_id` and `store_id` must match the Reservation and selected resource.
- `party_size_snapshot` must equal the Reservation party size at seating time.
- `status` should be `occupied` for the accepted V1 direct seating result.
- `seated_at` should be the application current UTC instant.
- `manual_override_reason_code` may store `overrideReasonCode` when supplied.
- `note` may store `note` or `overrideNote` according to the later implementation mapping, without losing audit metadata.

SeatingResource must use the existing `seating_resources` table:

```text
resource_type = dining_table | table_group
table_id = selected table id when resource_type = dining_table
table_group_id = selected group id when resource_type = table_group
status = active
```

`seating_resources` active uniqueness must prevent duplicate active occupancy for the same selected resource.

## 15. Table / TableGroup Boundary

DiningTable target rules:

- `tableId` must belong to the same Tenant and Store.
- Table must exist.
- Table must not be deleted.
- Table status must be `available`.
- Table must not be `locked`, `reserved`, `occupied`, `cleaning`, or `inactive`.
- Table capacity must fit Reservation party size.
- Table must not have an active lock conflict.
- Table must not have active SeatingResource occupancy.
- Table becomes `occupied` after successful Seating.

TableGroup target rules:

- `tableGroupId` must belong to the same Tenant and Store.
- Group must exist.
- Group must not be deleted or inactive.
- Fixed group must be `active`.
- Temporary group must be in a state allowed for seating and not released or ended.
- Group capacity must fit Reservation party size.
- Group must have valid active members.
- Every member DiningTable must belong to the same Tenant and Store.
- Every member DiningTable must be available and not locked, occupied, cleaning, inactive, or already used by another active effective group.
- Group must not have active SeatingResource occupancy.
- Group or member tables must not have active lock conflicts.
- Member DiningTables become `occupied` after successful Seating.
- Fixed TableGroup configuration status should not be mutated solely to represent occupancy.
- Temporary TableGroup status may become `occupied` if the implementation supports that lifecycle.

## 16. App Gate Future Boundary

This section is for a later API implementation round. It does not authorize a Controller, DTO, route, permission registry change, migration, or frontend entry in this round.

Future API must use:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")
```

or the project-equivalent annotation shape.

Rules:

- `app_key` is fixed as `reservation_queue`.
- `permission` is fixed as `reservation.seat`.
- Do not create a new app key.
- Do not use `reservation.direct_seat`.
- Do not use `seating.reservation.create`.
- Do not use `reservation.arrived.seat`.
- `reservation.seat` is a future permission to add to the `reservation_queue` permission set.
- This contract does not modify `AppGateRequiredPermission`.
- `tenantId` must come from trusted actor/server context.
- `storeId` must come from path or trusted server context.
- Body `tenantId` must not be trusted.
- App Gate deny happens before the application service runs.
- App Gate deny must not change Reservation, Seating, SeatingResource, Table, TableGroup, BusinessEvent, StateTransitionLog, AuditLog, or Idempotency business records for this seating command.
- App Gate deny must write `app_gate_audit_logs`.
- Deny action must be `APP_GATE_DENIED`.

Future `/api/me/apps` note:

- Button-level visibility for Reservation Seating must be designed in a separate UI/API contract if needed.
- The current app-entry model must not be stretched in this contract.

## 17. Idempotency Boundary

Action:

```text
seat_arrived_reservation
```

Idempotency scope:

```text
tenant_id + store_id + source + action + idempotency_key
```

For this slice:

- `source = staff`
- `action = seat_arrived_reservation`

Request hash must include normalized command intent:

- `tenantId`
- `storeId`
- `reservationId`
- `tableId`
- `tableGroupId`
- `actorId`
- `actorType`
- `overrideReasonCode`
- `overrideNote`
- `note`

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| missing | Start command and execute once. | Not applicable. |
| `completed` | Replay stored result with `replayed = true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` or in progress | Return retry-later application error. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| `expired` | Require new key unless a later retention policy explicitly allows reuse. | `IDEMPOTENCY_CONFLICT`. |

Completed replay must not create duplicate Reservation mutations, Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, table status updates, or group member status updates.

## 18. Already Seated Behavior

If Reservation is already `seated`:

Same completed idempotency key:

- Replay the previous result.
- `idempotency.replayed = true`.
- `alreadySeated` should reflect the stored response.
- No mutation.
- No duplicate event, transition, or audit.

Different idempotency key:

- Load existing active Seating by Reservation source.
- Return success-like result with `alreadySeated = true` only when the existing Seating source is the same Reservation and the active SeatingResource belongs to the same Store scope.
- Complete the new idempotency record with the already-seated response snapshot.
- Do not create another Seating.
- Do not create another SeatingResource.
- Do not update table/group statuses again.
- Do not append duplicate `reservation.seated`, `seating.created`, `table.occupied`, transition logs, or success audit.

If Reservation is `seated` but no matching active Seating source can be found:

- Return an application-level consistency error.
- Do not create a compensating duplicate Seating in this slice.

Recommended error code:

```text
RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING
```

## 19. Failure Cases

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
| Reservation not found | `RESERVATION_NOT_FOUND` |
| Reservation belongs to another Store | `STORE_SCOPE_MISMATCH` |
| Reservation status is not `arrived` | `RESERVATION_STATUS_NOT_ARRIVED` |
| Reservation already seated with matching active Seating | success-like `alreadySeated = true` |
| Reservation already seated without matching active Seating | `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING` |
| Reservation cancelled | `RESERVATION_CANNOT_SEAT_CANCELLED` |
| Reservation no_show | `RESERVATION_CANNOT_SEAT_NO_SHOW` |
| Reservation completed | `RESERVATION_CANNOT_SEAT_COMPLETED` |
| Reservation draft or confirmed | `RESERVATION_STATUS_NOT_ARRIVED` |
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
| Idempotency conflict | `IDEMPOTENCY_CONFLICT` |
| Idempotency in progress | `IDEMPOTENCY_IN_PROGRESS` |
| Failed idempotency reused | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| Illegal state transition | `ILLEGAL_STATE_TRANSITION` |
| BusinessEvent write failure | `EVENT_WRITE_FAILED` |
| StateTransitionLog write failure | `STATE_TRANSITION_WRITE_FAILED` |
| AuditLog write failure | `AUDIT_WRITE_FAILED` |
| Seating save failure | `PERSISTENCE_ERROR` |
| SeatingResource save failure | `PERSISTENCE_ERROR` |
| Reservation save failure | `PERSISTENCE_ERROR` |
| Table or group member save failure | `PERSISTENCE_ERROR` |

Failure side-effect rules:

- Validation failures before idempotency start do not need an IdempotencyRecord.
- Failures after idempotency start should mark the record as `failed`.
- V1 failed idempotency requires a new key.
- No failure path creates QueueTicket, Cleaning, Turnover, No-show, Cancellation, API, UI, migration, or new seating table artifacts.

## 20. Test Contract

Future implementation tests should cover the following. This round does not write tests.

Success:

- Arrived Reservation seats to a single DiningTable.
- Arrived Reservation seats to a TableGroup.
- Reservation becomes `seated`.
- Seating is created with source Reservation.
- SeatingResource is created with status `active`.
- DiningTable becomes `occupied`.
- TableGroup member tables become `occupied`.
- `reservation.seated`, `seating.created`, and `table.occupied` events are written.
- Reservation `arrived -> seated` transition is written.
- Table or member table occupancy transition is written.
- AuditLog operation `reservation.seat` is written.
- Idempotency is completed.

Idempotency:

- Completed replay returns stored result.
- Completed replay does not duplicate Seating.
- Completed replay does not duplicate SeatingResource.
- Completed replay does not duplicate events, transitions, audit, or table changes.
- In-progress same hash returns retry later.
- Failed same hash requires a new key.
- Same key with different hash returns conflict.
- Already seated with new key returns `alreadySeated = true` only when existing Seating source matches the Reservation.
- Already seated with new key does not duplicate Seating or business evidence.

Failure:

- Reservation not found.
- Reservation not `arrived`.
- Reservation cancelled.
- Reservation no_show.
- Reservation completed.
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

- No QueueTicket created.
- No Cleaning created.
- No Turnover created.
- No No-show behavior.
- No Cancellation behavior.
- No Reservation list/search/calendar behavior.
- No new seating table.
- No migration change.
- No API implementation.
- No UI implementation.

Future App Gate API tests:

- Allowed request uses `app_key = reservation_queue` and `permission = reservation.seat`.
- Tenant not entitled is denied before business handler.
- Store app disabled is denied before business handler.
- Missing `reservation.seat` permission is denied before business handler.
- Deny writes `APP_GATE_DENIED`.
- Deny does not mutate Reservation, Seating, SeatingResource, Table, TableGroup, events, transitions, audit, or idempotency for this business command.

## 21. Next Implementation Notes

Recommended next allowed round:

```text
Reservation Arrived Direct Seating Application Implementation
```

Implementation should:

- Add application tests first.
- Reuse WalkIn Direct Seating seating/resource/event/transition/audit/idempotency patterns.
- Reuse Reservation CheckIn duplicate success-like behavior for already-seated handling.
- Add `ReservationArrivedSeatingRule` only for Reservation-specific status validation.
- Reuse `ReservationStateMachine` for `arrived -> seated`.
- Reuse `DiningTableStateMachine`, `TableAvailabilityRule`, `TableCapacityRule`, `TableLockRule`, `TableGroupValidationRule`, `SeatingSourceValidator`, and `SeatingResourceValidator`.
- Keep automatic assignment disabled.
- Keep Queue, No-show, Cancellation, Cleaning, Turnover, API, UI, App Gate registry change, migration, SQL, seed data, and production data outside the implementation round unless separately approved.

Future API round should:

- Design the endpoint separately.
- Use `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")`.
- Add `reservation.seat` to App Gate permission metadata only in an approved metadata/code round.
- Update local runtime security allowlist only in an approved API/runtime round.

## 22. Not Created In This Round

- No Java Application Service.
- No Repository implementation.
- No Controller.
- No API DTO.
- No Vue page.
- No Vue component.
- No Flyway migration.
- No SQL file.
- No App Gate Java registry change.
- No database structure change.
- No Queue.
- No No-show.
- No Cancellation.
- No Cleaning.
- No Turnover.
- No seed data.
- No production configuration.
- No production database access.
