# Reservation CheckIn Application Contract V1

## 1. Purpose

This document defines the minimum vertical slice contract for Reservation CheckIn.

Business flow:

```text
Confirmed Reservation
-> customer arrives at the Store
-> Store staff performs CheckIn
-> Reservation status changes from confirmed to arrived
-> Reservation arrival evidence is written
-> BusinessEvent, StateTransitionLog, AuditLog, and Idempotency are handled
-> application result is returned
```

This is a contract-only round. It does not implement Java code, API DTOs, Vue UI, repository adapters, SQL, Flyway migration, seed data, production configuration, or production database access.

## 2. Scope

In scope:

- Application contract for Store staff checking in an existing confirmed Reservation.
- Store-scoped Reservation lookup by `tenantId`, `storeId`, and `reservationId`.
- Reservation status transition:

```text
confirmed -> arrived
```

- Arrival timestamp resolution through `arrivedAt` from the command or application current time.
- BusinessEvent boundary.
- StateTransitionLog boundary.
- AuditLog boundary.
- App Gate boundary for a later API implementation.
- Idempotency boundary for `check_in_reservation`.
- Duplicate/already-arrived behavior.
- Failure and test contract for a later implementation round.

CheckIn result should include at least:

- `reservationId`
- `reservationCode`
- `status = arrived`
- `arrivedAt`
- `alreadyArrived`
- event codes
- idempotency status and replay flag

## 3. Non-Scope

Out of scope:

- Queue creation.
- Seating.
- Table assignment.
- TableLock creation.
- Reservation preassignment.
- No-show.
- Cancellation.
- Reservation list/search.
- Reservation calendar.
- CheckIn primary entity.
- `check_ins` table.
- `CheckInEntity`.
- New App Gate app key.
- API implementation.
- Controller.
- API DTO.
- Vue page.
- Vue component.
- Repository implementation.
- Java Application Service implementation.
- Flyway migration or SQL change.
- Database structure change.
- Seed data.
- Production configuration.
- Production database connection.

Explicit boundaries:

- CheckIn V1 must not create `QueueTicket`.
- CheckIn V1 must not create `Seating`.
- CheckIn V1 must not assign a DiningTable or TableGroup.
- CheckIn V1 must not decide whether a table is available.
- CheckIn V1 must not mark a Reservation as `seated`, `cancelled`, `no_show`, or `completed`.

## 4. Command Contract

Command:

```text
CheckInReservationCommand
```

Fields:

| Field | Source | Required | Notes |
| --- | --- | --- | --- |
| `tenantId` | actor/server context | Yes | Trusted Tenant scope. |
| `storeId` | path/server context | Yes | Store operation boundary. |
| `reservationId` | request/application input | Yes | Existing Reservation id. |
| `idempotencyKey` | request/application input | Yes | Required for CheckIn. |
| `actorId` | actor/server context | Yes | Staff, customer, integration, or system actor id. |
| `actorType` | actor/server context | Yes | Expected values align with `staff`, `customer`, `integration`, `system`. |
| `arrivedAt` | request/application input | No | Optional UTC instant. If absent, application uses current time. |
| `reasonCode` | request/application input | No | Optional reason or operational context. Not a cancellation/no-show reason. |
| `note` | request/application input | No | Optional staff note. |

Forbidden fields:

- `queueTicketId`
- `seatingId`
- `tableId`
- `tableGroupId`

Reason:

Reservation CheckIn V1 only confirms arrival. It does not create queue, seating, table assignment, or table-group assignment intent.

## 5. Application Service Boundary

Application Service:

```text
ReservationCheckInApplicationService
```

Method:

```text
checkInReservation(CheckInReservationCommand command)
```

Responsibilities:

1. Validate command presence and required fields.
2. Build `StoreScope` from `tenantId` and `storeId`.
3. Check idempotency using action `check_in_reservation`.
4. Validate Store access.
5. Load Reservation by `StoreScope + reservationId`.
6. Validate Reservation belongs to the Store scope.
7. Validate Reservation status.
8. Validate Reservation can check in.
9. Resolve `arrivedAt`:
   - use command `arrivedAt` when supplied
   - otherwise use application current time
