# Reservation Arrived To Queue Application Contract V1

## 1. Purpose

This document defines the minimum application contract for placing an already arrived Reservation into the waiting Queue when the store staff does not seat the party directly.

Business flow:

```text
Reservation confirmed
-> Reservation CheckIn
-> Reservation status = arrived
-> Store staff chooses the waiting branch
-> QueueTicket is created with source = Reservation
-> QueueTicket status = waiting
-> Reservation status remains arrived
-> BusinessEvent, QueueTicket transition evidence, AuditLog, and Idempotency are handled
```

This is a contract-only round. It does not create Java code, repository implementation, controller, API DTO, Vue UI, router entry, App Gate metadata, Flyway migration, SQL, seed data, production configuration, or database data.

## 2. Read Inputs

Reservation main-chain inputs:

- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_VALIDATION_REPORT.md`

Queue, schema, and domain inputs:

- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/domain/QueueGroup.java`
- `src/main/java/com/rpb/reservation/queue/status/QueueTicketStatus.java`
- `src/main/java/com/rpb/reservation/queue/state/QueueTicketStateMachine.java`
- `src/main/java/com/rpb/reservation/queue/application/port/out/QueueTicketRepositoryPort.java`
- `src/main/java/com/rpb/reservation/queue/policy/QueueGroupPolicy.java`
- `src/main/java/com/rpb/reservation/queue/policy/QueueOrderingPolicy.java`
- `src/main/java/com/rpb/reservation/queue/command/CreateQueueTicketCommand.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueTicketEntity.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueGroupEntity.java`

Reservation domain inputs:

- `src/main/java/com/rpb/reservation/reservation/domain/Reservation.java`
- `src/main/java/com/rpb/reservation/reservation/status/ReservationStatus.java`
- `src/main/java/com/rpb/reservation/reservation/state/ReservationStateMachine.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`

WalkIn, seating, and contrast inputs:

- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`

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

- Reservation Create has passed implementation and runtime/UI validation.
- Reservation Create currently creates `status = confirmed` without Queue, Seating, TableLock, or ReservationPreassignment side effects.
- Reservation CheckIn has passed API, UI, and local runtime security validation.
- Reservation can currently reach `status = arrived`.
- Reservation Arrived Direct Seating has passed API/UI/local validation and supports `arrived -> seated`.
- Today View has passed UI validation and is read-only.
- Queue schema already exists through `queue_groups` and `queue_tickets`.
- V001 `reservations.status` does not contain `queued`.
- V001 `queue_tickets` already contains `reservation_id` and `walk_in_id`.
- V001 `queue_tickets` does not contain `source_type`.
- This round does not modify migration or App Gate metadata.

## 3. Selected Vertical Slice

Selected slice:

```text
Reservation Arrived To Queue
```

Input precondition:

```text
reservation.status = arrived
```

Required output:

```text
reservation.status remains arrived
queue_ticket created
queue_ticket.reservation_id = reservationId
queue_ticket.walk_in_id = null
queue_ticket.status = waiting
business event written
queue_ticket transition evidence written when transition logging is used for creation
audit written
idempotency completed
```

This slice is the no-table branch after Reservation arrival. It does not seat the party, does not call the queue ticket, and does not assign or lock a table.

## 4. Scope

In scope:

- Application command contract for queueing an arrived Reservation.
- Store-scoped Reservation lookup by `tenantId`, `storeId`, and `reservationId`.
- Validation that the Reservation belongs to the Store scope.
- Validation that Reservation status is `arrived`.
- Selection or derivation of a QueueGroup from Reservation party size.
- Creation of one QueueTicket with source = Reservation.
- QueueTicket initial status:

```text
waiting
```

- Reservation status decision:

```text
arrived stays arrived
```

- QueueTicket number generation by backend policy.
- QueueTicket ordering/positioning at the tail of the selected QueueGroup for the business date.
- BusinessEvent boundary.
- StateTransitionLog boundary for QueueTicket creation evidence.
- AuditLog boundary.
- App Gate boundary for a later API round.
- Idempotency boundary for `queue_arrived_reservation`.
- Already-queued duplicate behavior.
- Failure and test contract for a later implementation round.

## 5. Non-Scope

Out of scope:

- Queue Call.
- Queue Skip.
- Queue Rejoin.
- Queue notification.
- Queue display board.
- Seating.
- Reservation Arrived Direct Seating.
- Table assignment.
- Table lock.
- DiningTable mutation.
- TableGroup occupancy mutation.
- Cleaning.
- Turnover.
- No-show.
- Cancellation.
- Reservation list/search/calendar.
- Table map.
- Auto assignment.
- Recommendation ranking.
- CheckIn creation.
- API implementation.
- Controller.
- API DTO.
- Vue page.
- Vue component.
- Router entry.
- Repository implementation.
- Java Application Service implementation.
- Flyway migration or SQL change.
- App Gate Java registry modification.
- Permission metadata modification.
- Production configuration.
- Production database connection.
- Seed data.

## 6. Existing Schema / Source Decision

QueueTicket must use the existing V001 table:

```text
queue_tickets
```

Source mapping for this slice:

```text
queue_tickets.reservation_id = reservationId
queue_tickets.walk_in_id = null
```

Rules:

- Do not add `source_type`.
- Do not create a new queue source table.
- Do not create a new reservation queue mapping table.
- Do not use `walk_in_id` for this slice.
- `tenant_id` and `store_id` on QueueTicket must match the Reservation and QueueGroup.
- `customer_id`, when available on the Reservation, should be copied to QueueTicket for lookup and display.
- `party_size` must come from the Reservation party size snapshot at queue time.
- `note` may store staff note when provided.

The existing V001 constraint allows at most one of `reservation_id` and `walk_in_id`. This slice must populate only `reservation_id`.

## 7. Reservation Status Decision

Reservation does not move to a new status in this slice.

Required status behavior:

```text
before: reservation.status = arrived
after:  reservation.status = arrived
```

Rules:

- Do not add `queued` to `ReservationStatus`.
- Do not modify `ReservationStateMachine` for `arrived -> queued`.
- Do not write a Reservation state transition log for `arrived -> queued`.
- Queue waiting state is represented by QueueTicket, not by Reservation.
- A Reservation may later be seated from the QueueTicket in a separate future slice.
- Terminal Reservation states must not be queued.

Forbidden Reservation transitions in this slice:

```text
draft -> queued
confirmed -> queued
arrived -> queued
arrived -> seated
cancelled -> queued
no_show -> queued
completed -> queued
```

## 8. QueueGroup Decision

QueueGroup is Store-scoped and party-size based.

Default QueueGroup bands:

```text
1-2
3-4
5-6
7+
```

Selection rules:

- If `partySizeGroup` is absent, derive QueueGroup from Reservation party size.
- If `partySizeGroup` is present, treat it as a requested group code or logical group identifier and validate it against Store-scoped active QueueGroup records.
- Explicit `partySizeGroup` must still be compatible with Reservation party size.
- QueueGroup must belong to the same Tenant and Store.
- QueueGroup must be active.
- QueueGroup must not be deleted.
- QueueGroup min/max party size must cover the Reservation party size.
- If no QueueGroup can be derived or validated, return a stable application-level error.

This contract does not add seed data or migration for default QueueGroups. Later implementation may rely on existing seeded/configured QueueGroups or a separately approved setup round.

## 9. QueueTicket Number / Position Decision

QueueTicket number is generated by backend policy.

Persisted field:

```text
queue_tickets.ticket_number integer
```

Generation scope:

```text
tenant_id + store_id + queue_group_id + business_date
```

Rules:

- The caller must not provide `ticketNumber`.
- The command must not trust a client-generated queue number.
- The backend should generate the next positive integer for the selected Store, QueueGroup, and business date.
- Generation must respect the V001 uniqueness constraint on `tenant_id + store_id + queue_group_id + business_date + ticket_number`.
- Concurrency must be handled so two simultaneous queue requests do not create the same ticket number.
- User-facing formatting such as `Q-YYYYMMDD-XXXX` is a future presentation/API decision and must not replace the persisted integer in this contract.
- Queue position should be assigned at the tail of the selected QueueGroup for the same business date.
- This contract does not call, skip, rejoin, seat, cancel, or expire the ticket.

Recommended policy names:

- `QueueTicketNumberPolicy`
- `QueueOrderingPolicy`

## 10. Business Date Decision

QueueTicket `business_date` must be deterministic and Store-scoped.

V1 rule:

```text
queue_ticket.business_date = reservation.businessDate
```

Validation guidance:

- Reservation must belong to the target Store.
- Reservation must be in `arrived`.
- If later Store operating-day rules distinguish calendar date from business date, that rule must be added in a separate implementation design.
- This contract does not create a Store business-day calendar service.

## 11. Command Contract

Command:

```text
QueueArrivedReservationCommand
```

Fields:

| Field | Source | Required | Notes |
| --- | --- | ---: | --- |
| `tenantId` | actor/server context | Yes | Trusted Tenant scope. |
| `storeId` | path/server context | Yes | Store operation boundary. |
| `reservationId` | caller/path/application input | Yes | Existing Reservation id. |
| `idempotencyKey` | caller/header/application input | Yes | Required for this critical command. |
| `actorId` | actor/server context | Yes | Staff actor id. |
| `actorType` | actor/server context | Yes | Expected value aligns with `staff` for this slice. |
| `partySizeGroup` | caller/application input | No | Optional requested QueueGroup code/logical band. Must be validated. |
| `reasonCode` | caller/application input | No | Optional audit context for why waiting branch was chosen. |
| `note` | caller/application input | No | Optional staff note. |

Structural rules:

- `tenantId` is required.
- `storeId` is required.
- `reservationId` is required.
- `idempotencyKey` is required.
- `actorId` is required.
- `actorType` is required.
- `partySizeGroup` is optional and must never override Store scope or party-size compatibility.
- `tenantId`, `actorId`, `actorType`, role, permission, and Store scope must come from trusted server context in a later API layer.
- Body `tenantId` must not be trusted in a later API layer.

Forbidden command fields:

- `queueTicketId`
- `queueTicketNumber`
- `ticketNumber`
- `tableId`
- `tableGroupId`
- `seatingId`
- `walkInId`
- `checkInAt`
- `calledAt`
- `skippedAt`
- `rejoinedAt`
- `seatedAt`
- `noShowAt`
- `cancelledAt`
- `cleaningId`
- `turnoverId`
- trusted `role`
- trusted `permission`
- trusted `status`

## 12. Application Result Contract

Recommended application result:

```text
ReservationArrivedToQueueResult
```

Success fields should include at least:

- `success = true`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = waiting`
- `queueGroupId`
- `queueGroupCode`
- `partySize`
- `partySizeGroup`
- `businessDate`
- `queuePosition`
- `events`
- `idempotency.status`
- `idempotency.replayed`
- `alreadyQueued`

