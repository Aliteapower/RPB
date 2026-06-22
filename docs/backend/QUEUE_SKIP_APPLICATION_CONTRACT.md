# Queue Skip Application Contract V1

## 1. Purpose

This document defines the minimum application contract for skipping an existing called QueueTicket.

Business flow:

```text
QueueTicket.status = called
-> Store staff skips the ticket
-> QueueTicket.status = skipped
-> QueueTicket.skippedAt is recorded
-> Reservation.status remains arrived
-> Table, Seating, Cleaning, and Turnover are unchanged
-> BusinessEvent, StateTransitionLog, AuditLog, and Idempotency are handled
```

This is a contract-only round. It does not create Java implementation, Controller, API DTO, Vue UI, router entry, Staff Home entry, App Gate metadata, Flyway migration, SQL, seed data, production configuration, or database data.

## 2. Read Inputs

Completed Queue baseline:

- `docs/api/QUEUE_LIST_API_CONTRACT.md`
- `docs/api/QUEUE_LIST_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_LIST_UI_VALIDATION_REPORT.md`
- `docs/backend/QUEUE_CALL_APPLICATION_CONTRACT.md`
- `docs/backend/QUEUE_CALL_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_CALL_UI_VALIDATION_REPORT.md`
- `docs/backend/SEATING_FROM_CALLED_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/SEATING_FROM_CALLED_QUEUE_UI_VALIDATION_REPORT.md`

Queue, Reservation, schema, evidence, and idempotency inputs:

- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/status/QueueTicketStatus.java`
- `src/main/java/com/rpb/reservation/queue/state/QueueTicketStateMachine.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueTicketEntity.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/adapter/QueueTicketPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/queue/persistence/repository/QueueTicketJpaRepository.java`
- `src/main/java/com/rpb/reservation/queue/application/port/out/QueueTicketRepositoryPort.java`
- `src/main/java/com/rpb/reservation/reservation/domain/Reservation.java`
- `src/main/java/com/rpb/reservation/reservation/status/ReservationStatus.java`
- `src/main/java/com/rpb/reservation/reservation/state/ReservationStateMachine.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/entity/ReservationEntity.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/mapper/DefaultReservationMapper.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/reservation/persistence/repository/ReservationJpaRepository.java`
- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/domain/BusinessEvent.java`
- `src/main/java/com/rpb/reservation/audit/domain/StateTransitionLog.java`
- `src/main/java/com/rpb/reservation/audit/domain/AuditLog.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/BusinessEventRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/StateTransitionLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/AuditLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/idempotency/domain/IdempotencyRecord.java`
- `src/main/java/com/rpb/reservation/idempotency/status/IdempotencyStatus.java`
- `src/main/java/com/rpb/reservation/idempotency/rule/DefaultIdempotencyRule.java`
- `src/main/java/com/rpb/reservation/idempotency/application/port/out/IdempotencyRepositoryPort.java`
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

Queue List API inputs:

- `QueueTicketListController`
- `QueueTicketListResponse`
- `QueueTicketListApiMapper`
- `QueueTicketListApiErrorMapper`
- `QueueTicketListApplicationService`
- `QueueTicketListApplicationServiceTest`
- `QueueTicketListApiIntegrationTest`
- `QueueTicketListControllerTest`
- `QueueTicketListLocalRuntimeSecurityTest`

App Gate inputs:

- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`

Governance, architecture, and skill inputs:

- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`

## 3. Confirmed Baseline

- Queue List Read API is implemented and validated as read-only.
- Queue List UI is implemented and validated as read-only.
- Queue Call is implemented and can produce `QueueTicket.status = called`.
- Seating From Called Queue is implemented and consumes `called -> seated`.
- `QueueTicketStatus` includes `SKIPPED("skipped")`.
- `QueueTicketStateMachine` allows `CALLED -> SKIPPED`.
- V001 `queue_tickets.status` allows `skipped`.
- V001 `queue_tickets` includes `skipped_at`.
- `QueueTicketEntity` includes `skippedAt`.
- Current `QueueTicket` domain and `DefaultQueueTicketMapper` do not expose or preserve `skippedAt`; future implementation must close this source-code gap before using the generic save path for skip.
- `AppGateRequiredPermission` currently includes `queue.view`, `queue.call`, and `queue.seat`, but not `queue.skip`.