10. Transition Reservation from `confirmed` to `arrived`.
11. Save Reservation.
12. Write BusinessEvent.
13. Write StateTransitionLog.
14. Write AuditLog.
15. Complete idempotency with response snapshot.
16. Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI message text.
- Queue creation.
- Seating.
- Table assignment.
- Table availability decision.
- No-show.
- Cancellation.
- Reservation list/search/calendar.
- SQL implementation.
- Migration design.

Transaction guidance:

- Fresh successful CheckIn should commit Reservation save, BusinessEvent, StateTransitionLog, AuditLog, and idempotency completion atomically.
- If event, transition, audit, or Reservation persistence fails, the application must return a stable application-level error and must not expose a raw database exception.
- Already-arrived success-like behavior may complete the new idempotency record with an `alreadyArrived=true` response snapshot, but must not append duplicate event, transition, or audit records.

## 6. Required Ports

Only CheckIn-needed methods are part of this contract. Do not create mechanical CRUD contracts.

### StoreRepositoryPort

Required method:

```text
findById(StoreScope scope)
```

Purpose:

- Verify Store exists.
- Verify Store belongs to Tenant scope.
- Load Store status and Store operation metadata needed by access policy.

### ReservationRepositoryPort

Required methods:

```text
findById(StoreScope scope, ReservationId reservationId)
save(StoreScope scope, Reservation reservation)
```

Purpose:

- Load the Reservation inside the Store operation boundary.
- Persist `confirmed -> arrived`.
- Return Reservation state needed for result mapping.

### BusinessEventRepositoryPort

Required method:

```text
append(StoreScope scope, BusinessEvent event)
```

Purpose:

- Append `reservation.arrived` for fresh CheckIn success.

### StateTransitionLogRepositoryPort

Required methods:

```text
append(StoreScope scope, StateTransitionLog transition)
findLatestReservationArrival(StoreScope scope, ReservationId reservationId)
```

Purpose:

- Append the fresh `confirmed -> arrived` transition.
- Read existing arrival evidence when returning `alreadyArrived=true` and the Reservation aggregate does not already carry an arrival timestamp.

### AuditLogRepositoryPort

Required method:

```text
append(StoreScope scope, AuditLog auditLog)
```

Purpose:

- Append `reservation.check_in` audit for fresh CheckIn success.
- Append failure audit where the command has enough context and the AuditRule requires it.

### IdempotencyRepositoryPort

Required methods:

```text
findOrStart(scope, source, action, key, requestHash, expiresAt)
complete(record, targetRef, responseSnapshot)
fail(record, failureReason)
```

Action:

```text
check_in_reservation
```

Purpose:

- Deduplicate retried CheckIn commands.
- Replay completed same-hash result.
- Reject same-key different-hash conflicts.
- Preserve retry behavior for already-arrived success-like responses.

Forbidden ports in this slice:

- `QueueTicketRepositoryPort`
- `SeatingRepositoryPort`
- `TableLockRepositoryPort`
- `ReservationPreassignmentRepositoryPort`
- `NoShowRepositoryPort`
- `CancellationRepositoryPort`

## 7. Required Rules / Policies / Validators

Required:

| Component | Type | Purpose |
| --- | --- | --- |
| `StoreAccessPolicy` | Policy | Actor must have Tenant and Store scope access for Reservation CheckIn. |
| `ReservationCheckInRule` | Rule | Determines whether the current Reservation status can be checked in. |
| `ReservationStateMachine` | Rule/State machine | Allows only `confirmed -> arrived` for this slice. |
| `AuditRule` | Rule | Requires and shapes `reservation.check_in` audit. |
| `BusinessEventRule` | Rule | Requires and validates `reservation.arrived` event. |
| `StateTransitionRule` | Rule | Requires and validates `confirmed -> arrived` transition log. |
| `IdempotencyRule` | Rule | Applies replay, in-progress, failed-key, and conflict behavior. |