Failure fields should include at least:

- `success = false`
- `error`
- `idempotency.status`
- `retryLater` when applicable

Terminology note:

- Product wording "arrived to queue" maps to Reservation staying `arrived` and QueueTicket becoming `waiting`.
- `alreadyQueued = true` is a success-like duplicate result and must not create a second QueueTicket.

## 13. Application Service Boundary

Application service:

```text
ReservationArrivedToQueueApplicationService
```

Method:

```text
queueArrivedReservation(QueueArrivedReservationCommand command)
```

Responsibilities:

1. Validate command presence and required fields.
2. Build `StoreScope` from `tenantId` and `storeId`.
3. Check idempotency using action `queue_arrived_reservation`.
4. Validate Store exists and actor can access the Store.
5. Load Reservation by `StoreScope + reservationId`.
6. Validate Reservation belongs to Store scope.
7. Validate Reservation status is `arrived`.
8. Detect already-queued duplicate behavior.
9. Select active QueueGroup by Reservation party size or validated `partySizeGroup`.
10. Generate backend QueueTicket number for the selected Store, QueueGroup, and business date.
11. Assign QueueTicket position at the tail of the selected QueueGroup.
12. Create QueueTicket with source = Reservation.
13. Set QueueTicket initial status to `waiting`.
14. Keep Reservation status as `arrived`.
15. Write required BusinessEvent records.
16. Write required StateTransitionLog records for QueueTicket creation evidence when the project records initial state transitions.
17. Write required AuditLog record.
18. Complete idempotency with a replayable response snapshot.
19. Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI message text.
- Queue calling.
- Queue skip.
- Queue rejoin.
- Queue notification.
- Seating.
- Table assignment.
- Table lock.
- Cleaning start.
- Cleaning completion.
- No-show.
- Cancellation.
- Reservation list/search/calendar.
- Migration or SQL design.
- App Gate metadata modification.