## 4. Selected Vertical Slice

```text
Queue Skip
```

Input precondition:

```text
queue_ticket.status = called
reservation.status = arrived
```

Required output:

```text
queue_ticket.status = skipped
queue_ticket.skipped_at = effectiveSkippedAt
reservation.status remains arrived
business event written
queue_ticket transition evidence written
audit written
idempotency completed
```

This slice only skips a called QueueTicket. It does not rejoin the ticket, display a queue board, seat the party, assign a table, create no-show/cancellation behavior, start Cleaning, or create Turnover.

## 5. Scope

In scope:

- Application command contract for skipping an existing QueueTicket.
- Store-scoped QueueTicket lookup by `tenantId`, `storeId`, and `queueTicketId`.
- Validation that QueueTicket belongs to the current Tenant and Store scope.
- Validation that fresh skip is allowed only from `called`.
- Success-like `alreadySkipped = true` behavior for an already skipped ticket when complete durable skip evidence exists.
- Related Reservation lookup from `QueueTicket.reservationId`.
- Validation that related Reservation exists and remains `arrived`.
- Resolve `skippedAt`.
- Persist QueueTicket skip state in existing schema fields.
- BusinessEvent boundary.
- StateTransitionLog boundary.
- AuditLog boundary.
- Idempotency boundary for `skip_queue_ticket`.
- Future API boundary.
- Future UI boundary.
- Failure and test contract for later implementation.

## 6. Non-Scope

Out of scope:

- Queue Rejoin.
- Queue Display Screen.
- Queue Workbench mutation.
- Queue Call mutation.
- Queue Seat mutation.
- Seating from skipped QueueTicket.
- Table assignment.
- Table lock.
- DiningTable mutation.
- TableGroup mutation.
- Table map.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- Reservation state-machine changes.
- `Reservation.status = skipped`.
- API implementation.
- Controller.
- API DTO.
- Vue UI.
- Vue Router.
- Staff Home.
- Java Application Service implementation.
- Flyway migration or SQL change.
- App Gate Java registry modification.
- Permission metadata modification.
- Production configuration.
- Seed data.
- Production database access or data changes.

## 7. QueueTicket Status Decision

Fresh skip transition:

```text
called -> skipped
```

Rules:

- QueueTicket must already exist.
- QueueTicket must belong to the current Tenant and Store scope.
- Fresh skip is allowed only from `called`.
- Current project state machine already supports `called -> skipped`.
- `waiting`, `seated`, `cancelled`, `expired`, and `rejoined` are not valid fresh-skip source states.
- `skipped` is handled only by the success-like `alreadySkipped` branch when complete skip evidence exists.

Persisted values:

```text
queue_tickets.status = skipped
queue_tickets.skipped_at = effectiveSkippedAt
```

`effectiveSkippedAt` is `command.skippedAt` when provided; otherwise it is the application clock time.

## 8. Reservation Status Decision

Reservation status remains:

```text
arrived -> arrived
```

Rules:

- V1 requires a related Reservation. The QueueTicket must have `reservationId`.
- Related Reservation must belong to the same Tenant and Store scope.
- Related Reservation must have `status = arrived`.
- Skip must not persist a Reservation change.
- Skip must not write a Reservation StateTransitionLog.
- Skip must not introduce `Reservation.status = skipped`.
- Skip must not convert the Reservation to `cancelled`, `no_show`, or `seated`.

Reason:

- Skip is not cancellation.
- Skip is not no-show.
- Skip is not seating.
- The customer may still return in a later Queue Rejoin slice.