Forbidden in this slice:

- `QueueCallingRule`
- `TableAssignmentRule`
- `SeatingSourceValidator`
- `NoShowPolicy`
- `CancellationPolicy`

## 8. State Transition

Successful fresh CheckIn:

```text
Reservation.status: confirmed -> arrived
```

Transition log:

```text
target_type = reservation
target_id = reservationId
from_status = confirmed
to_status = arrived
transition_code = reservation.check_in
```

Allowed source:

```text
staff
```

Other actor/source values may be supported later only when a separate API/product contract authorizes them. This contract is for Store staff CheckIn.

Illegal transitions in this slice:

- `draft -> arrived`
- `confirmed -> seated`
- `confirmed -> cancelled`
- `confirmed -> no_show`
- `confirmed -> completed`
- `arrived -> seated`
- `arrived -> queue`
- `arrived -> no_show`
- `arrived -> cancelled`
- `seated -> arrived`
- terminal state to any non-terminal state

## 9. Event / Audit Boundary

Recommended event name:

```text
reservation.arrived
```

This contract standardizes on `reservation.arrived` rather than writing both `reservation.checked_in` and `reservation.arrived`.

Rationale:

- `arrived` is the resulting Reservation state.
- `check_in` is the staff operation.
- The separation keeps event naming state-oriented and audit naming operation-oriented.

BusinessEvent requirements:

- `tenantId`
- `storeId`
- `eventType = reservation.arrived`
- `targetType = reservation`
- `targetId = reservationId`
- `actorId`
- `actorType`
- `source = staff`
- `beforeState.status = confirmed`
- `afterState.status = arrived`
- `reasonCode`
- `note`
- `idempotencyKey`
- `occurredAt = arrivedAt`

Audit operation:

```text
reservation.check_in
```

Audit requirements:

- `tenantId`
- `storeId`
- `operationCode = reservation.check_in`
- `targetType = reservation`
- `targetId = reservationId`
- `actorId`
- `actorType`
- `source = staff`
- `beforeState.status = confirmed`
- `afterState.status = arrived`
- `reservationCode`
- `arrivedAt`
- optional `reasonCode`
- optional `note`
- `idempotencyKey`

Already-arrived duplicate behavior:

- Do not write another `reservation.arrived` event.
- Do not write another `confirmed -> arrived` transition.
- Do not write another `reservation.check_in` audit.
- Return success-like result with `alreadyArrived=true`.

## 10. Idempotency Boundary

Action:

```text
check_in_reservation
```

Required key:

```text
idempotencyKey
```

Request hash must include normalized command intent:

- `tenantId`
- `storeId`
- `reservationId`
- `actorId`
- `actorType`
- explicit `arrivedAt` when supplied
- a stable sentinel such as `application_clock` when `arrivedAt` is absent
- `reasonCode`
- `note`

The request hash must not include the resolved current time when `arrivedAt` is absent, otherwise identical retries would not replay.

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| `completed` | Replay stored result with `replayed=true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` / `in_progress` | Return retry-later application error. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| missing | Start record and execute once. | Not applicable. |

Missing key:

```text
MISSING_IDEMPOTENCY_KEY
```

Completed replay must not append duplicate BusinessEvent, StateTransitionLog, or AuditLog records.

Failed idempotency records must not be reused for a new attempt. A new idempotency key is required after a failed same-hash command.

## 11. Duplicate / Already Arrived Behavior

When the Reservation is already `arrived`:

```text
Return success-like result
status = arrived
alreadyArrived = true
```

Behavior with same completed idempotency key:

- Replay the previous result.
- `idempotency.replayed = true`.
- Do not mutate Reservation.
- Do not append event, transition, or audit.

Behavior with a new idempotency key:

- If idempotency check accepts the new key, return `alreadyArrived=true`.
- Complete the new idempotency record with the `alreadyArrived=true` response snapshot.
- Do not mutate Reservation.
- Do not append event, transition, or audit.

`arrivedAt` for already-arrived result:

- Prefer the Reservation aggregate arrival timestamp if available.
- Otherwise use existing arrival evidence from `StateTransitionLog` or `BusinessEvent`.
- Do not use the new command time as a fresh arrival timestamp for already-arrived behavior.

## 12. Failure Cases

| Scenario | Application behavior | Side effects |
| --- | --- | --- |
| App Gate denied by future API adapter | Return mapped App Gate rejection before application service execution. | No Reservation mutation. No BusinessEvent, StateTransitionLog, AuditLog, or idempotency write by CheckIn application service. App Gate writes `app_gate_audit_logs` with `action = APP_GATE_DENIED`. |
| Store not found | Return `STORE_NOT_FOUND`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Store scope mismatch | Return `STORE_SCOPE_MISMATCH`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation not found | Return `RESERVATION_NOT_FOUND`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation belongs to different Store | Return `STORE_SCOPE_MISMATCH`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation status is not `confirmed` | Return state-specific application result or error below. | No illegal transition. |
| Reservation already `arrived` | Return success-like `alreadyArrived=true`. | No duplicate event, transition, or audit. Complete new idempotency record when started. |
| Reservation `cancelled` | Return `RESERVATION_CANNOT_CHECK_IN_CANCELLED`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation `no_show` | Return `RESERVATION_CANNOT_CHECK_IN_NO_SHOW`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation `completed` | Return `RESERVATION_CANNOT_CHECK_IN_COMPLETED`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation `seated` | Return `RESERVATION_CANNOT_CHECK_IN_SEATED`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Reservation `draft` | Return `RESERVATION_STATUS_NOT_CONFIRMED`. | No Reservation mutation. Mark started idempotency failed when possible. |
| Idempotency conflict | Return `IDEMPOTENCY_CONFLICT`. | Existing idempotency record unchanged. No Reservation mutation. |
| Idempotency in progress | Return `IDEMPOTENCY_IN_PROGRESS`. | Existing idempotency record unchanged. No Reservation mutation. |
| Failed idempotency requires new key | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | Existing failed record unchanged. No Reservation mutation. |
| BusinessEvent write failure | Return `EVENT_WRITE_FAILED`. | Roll back Reservation mutation and transition/audit writes if transaction started. Mark idempotency failed when possible. |
| StateTransitionLog write failure | Return `STATE_TRANSITION_WRITE_FAILED`. | Roll back Reservation mutation and event/audit writes if transaction started. Mark idempotency failed when possible. |
| AuditLog write failure | Return `AUDIT_WRITE_FAILED`. | Roll back Reservation mutation and event/transition writes if transaction started. Mark idempotency failed when possible. |
| Reservation save failure | Return `PERSISTENCE_ERROR`. | No committed event, transition, audit, or idempotency completion. Mark idempotency failed when possible. |

Failure requirements:

- Failures must return application-level errors.
- Raw database exceptions must not leak across the application service boundary.
- Failure audit is allowed where target context exists and AuditRule requires it, except when the audit append itself is the failed operation.

## 13. App Gate Boundary

This section is for a later API implementation. It does not authorize a Controller, DTO, route, OpenAPI document, permission migration, or new app key in this round.

Future protected API contract:

```text
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")
```

App key and permission:

- Use existing `app_key = reservation_queue`.
- Use permission `reservation.check_in`.
- Do not introduce `reservation_checkin`, `checkin_app`, `arrival_app`, or any other new app key.
- Do not create a new permission model for CheckIn.

Context rules:

- `tenantId` must come from the trusted actor/server context.
- `storeId` must come from the path/server context.
- Request body must not supply or override `tenantId`.
- App Gate denial happens before business execution.
- App Gate denial must not change Reservation state.
- App Gate denial must not write CheckIn BusinessEvent, StateTransitionLog, AuditLog, or idempotency records.
- App Gate denial must write `app_gate_audit_logs` with `action = APP_GATE_DENIED`.

Future App Gate tests:

- Unauthorized app is denied before the CheckIn application service runs.
- Store not enabled for `reservation_queue` is denied before the CheckIn application service runs.
- Missing `reservation.check_in` permission is denied before the CheckIn application service runs.
- Authorized `reservation_queue` app with `reservation.check_in` permission may call the CheckIn application service.
- Denied requests write App Gate denial audit and do not mutate Reservation.

## 14. Test Contract

This round does not write tests. A later implementation round should cover the following.

Success:

- Confirmed Reservation CheckIn succeeds.
- Reservation status becomes `arrived`.
- `arrivedAt` is set from command when supplied.
- `arrivedAt` is set from application current time when command omits it.
- `reservation.arrived` event is written.
- StateTransitionLog writes `confirmed -> arrived`.
- AuditLog writes `reservation.check_in`.
- Idempotency is completed.

Idempotency:

- Completed same-hash replay returns previous result.
- In-progress same-hash returns retry later.
- Failed same-hash requires a new key.
- Same key with different hash returns conflict.
- Already-arrived with new key returns `alreadyArrived=true`.
- Already-arrived with new key does not duplicate BusinessEvent.
- Already-arrived with new key does not duplicate StateTransitionLog.
- Already-arrived with new key does not duplicate AuditLog.

Failure:

- App Gate denied before CheckIn application service.
- Reservation not found.
- Wrong Store scope.
- Store not found.
- Status `draft` cannot check in.
- Status `cancelled` cannot check in.
- Status `no_show` cannot check in.
- Status `completed` cannot check in.
- Status `seated` cannot check in.
- Persistence save failure.
- Audit write failure.
- Event write failure.
- Transition write failure.

App Gate future API tests:

- Unauthorized app denied.
- Store not enabled denied.
- Permission denied for missing `reservation.check_in`.
- Success path uses `app_key = reservation_queue` and `permission = reservation.check_in`.
- Denied request writes `APP_GATE_DENIED` to `app_gate_audit_logs`.
- Denied request does not mutate Reservation and does not write CheckIn event, transition, audit, or idempotency records.

Boundary:

- No QueueTicket created.
- No Seating created.
- No TableLock created.
- No ReservationPreassignment created.
- No CheckInEntity created.
- No `check_ins` table created.
- No new app key created.
- No No-show behavior implemented.
- No Cancellation behavior implemented.
- No API DTO created.
- No Controller created.
- No Vue UI created.
- No Migration changed.

## 15. Next Implementation Notes

Recommended next implementation sequence, only after this contract is accepted:

1. Add application tests first for `ReservationCheckInApplicationService`.
2. Reuse existing Store scope, idempotency, event, transition, and audit patterns from Reservation Create.
3. Keep `ReservationRepositoryPort` limited to `findById(StoreScope, ReservationId)` and `save(StoreScope, Reservation)` for this slice.
4. Implement `ReservationCheckInRule` and `ReservationStateMachine` for only `confirmed -> arrived`.
5. Use `reservation.arrived` as the BusinessEvent and `reservation.check_in` as the AuditLog operation.
6. Preserve already-arrived success-like behavior with no duplicate event, transition, or audit writes.
7. Do not add a `check_ins` table or `CheckInEntity`.
8. Do not implement Queue, Seating, Table assignment, No-show, or Cancellation.
9. Reuse App Gate `app_key = reservation_queue` and permission `reservation.check_in` in the later API layer.
10. Do not add a new App Gate app key or permission model.
11. Do not modify V001 migration.
12. Do not connect to production database.

V001 schema note:

- V001 already has `reservations`, `business_events`, `state_transition_logs`, `audit_logs`, and `idempotency_records`.
- V001 does not need a `check_ins` table for this contract.
- If a future implementation needs a dedicated physical Reservation `arrived_at` column and it does not already exist, that must be handled in a separate approved migration round. This contract does not authorize that migration.

## 16. Not Created In This Round

- No Java Application Service.
- No Repository implementation.
- No Controller.
- No API DTO.
- No Vue page.
- No Vue component.
- No Flyway migration.
- No SQL file.
- No App Gate app key.
- No permission model.
- No database structure change.
- No seed data.
- No production config.
- No production database access.