Transaction guidance:

- Fresh successful queue creation should commit QueueTicket save, BusinessEvent, StateTransitionLog if used, AuditLog, and idempotency completion atomically.
- Reservation may be saved only if implementation needs metadata such as `updated_at`; it must not change status.
- Validation should happen before mutation whenever possible.
- If event, transition, audit, QueueTicket persistence, or idempotency completion fails, the application must return a stable application-level error and must not expose raw database exceptions.
- Audit write failure is blocking because queueing an arrived Reservation is a critical operation.
- Completed idempotency replay must not append duplicate events, transitions, audit logs, QueueTickets, or Reservation metadata updates.

## 14. Required Ports

Required ports:

| Port | Required capability for this slice |
| --- | --- |
| `StoreRepositoryPort` | Load Store by `StoreScope`, validate Store existence and operational scope. |
| `ReservationRepositoryPort` | Load Reservation by `StoreScope + reservationId`, validate status and party size snapshot. |
| `QueueGroupRepositoryPort` | Load active Store-scoped QueueGroup by group code or party size band. Create in a future implementation round if absent. |
| `QueueTicketRepositoryPort` | Find active source ticket by Reservation, generate/reserve next ticket number or support the number policy, save QueueTicket. |
| `BusinessEventRepositoryPort` | Append `reservation.queued` and `queue_ticket.created`. |
| `StateTransitionLogRepositoryPort` | Append QueueTicket creation transition evidence when used by the project. |
| `AuditLogRepositoryPort` | Append success and required failure audit records. |
| `IdempotencyRepositoryPort` | Start, replay, complete, fail, and detect conflict for `queue_arrived_reservation`. |

Forbidden ports:

- `DiningTableRepositoryPort`
- `TableGroupRepositoryPort` for seating/table composition behavior
- `TableLockRepositoryPort`
- `SeatingRepositoryPort`
- `CleaningRepositoryPort`
- `TurnoverRepositoryPort`
- broad BI/reporting repositories
- mechanical CRUD ports unrelated to this command

QueueGroup naming note:

- `QueueGroupRepositoryPort` is distinct from any seating `TableGroupRepositoryPort`.
- QueueGroup is a waiting-line grouping policy, not a physical dining table group.

## 15. Required Rules / Policies / Validators

Reuse existing rules and validators where possible:

| Component | Purpose |
| --- | --- |
| `StoreAccessPolicy` | Actor must have Tenant and Store scope access. |
| `ReservationStateMachine` | Confirm that no Reservation transition is made in this slice and reject unsupported statuses. |
| `QueueTicketStateMachine` | Confirm initial QueueTicket lifecycle begins at `waiting`. |
| `QueueGroupPolicy` | Select or validate QueueGroup by Store and party size. |
| `QueueOrderingPolicy` | Assign QueueTicket position within Store, QueueGroup, and business date. |
| `AuditRule` | Require and shape `reservation.queue` audit. |
| `BusinessEventRule` | Require and validate event codes and targets. |
| `StateTransitionRule` | Require and validate QueueTicket creation transition evidence when used. |
| `IdempotencyRule` | Apply replay, in-progress, failed-key, and conflict behavior. |