## 9. skippedAt / Evidence Decision

Current schema decision:

- V001 has `queue_tickets.skipped_at timestamptz null`.
- `QueueTicketEntity` has `skippedAt`.
- No migration is required for durable `skippedAt`.

Current source-code gap:

- `QueueTicket` domain currently has no `skippedAt` field.
- `DefaultQueueTicketMapper.toDomain(...)` currently ignores `QueueTicketEntity.skippedAt`.
- `DefaultQueueTicketMapper.toEntity(...)` currently writes `skippedAt = null`.
- A future implementation must update the domain/mapper/persistence boundary or add a dedicated repository method so skip can persist and preserve `skipped_at`.
- Future implementation must not use the current generic mapper unchanged in a way that clears existing `skipped_at`.

Fresh skip evidence:

- `queue_tickets.status = skipped`.
- `queue_tickets.skipped_at = effectiveSkippedAt`.
- `business_events.event_type = queue_ticket.skipped`.
- `state_transition_logs.target_type = queue_ticket`, `from_status = called`, `to_status = skipped`, `transition_code = queue_ticket.skip`.
- `audit_logs.operation_code = queue.skip`.
- `idempotency_records.action = skip_queue_ticket`, `status = completed`, and response snapshot records `skippedAt`.

Already skipped evidence:

- `queue_tickets.status = skipped`.
- `queue_tickets.skipped_at is not null`.
- Existing skip event / transition / audit evidence proves the prior skip.

If QueueTicket is `skipped` but evidence is incomplete, return an application-level consistency error. Do not silently succeed.

If a future deployment somehow lacks `queue_tickets.skipped_at`, implementation must stop and report a schema conflict. It must not add a migration inside this slice unless a later approved migration round explicitly allows it.

## 10. Command Contract

Command name:

```java
SkipQueueTicketCommand
```

Fields:

| Field | Required | Source | Notes |
| --- | --- | --- | --- |
| `tenantId` | yes | Actor context | Never trusted from request body. |
| `storeId` | yes | API path / trusted server context | Store operational scope. |
| `queueTicketId` | yes | API path / command | Target QueueTicket. |
| `skippedAt` | no | Request body / command | If absent, application clock supplies it. |
| `reasonCode` | no | Request body / command | Stored in metadata/audit when provided. |
| `note` | no | Request body / command | Stored in metadata/audit when provided. |
| `idempotencyKey` | yes | Future `Idempotency-Key` header | Required for this mutation. |
| `actorId` | yes | Actor context | Staff actor id. |
| `actorType` | yes | Actor context | Usually `staff`. |

Forbidden command fields:

- `reservationId` from client.
- `tableId`.
- `tableGroupId`.
- `seatingId`.
- `cleaningId`.
- `turnoverId`.
- `rejoinReason`.
- `noShowAt`.
- `cancelledAt`.
- `status`.
- Any mutation action flag supplied by the client.

Validation rules:

- `tenantId` is required.
- `storeId` is required.
- `queueTicketId` is required.
- `idempotencyKey` is required.
- `actorId` is required.
- `actorType` is required.
- QueueTicket must belong to current Tenant and Store scope.
- QueueTicket status must be `called` for fresh skip.
- Related Reservation must exist.
- Reservation status must be `arrived`.
- `skippedAt` is optional. If null, use application clock.
- Do not create Seating.
- Do not change Table.
- Do not do Rejoin.

## 11. Application Service Boundary

Service name:

```java
QueueSkipApplicationService
```

Method:

```java
skipQueueTicket(SkipQueueTicketCommand command)
```

Responsibilities:

1. Validate command shape.
2. Build `StoreScope` from actor tenant and store.
3. Build stable idempotency request hash.
4. Check existing idempotency record.
5. Start idempotency for new keys.
6. Validate store access.
7. Load QueueTicket by `StoreScope + queueTicketId`.
8. Validate QueueTicket scope.
9. If QueueTicket is already `skipped`, validate complete skip evidence and return `alreadySkipped = true`.
10. Validate QueueTicket status is `called`.
11. Validate `QueueTicketStateMachine.canTransition(CALLED, SKIPPED)`.
12. Load related Reservation from QueueTicket source.
13. Validate Reservation scope and `status = arrived`.
14. Resolve `effectiveSkippedAt`.
15. Persist QueueTicket `status = skipped` and `skipped_at = effectiveSkippedAt`.
16. Keep Reservation `status = arrived`.
17. Append BusinessEvent.
18. Append StateTransitionLog.
19. Append AuditLog.
20. Complete idempotency with response snapshot.
21. Return result.

The application service does not own:

- API parsing.
- API error envelope mapping.
- UI message text.
- Queue Rejoin.
- Queue Display.
- Seating.
- Table assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- App Gate metadata changes.
- Migration.

Transactional boundary:

- Fresh skip mutation, evidence writes, audit write, and idempotency completion must happen in one business transaction.
- Application failures after idempotency start must mark idempotency failed when possible.
- Failure audit `queue.skip.failed` may be written following the existing queue mutation pattern, but failure audit must never hide the original application error.

## 12. State / Event / Audit Boundary

QueueTicket:

```text
called -> skipped
```

Reservation:

```text
arrived -> arrived
```

No Reservation transition log is written.

Table / Seating:

- No Seating created.
- No SeatingResource created.
- No DiningTable status changed.
- No TableGroup status changed.
- No TableLock created or released.
- No Cleaning created.
- No Turnover created.

BusinessEvent:

```text
event_type = queue_ticket.skipped
target_type = queue_ticket
target_id = queueTicketId
```

Recommended metadata:

- `queueTicketId`
- `queueTicketNumber`
- `beforeQueueTicketStatus = called`
- `afterQueueTicketStatus = skipped`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `queueGroupId`
- `businessDate`
- `partySize`
- `calledAt`
- `holdUntilAt` / `expiresAt`
- `skippedAt`
- `reasonCode`
- `note`
- `idempotencyKey`
- `alreadySkipped`

StateTransitionLog:

```text
target_type = queue_ticket
from_status = called
to_status = skipped
transition_code = queue_ticket.skip
```

Do not write:

- `reservation: arrived -> skipped`
- Table transition.
- Seating transition.
- Cleaning transition.
- Turnover transition.

AuditLog:

```text
operation_code = queue.skip
target_type = queue_ticket
target_id = queueTicketId
```

Failure audit, if written:

```text
operation_code = queue.skip.failed
target_type = queue_ticket
target_id = queueTicketId
```

## 13. Idempotency Behavior

Action:

```text
skip_queue_ticket
```

Required key:

```text
Idempotency-Key
```

Stable request hash fields:

- `tenantId`
- `storeId`
- `queueTicketId`
- `actorId`
- normalized `actorType`
- `skippedAt`, or literal `application_clock` when omitted
- normalized `reasonCode`
- normalized `note`

Rules:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| none | Start idempotency, execute command, complete snapshot. | Not applicable. |
| `completed` | Replay stored result. | Return idempotency conflict. |
| `started` / `in_progress` | Return retry-later / command-in-progress. | Return idempotency conflict. |
| `failed` | Require a new key. | Return idempotency conflict. |
| `expired` | Treat according to the shared idempotency rule in the implementation round; do not mutate without a clear rule. | Return idempotency conflict unless the shared rule says otherwise. |

Completed replay must not duplicate:

- QueueTicket mutation.
- BusinessEvent.
- StateTransitionLog.
- AuditLog.
- Reservation mutation.
- Seating.
- SeatingResource.
- Table mutation.
- Cleaning.
- Turnover.

Response snapshot must include at least:

- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = skipped`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `skippedAt`
- `alreadySkipped`

## 14. AlreadySkipped Behavior

If QueueTicket is already `skipped`:

- Validate it belongs to current Tenant and Store scope.
- Validate related Reservation exists.
- Validate Reservation remains `arrived`.
- Validate complete skip evidence exists.
- Return success-like result with `alreadySkipped = true`.
- Complete the new idempotency key with the already-skipped response snapshot.

Complete evidence requires:

- `queue_tickets.skipped_at is not null`.
- Existing `queue_ticket.skipped` BusinessEvent.
- Existing `queue_ticket.skip` StateTransitionLog for `called -> skipped`.
- Existing `queue.skip` AuditLog.

AlreadySkipped must not:

- Rewrite QueueTicket.
- Rewrite Reservation.
- Create duplicate BusinessEvent.
- Create duplicate StateTransitionLog.
- Create duplicate AuditLog.
- Create Seating or SeatingResource.
- Change Table.
- Create Cleaning.
- Create Turnover.

If QueueTicket is `skipped` but complete evidence is missing:

```text
QUEUE_TICKET_ALREADY_SKIPPED_WITHOUT_EVIDENCE
```

Return an application-level error and do not silently succeed.

## 15. Failure Cases

The future implementation must return application-level errors for:

- Invalid command.
- Missing idempotency key.
- Store not found.
- Store scope mismatch.
- Store access denied.
- Queue ticket not found.
- Queue ticket scope mismatch.
- Queue ticket status not called.
- Queue ticket already skipped without evidence.
- Queue ticket call evidence incomplete, if `calledAt` or `expiresAt` is required for skip evidence.
- Related reservation id missing.
- Related reservation not found.
- Related reservation scope mismatch.
- Related reservation status not arrived.
- Illegal state transition.
- Idempotency conflict.
- Idempotency in progress.
- Failed idempotency requires new key.
- Event write failure.
- Transition write failure.
- Audit write failure.
- Idempotency persistence failure.
- QueueTicket persistence save failure.
- Unknown persistence failure.

Raw database exceptions must not be exposed to API clients.

Suggested application error codes:

- `INVALID_COMMAND`
- `MISSING_IDEMPOTENCY_KEY`
- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `STORE_ACCESS_DENIED`
- `QUEUE_TICKET_NOT_FOUND`
- `QUEUE_TICKET_STATUS_NOT_CALLED`
- `QUEUE_TICKET_ALREADY_SKIPPED_WITHOUT_EVIDENCE`
- `QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE`
- `RESERVATION_NOT_FOUND`
- `RESERVATION_STATUS_NOT_ARRIVED`
- `ILLEGAL_STATE_TRANSITION`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY`
- `BUSINESS_EVENT_WRITE_FAILED`
- `STATE_TRANSITION_WRITE_FAILED`
- `AUDIT_WRITE_FAILED`
- `PERSISTENCE_ERROR`

## 16. Future API Boundary

Future endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

Header:

```http
Idempotency-Key: <key>
```

Request body allowlist:

```json
{
  "skippedAt": "2030-06-20T03:25:00Z",
  "reasonCode": "NO_RESPONSE",
  "note": "Customer did not respond to call"
}
```

Forbidden request fields:

- `tenantId`
- `storeId` in body or query
- `queueTicketId` in body
- `reservationId`
- `actorId`
- `actorType`
- `tableId`
- `tableGroupId`
- `seatingId`
- `cleaningId`
- `turnoverId`
- `rejoinReason`
- `noShowAt`
- `cancelledAt`
- `status`