New or supplemented rules:

| Component | Purpose |
| --- | --- |
| `ReservationArrivedToQueueRule` | Accept only `arrived`, define already-queued behavior, reject terminal states, and keep Reservation status unchanged. |
| `QueueTicketNumberPolicy` | Generate backend ticket number without trusting caller-provided numbers. |
| `QueueGroupSelectionRule` | Derive Store QueueGroup from Reservation party size or validate optional `partySizeGroup`. |

Forbidden components:

- `QueueCallingRule`
- `QueueSkipRule`
- `QueueRejoinRule`
- `SeatingSourceValidator`
- `SeatingResourceValidator`
- `TableAvailabilityRule`
- `TableCapacityRule`
- `TableLockRule`
- `NoShowPolicy`
- `CancellationPolicy`
- `AutoAssignmentPolicy`
- `TurnoverPolicy`

## 16. State Boundary

Reservation:

```text
arrived -> arrived
```

Fresh success must not allow:

- `draft -> waiting`
- `confirmed -> waiting`
- `arrived -> queued`
- `arrived -> seated`
- `cancelled -> waiting`
- `no_show -> waiting`
- `completed -> waiting`

QueueTicket:

```text
none -> waiting
```

Persisted QueueTicket status:

```text
waiting
```

No state transition in this slice:

- calls QueueTicket
- skips QueueTicket
- rejoins QueueTicket
- seats QueueTicket
- cancels QueueTicket
- expires QueueTicket
- starts Cleaning
- creates Turnover
- mutates DiningTable
- mutates physical TableGroup occupancy

## 17. Event / Audit Boundary

Minimum required BusinessEvent codes:

| Event type | Target type | Target id | Notes |
| --- | --- | --- | --- |
| `reservation.queued` | `reservation` | Reservation id | Reservation has entered waiting branch while status stays `arrived`. |
| `queue_ticket.created` | `queue_ticket` | QueueTicket id | QueueTicket source is Reservation. |

Minimum required AuditLog operation:

```text
reservation.queue
```

Required audit metadata:

- `tenantId`
- `storeId`
- `reservationId`
- `reservationCode`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus`
- `queueGroupId`
- `queueGroupCode`
- `partySize`
- `businessDate`
- `queuePosition`
- previous Reservation status
- current Reservation status
- `partySizeGroup`
- `reasonCode`
- `note`
- `actorId`
- `actorType`
- `idempotencyKey`

Failure audit operation:

```text
reservation.queue.failed
```

Failure audit is best effort and must not mask the original application error, except that audit write failure for an accepted business mutation remains blocking.

## 18. StateTransitionLog Boundary

Reservation:

- Do not write `arrived -> queued`.
- Do not write `arrived -> seated`.
- It is acceptable to omit Reservation transition logging because status is unchanged.

QueueTicket:

Fresh success should record initial creation evidence when the project records initial state transitions:

| Target type | From | To | Transition code |
| --- | --- | --- | --- |
| `queue_ticket` | `none` | `waiting` | `queue_ticket.create` |

If the current transition infrastructure does not support `none` as a source state, the implementation must still write BusinessEvent and AuditLog evidence. A future implementation may choose the project-standard representation for entity creation, but it must not invent a Reservation `queued` state.

## 19. QueueTicket Source / Active Duplicate Boundary

Fresh QueueTicket source:

```text
reservation_id = reservationId
walk_in_id = null
status = waiting
```

Duplicate detection:

- Before creating a new QueueTicket, the application must check for an active QueueTicket with the same Tenant, Store, and Reservation source.
- This V1 slice creates only `waiting`.
- Minimum active status for V1 duplicate detection is `waiting`.
- For defensive consistency, an implementation may treat `waiting`, `called`, `skipped`, and `rejoined` as active/non-terminal if such data exists.
- Terminal statuses for duplicate prevention are `seated`, `cancelled`, and `expired`.

Already-queued behavior:

- Return success-like result with `alreadyQueued = true`.
- Return the existing active QueueTicket details.
- Do not create a duplicate QueueTicket.
- Do not generate a new QueueTicket number.
- Do not change Reservation status.
- Do not append duplicate `reservation.queued`, `queue_ticket.created`, transition logs, or success audit.
- Complete the new idempotency record with the already-queued response snapshot when the duplicate is detected under a new idempotency key.

If an active QueueTicket exists for the same Reservation but belongs to another Store scope:

- Return `STORE_SCOPE_MISMATCH` or a stable consistency error.
- Do not create a compensating duplicate QueueTicket.

## 20. App Gate Future Boundary

This section is for a later API implementation round. It does not authorize a Controller, DTO, route, permission registry change, migration, or frontend entry in this round.

Future API must use:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

or the project-equivalent annotation shape.

Rules:

- `app_key` is fixed as `reservation_queue`.
- `permission` is fixed as `reservation.queue`.
- Do not create a new app key.
- Do not use `queue.ticket.create` for this Reservation-arrived business action.
- Do not use `reservation.seat`.
- Do not use `reservation.arrived.queue`.
- `reservation.queue` is a future permission to add to the `reservation_queue` permission set in a separately approved metadata/code round.
- This contract does not modify `AppGateRequiredPermission`.
- This contract does not select or implement an API path.
- `tenantId` must come from trusted actor/server context.
- `storeId` must come from path or trusted server context.
- Body `tenantId` must not be trusted.
- App Gate deny happens before the application service runs.
- App Gate deny must not change Reservation, QueueTicket, BusinessEvent, StateTransitionLog, AuditLog, or Idempotency business records for this command.
- App Gate deny must write `app_gate_audit_logs`.
- Deny action must be `APP_GATE_DENIED`.

Future `/api/me/apps` note:

- Button-level visibility for Reservation Arrived To Queue must be designed in a separate UI/API contract if needed.
- Today View may later display `进入排队` only when the staff actor has `reservation.queue`.
- The current app-entry model must not be stretched in this contract.

## 21. Idempotency Boundary

Action:

```text
queue_arrived_reservation
```

Idempotency scope:

```text
tenant_id + store_id + source + action + idempotency_key
```

For this slice:

- `source = staff`
- `action = queue_arrived_reservation`

Request hash must include normalized command intent:

- `tenantId`
- `storeId`
- `reservationId`
- `actorId`
- `actorType`
- `partySizeGroup`
- `reasonCode`
- `note`

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| missing | Start command and execute once. | Not applicable. |
| `completed` | Replay stored result with `replayed = true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` or in progress | Return retry-later application error. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| `expired` | Require new key unless a later retention policy explicitly allows reuse. | `IDEMPOTENCY_CONFLICT`. |

Completed replay must not create duplicate QueueTicket, BusinessEvent, StateTransitionLog, AuditLog, ticket number, queue position, or Reservation metadata changes.

Failure behavior:

- Validation failures before idempotency start do not need an IdempotencyRecord.
- Failures after idempotency start should mark the record as `failed`.
- V1 failed idempotency requires a new key.

## 22. Failure Cases

Failures must return stable application-level errors. Raw database exceptions must not cross the application service boundary.

Required failure coverage:

| Case | Application error |
| --- | --- |
| Command is null or missing required fields | `INVALID_COMMAND` |
| Missing idempotency key | `MISSING_IDEMPOTENCY_KEY` |
| Store not found | `STORE_NOT_FOUND` |
| Store scope mismatch | `STORE_SCOPE_MISMATCH` |
| Store access denied | `STORE_ACCESS_DENIED` |
| Reservation not found | `RESERVATION_NOT_FOUND` |
| Reservation belongs to another Store | `STORE_SCOPE_MISMATCH` |
| Reservation status is not `arrived` | `RESERVATION_STATUS_NOT_ARRIVED` |
| Reservation draft or confirmed | `RESERVATION_STATUS_NOT_ARRIVED` |
| Reservation already has active QueueTicket | success-like `alreadyQueued = true` |
| Reservation seated | `RESERVATION_CANNOT_QUEUE_SEATED` |
| Reservation cancelled | `RESERVATION_CANNOT_QUEUE_CANCELLED` |
| Reservation no_show | `RESERVATION_CANNOT_QUEUE_NO_SHOW` |
| Reservation completed | `RESERVATION_CANNOT_QUEUE_COMPLETED` |
| QueueGroup not found | `QUEUE_GROUP_NOT_FOUND` |
| QueueGroup inactive | `QUEUE_GROUP_NOT_FOUND` or `QUEUE_GROUP_INACTIVE` if later public API needs specificity |
| QueueGroup cannot be derived | `QUEUE_GROUP_CANNOT_BE_DERIVED` |
| Requested partySizeGroup incompatible with Reservation party size | `QUEUE_GROUP_PARTY_SIZE_MISMATCH` |
| QueueTicket number conflict after retry policy | `QUEUE_TICKET_NUMBER_CONFLICT` |
| Active QueueTicket exists but is inconsistent | `ACTIVE_QUEUE_TICKET_CONFLICT` |
| Idempotency conflict | `IDEMPOTENCY_CONFLICT` |
| Idempotency in progress | `IDEMPOTENCY_IN_PROGRESS` |
| Failed idempotency reused | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` |
| Illegal QueueTicket state transition | `ILLEGAL_STATE_TRANSITION` |
| BusinessEvent write failure | `EVENT_WRITE_FAILED` |
| StateTransitionLog write failure | `STATE_TRANSITION_WRITE_FAILED` |
| AuditLog write failure | `AUDIT_WRITE_FAILED` |
| QueueTicket save failure | `PERSISTENCE_ERROR` |
| Idempotency save/complete failure | `PERSISTENCE_ERROR` |

Failure side-effect rules:

- No failure path creates Seating.
- No failure path creates SeatingResource.
- No failure path mutates DiningTable.
- No failure path starts Cleaning.
- No failure path creates Turnover.
- No failure path performs No-show.
- No failure path performs Cancellation.
- No failure path changes App Gate metadata.
- No failure path creates migration or SQL artifacts.

## 23. Test Contract

Future implementation tests should cover the following. This round does not write tests.

Success:

- Arrived Reservation is queued.
- Reservation status remains `arrived`.
- QueueTicket is created with `reservation_id`.
- QueueTicket has `walk_in_id = null`.
- QueueTicket status is `waiting`.
- QueueGroup is derived by Reservation party size.
- Optional `partySizeGroup` is validated.
- QueueTicket number is generated by backend.
- QueueTicket number is unique within Store, QueueGroup, and business date.
- Queue position is assigned at the tail.
- `reservation.queued` event is written.
- `queue_ticket.created` event is written.
- QueueTicket creation transition evidence is written when supported.
- AuditLog operation `reservation.queue` is written.
- Idempotency is completed.

Idempotency:

- Completed replay returns stored result.
- Completed replay does not duplicate QueueTicket.
- Completed replay does not duplicate ticket number.
- Completed replay does not duplicate events, transitions, or audit.
- In-progress same hash returns retry later.
- Failed same hash requires a new key.
- Same key with different hash returns conflict.
- Already queued with new key returns `alreadyQueued = true`.
- Already queued with new key does not duplicate QueueTicket or business evidence.

Failure:

- Reservation not found.
- Reservation not `arrived`.
- Reservation seated.
- Reservation cancelled.
- Reservation no_show.
- Reservation completed.
- QueueGroup not found.
- QueueGroup cannot be derived.
- Party-size group mismatch.
- Ticket number conflict after retry policy.
- Event write failure.
- Transition write failure.
- Audit write failure.
- Persistence save failure.

Boundary:

- No Seating created.
- No SeatingResource created.
- No DiningTable mutated.
- No TableLock created.
- No Cleaning created.
- No Turnover created.
- No Queue Call behavior.
- No Queue Skip behavior.
- No Queue Rejoin behavior.
- No No-show behavior.
- No Cancellation behavior.
- No Reservation list/search/calendar behavior.
- No migration change.
- No API implementation.
- No UI implementation.