Future App Gate:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")
```

Do not reuse:

- `queue.call`
- `queue.seat`
- `queue.view`
- `reservation.queue`
- `reservation.seat`

This round does not implement the API and does not add `queue.skip` to App Gate metadata.

## 17. Future UI Boundary

No UI is designed or implemented in this contract.

A future UI may expose Queue Skip as a separate approved slice, but this contract does not authorize:

- Queue Skip button in Queue List.
- Queue Workbench.
- Queue Display Screen.
- Queue Rejoin action.
- Queue Call action from list.
- Queue Seat action from list.
- Table map.
- Auto assignment.

Any future UI must use the future API boundary and must not mutate business state directly.

## 18. Test Contract

Future implementation tests must cover at least:

Success:

- Called queue ticket is skipped.
- QueueTicket becomes `skipped`.
- `queue_tickets.skipped_at` is written.
- Reservation remains `arrived`.
- No Seating is created.
- No SeatingResource is created.
- No DiningTable status changes.
- BusinessEvent `queue_ticket.skipped` is written.
- StateTransitionLog `queue_ticket called -> skipped` is written.
- AuditLog `queue.skip` is written.
- Idempotency is completed.

AlreadySkipped:

- Skipped QueueTicket with complete evidence returns `alreadySkipped = true`.
- No duplicate BusinessEvent is written.
- No duplicate StateTransitionLog is written.
- No duplicate AuditLog is written.
- No Reservation, Seating, SeatingResource, Table, Cleaning, or Turnover mutation occurs.
- Skipped QueueTicket without complete evidence returns an application-level error.

Idempotency:

- Completed replay.
- In-progress retry later.
- Failed idempotency requires a new key.
- Hash conflict.
- Missing idempotency key.
- Completed replay does not duplicate evidence.

Failure:

- Store not found.
- Store scope mismatch.
- Queue ticket not found.
- Queue ticket status not called.
- Queue ticket skipped without evidence.
- Related reservation id missing.
- Related reservation not found.
- Related reservation status not arrived.
- Event write failure.
- Transition write failure.
- Audit write failure.
- Persistence save failure.

Boundary:

- No Rejoin.
- No Display.
- No Seating.
- No SeatingResource.
- No Table status change.
- No Cleaning.
- No Turnover.
- No No-show.
- No Cancellation.
- No API in this round.
- No UI in this round.
- No Migration in this round.

## 19. Next Implementation Notes

Future application implementation should:

- Add `SkipQueueTicketCommand`, `QueueSkipApplicationService`, `QueueSkipResult`, `QueueSkipError`, and focused tests in a later approved implementation round.
- Reuse `DefaultStoreAccessPolicy`, `QueueTicketStateMachine`, shared audit/event/transition rules, and `DefaultIdempotencyRule`.
- Add a `QueueTicket.skip(Instant skippedAt)` domain method or a dedicated persistence update method that preserves durable `skipped_at`.
- Update `QueueTicket` domain and `DefaultQueueTicketMapper` to preserve `skippedAt`, or avoid the current mapper path for skip writes.
- Verify the generic QueueTicket save path does not clear `skipped_at` or `rejoined_at`.
- Use `BusinessEventRepositoryPort.findByTarget`, `StateTransitionLogRepositoryPort.findByTarget` or `findLatest`, and `AuditLogRepositoryPort.findByTarget` / `findByOperation` as needed for alreadySkipped evidence checks.
- Add future App Gate metadata for `queue.skip` only in a separate approved API/App Gate round.
- Avoid any migration unless a later approved schema round explicitly requires one.

## 20. Open Questions

- Should `reasonCode` be validated against `reason_codes.reason_type = skip`, or should V1 only preserve the provided code in metadata?
- Should a later slice support skipping walk-in-backed QueueTickets? This V1 contract requires a related Reservation because the selected output includes Reservation summary and Reservation remains `arrived`.
- Should `alreadySkipped` evidence require all three evidence records, or is `skipped_at` plus one evidence record enough for operational recovery?
- Should future API error message keys use `queue.skip.*` for every application error, matching existing `queue.call.*` and `queue.seat.*` patterns?

## 21. Open Conflicts

- `queue.skip` is not currently present in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`. This contract declares the future permission but does not modify App Gate metadata.
- `queue_tickets.skipped_at` exists in schema/entity, but current `QueueTicket` domain and `DefaultQueueTicketMapper` do not preserve it. Future implementation must resolve this before claiming durable `skippedAt` behavior.