Future App Gate API tests:

- Allowed request uses `app_key = reservation_queue` and `permission = reservation.queue`.
- Tenant not entitled is denied before business handler.
- Store app disabled is denied before business handler.
- Missing `reservation.queue` permission is denied before business handler.
- Deny writes `APP_GATE_DENIED`.
- Deny does not mutate Reservation, QueueTicket, events, transitions, audit, or idempotency for this business command.

## 24. Next Implementation Notes

Recommended next allowed round:

```text
Reservation Arrived To Queue Application Implementation
```

Implementation should:

- Add application tests first.
- Reuse Reservation CheckIn and Direct Seating idempotency patterns.
- Add `QueueArrivedReservationCommand`.
- Add `ReservationArrivedToQueueApplicationService`.
- Add or extend `QueueGroupRepositoryPort` only in an approved implementation round.
- Extend `QueueTicketRepositoryPort` only as needed for active-source lookup and number allocation.
- Keep QueueTicket number generated by backend.
- Use existing `queue_tickets.reservation_id`.
- Keep Reservation status unchanged as `arrived`.
- Write `reservation.queued`, `queue_ticket.created`, and `reservation.queue` audit.
- Keep Queue Call, Skip, Rejoin, Seating, Table assignment, Cleaning, No-show, Cancellation, API, UI, App Gate registry change, migration, SQL, seed data, and production data outside the implementation round unless separately approved.

Future API round should:

- Design the endpoint separately.
- Use `@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")`.
- Add `reservation.queue` to App Gate permission metadata only in an approved metadata/code round.
- Update local runtime security allowlist only in an approved API/runtime round if needed.

Future UI round should:

- Add Today View action text such as `进入排队` only after the API and permission metadata are approved.
- Keep visibility controlled by `/api/me/apps`.
- Continue showing backend error `code` and `messageKey`.

## 25. Open Questions

- Whether QueueTicket display formatting should be `Q-YYYYMMDD-XXXX` or another pattern remains a future presentation/API decision because the persisted V001 field is integer `ticket_number`.
- Whether QueueTicket creation should always write a `none -> waiting` StateTransitionLog depends on the current transition infrastructure's representation for initial entity creation.
- Whether Store operating-day rules should ever override `reservation.businessDate` for QueueTicket `business_date` remains a future Store calendar policy decision.

## 26. Open Conflicts / Implementation Prerequisites

No schema conflict was found for this contract:

- `queue_tickets.reservation_id` already exists.
- `queue_tickets.walk_in_id` already exists.
- `queue_tickets.ticket_number` is an integer with scoped uniqueness.
- `ReservationStatus` does not include `queued`, which matches this contract.

Known implementation prerequisites:

- `reservation.queue` is not currently registered in `AppGateRequiredPermission`; this is expected because this round must not modify App Gate metadata.
- Existing `CreateQueueTicketCommand` contains a caller-provided `QueueTicketNumber`; this contract requires backend-generated numbers for Reservation Arrived To Queue.
- Existing QueueTicket domain skeleton may need additional fields or mapping support for `reservationId`, `queueGroupId`, `customerId`, `businessDate`, and `queuePosition` in a future implementation round.
- `QueueGroupRepositoryPort` may need to be added in a future implementation round if no suitable port exists.

## 27. Not Created In This Round

- No Java Application Service.
- No Repository implementation.
- No Controller.
- No API DTO.
- No Vue page.
- No Vue component.
- No Router entry.
- No Flyway migration.
- No SQL file.
- No App Gate Java registry change.
- No permission metadata change.
- No database structure change.
- No Queue Call.
- No Queue Skip.
- No Queue Rejoin.
- No Seating.
- No table assignment.
- No table lock.
- No Cleaning.
- No Turnover.
- No No-show.
- No Cancellation.
- No seed data.
- No production configuration.
- No production database access.
