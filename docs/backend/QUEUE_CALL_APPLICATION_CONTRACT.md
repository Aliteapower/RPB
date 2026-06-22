# Queue Call Application Contract V1

## 1. Purpose

This document defines the minimum application contract for calling an existing QueueTicket.

Business flow:

```text
QueueTicket status = waiting
-> Store staff calls the ticket
-> QueueTicket status = called
-> calledAt is recorded
-> holdUntilAt is resolved from Store queue-call hold policy
-> BusinessEvent, StateTransitionLog, AuditLog, and Idempotency are handled
```

This is a contract-only round. It does not create Java code, repository implementation, controller, API DTO, Vue UI, router entry, App Gate metadata, Flyway migration, SQL, seed data, production configuration, or database data.

## 2. Read Inputs

Reservation Arrived To Queue baseline:

- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_ARRIVED_TO_QUEUE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_TO_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_CONTRACT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_TO_QUEUE_UI_VALIDATION_REPORT.md`

Queue, schema, and domain inputs:

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

Store policy inputs:

- `src/main/java/com/rpb/reservation/store/domain/StorePolicy.java`
- `src/main/java/com/rpb/reservation/store/application/port/out/StorePolicyRepositoryPort.java`
- `src/main/java/com/rpb/reservation/store/application/port/out/StoreRepositoryPort.java`
- `src/main/java/com/rpb/reservation/store/persistence/entity/StorePolicyEntity.java`

Reservation and evidence inputs:

- `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/BusinessEventRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/StateTransitionLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/audit/application/port/out/AuditLogRepositoryPort.java`
- `src/main/java/com/rpb/reservation/idempotency/application/port/out/IdempotencyRepositoryPort.java`

Staff flow and App Gate inputs:

- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`
- `docs/frontend/RESERVATION_TODAY_VIEW_UI_VALIDATION_REPORT.md`
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

- Reservation Arrived To Queue has passed API, UI, and local DB validation.
- QueueTicket can currently be created with `status = waiting`.
- Reservation status remains `arrived` after entering the Queue.
- Current QueueTicket enum includes `waiting`, `called`, `skipped`, `rejoined`, `seated`, `cancelled`, and `expired`.
- Current QueueTicket state machine allows `waiting -> called`.
- V001 `queue_tickets` already contains `called_at` and `expires_at`.
- V001 `store_policies` already contains `queue_call_hold_minutes` with default `3`.
- No Queue Call, Queue Skip, Queue Rejoin, Queue Display Screen, or Seating from Queue implementation exists yet.

## 3. Selected Vertical Slice

Selected slice:

```text
Queue Call
```

Input precondition:

```text
queue_ticket.status = waiting
```

Required output:

```text
queue_ticket.status = called
queue_ticket.called_at = calledAt
queue_ticket.expires_at = holdUntilAt
reservation.status remains arrived when queue_ticket.reservation_id is present
business event written
queue_ticket transition evidence written
audit written
idempotency completed
```

This slice only calls a waiting QueueTicket. It does not seat the party, skip the ticket, rejoin the ticket, display a queue board, assign a table, create no-show/cancellation behavior, start Cleaning, or create Turnover.

## 4. Scope

In scope:

- Application command contract for calling an existing QueueTicket.
- Store-scoped QueueTicket lookup by `tenantId`, `storeId`, and `queueTicketId`.
- Validation that QueueTicket belongs to the Store scope.
- Validation that QueueTicket status is `waiting` for fresh call.
- Success-like `alreadyCalled = true` behavior for an already called ticket when durable call evidence exists.
- Optional Reservation lookup when QueueTicket has `reservation_id`.
- Reservation status decision: status remains `arrived`.
- Resolve `calledAt`.
- Resolve `holdUntilAt`.
- Persist QueueTicket call state in existing schema fields.
- BusinessEvent boundary.
- StateTransitionLog boundary.
- AuditLog boundary.
- App Gate boundary for a later API round.
- Idempotency boundary for `call_queue_ticket`.
- Failure and test contract for later implementation.

## 5. Non-Scope

Out of scope:

- Queue Skip.
- Queue Rejoin.
- Queue Display Screen.
- Queue waiting list API or UI.
- Seating from Queue.
- Reservation Arrived Direct Seating.
- Table assignment.
- Table lock.
- DiningTable mutation.
- Physical TableGroup occupancy mutation.
- Auto assignment.
- Recommended table.
- Table map.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- Reservation state-machine changes.
- Reservation `called` status.
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

## 6. QueueTicket Status Decision

Fresh Queue Call transition:

```text
waiting -> called
```

Rules:

- QueueTicket must already exist.
- QueueTicket must belong to the current Tenant and Store scope.
- Fresh call is allowed only from `waiting`.
- Current project state machine already supports `waiting -> called`.
- `called` is not a fresh-call source state; it is handled by the success-like `alreadyCalled` branch.
- `skipped`, `rejoined`, `seated`, `cancelled`, and `expired` are not valid for Queue Call V1.
- Current enum has no `completed` QueueTicket status. If a future enum adds it, it must be treated as a non-callable terminal status unless a later contract says otherwise.

Persisted values:

```text
queue_tickets.status = called
queue_tickets.called_at = calledAt
queue_tickets.expires_at = holdUntilAt
```

`expires_at` is the existing durable field used by this V1 contract for call-hold expiry. A future expiration slice may reuse or refine that field, but this round does not implement expiry.

## 7. Reservation Status Decision

Queue Call does not change Reservation status.

When QueueTicket has `reservation_id`:

```text
before: reservation.status = arrived
after:  reservation.status = arrived
```

Rules:

- Do not add `Reservation.status = called`.
- Do not modify `ReservationStateMachine`.
- Do not write a Reservation state transition for Queue Call.
- Queue calling state is expressed by QueueTicket, not Reservation.
- If related Reservation is present but missing, return a stable application-level error.
- If related Reservation is present but not `arrived`, return a stable consistency error and do not call the ticket.

When QueueTicket is not Reservation-sourced:

- This contract does not load WalkIn.
- Result reservation fields may be null.
- QueueTicket can still be called if it is Store-scoped and `waiting`.
- No WalkIn mutation is part of this contract.

## 8. Call Hold Policy

Store policy already contains:

```text
queue_call_hold_minutes
```

Domain and entity support already expose:

```text
StorePolicy.queueCallHoldMinutes
StorePolicyEntity.queueCallHoldMinutes
```

V1 calculation:

```text
holdUntilAt = calledAt + storePolicy.queueCallHoldMinutes
```

Default:

```text
queue call hold = 3 minutes
```

Rules:

- Resolve current Store policy by Store scope at `calledAt`.
- If a current Store policy is available, use `queueCallHoldMinutes`.
- If no Store policy is available, use the governance default of 3 minutes and include that source in metadata.
- `queueCallHoldMinutes` must be positive.
- `calledAt` and `holdUntilAt` are UTC instants and should cross API boundaries as ISO8601 in later API work.
- Do not add migration or new persistence fields in this round.

Recommended policy:

```text
QueueCallHoldPolicy
```

Recommended result metadata:

```text
queueCallHoldMinutes
holdPolicySource = store_policy | default
```

## 9. Command Contract

Command:

```text
CallQueueTicketCommand
```

Fields:

| Field | Source | Required | Notes |
| --- | --- | ---: | --- |
| `tenantId` | actor/server context | Yes | Trusted Tenant scope. |
| `storeId` | path/server context | Yes | Store operation boundary. |
| `queueTicketId` | caller/path/application input | Yes | Existing QueueTicket id. |
| `idempotencyKey` | caller/header/application input | Yes | Required for this critical command. |
| `actorId` | actor/server context | Yes | Staff actor id. |
| `actorType` | actor/server context | Yes | Expected value aligns with `staff` for this slice. |
| `calledAt` | application clock or trusted caller | No | If absent, application uses current time. |
| `reasonCode` | caller/application input | No | Optional audit context. |
| `note` | caller/application input | No | Optional staff note. |

Structural rules:

- `tenantId` is required.
- `storeId` is required.
- `queueTicketId` is required.
- `idempotencyKey` is required.
- `actorId` is required.
- `actorType` is required.
- `calledAt` is optional.
- If `calledAt` is absent, use the application clock once and reuse that resolved instant for all persistence, event, transition, audit, result, and idempotency snapshot writes.
- `tenantId`, `actorId`, `actorType`, role, permission, and Store access must come from trusted server context in a later API layer.
- Body `tenantId` must not be trusted in a later API layer.

Forbidden command fields:

- `reservationStatus`
- `reservationId` as a state-changing target
- `tableId`
- `tableGroupId`
- `seatingId`
- `cleaningId`
- `turnoverId`
- `skipReason`
- `rejoinReason`
- `noShowAt`
- `cancelledAt`
- `queueTicketNumber`
- client-provided `status`
- trusted `role`
- trusted `permission`

## 10. Application Result Contract

Recommended application result:

```text
QueueCallResult
```

Success fields should include at least:

- `success = true`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = called`
- `queueGroupId`
- `queueGroupCode` when available without extra broad query
- `reservationId` when present
- `reservationCode` when present
- `reservationStatus = arrived` when present
- `businessDate`
- `partySize`
- `queuePosition`
- `calledAt`
- `holdUntilAt`
- `alreadyCalled`
- `events`
- `idempotency.status`
- `idempotency.replayed`

Failure fields should include at least:

- `success = false`
- `error`
- `idempotency.status`
- `retryLater` when applicable

Terminology note:

- `alreadyCalled = true` is a success-like duplicate result and must not create a second event, transition, audit log, or status write.
- `holdUntilAt` is the external result name. The current durable field is `queue_tickets.expires_at`.

## 11. Application Service Boundary

Application service:

```text
QueueCallApplicationService
```

Method:

```text
callQueueTicket(CallQueueTicketCommand command)
```

Responsibilities:

1. Validate command presence and required fields.
2. Build `StoreScope` from `tenantId` and `storeId`.
3. Check idempotency using action `call_queue_ticket`.
4. Validate Store exists and actor can access the Store.
5. Load QueueTicket by `StoreScope + queueTicketId`.
6. Validate QueueTicket belongs to Store scope.
7. Detect already-called behavior.
8. Validate QueueTicket status is `waiting` for fresh call.
9. Load related Reservation when `reservation_id` exists.
10. Validate related Reservation remains `arrived` when present.
11. Resolve `calledAt`.
12. Resolve Store call-hold minutes.
13. Resolve `holdUntilAt`.
14. Apply QueueTicket state transition `waiting -> called`.
15. Save QueueTicket.
16. Write required BusinessEvent record.
17. Write required StateTransitionLog record.
18. Write required AuditLog record.
19. Complete idempotency with a replayable response snapshot.
20. Return application result.

Not responsible for:

- API parsing.
- HTTP status mapping.
- UI message text.
- Queue Skip.
- Queue Rejoin.
- Queue Display Screen.
- Queue list.
- Seating.
- Table assignment.
- Table lock.
- Cleaning start.
- Cleaning completion.
- Turnover.
- No-show.
- Cancellation.
- Reservation list/search/calendar.
- Migration or SQL design.
- App Gate metadata modification.

Transaction guidance:

- Fresh successful Queue Call should commit QueueTicket save, BusinessEvent, StateTransitionLog, AuditLog, and idempotency completion atomically.
- Reservation is not saved and its status must not change.
- Validation should happen before mutation whenever possible.
- If QueueTicket persistence, event, transition, audit, or idempotency completion fails, the application must return a stable application-level error and must not expose raw database exceptions.
- Audit write failure is blocking because Queue Call is a critical operation.
- Completed idempotency replay must not append duplicate events, transitions, audit logs, or QueueTicket updates.

## 12. Required Ports

Required ports:

| Port | Required capability for this slice |
| --- | --- |
| `StoreRepositoryPort` | Load Store by `StoreScope`, validate Store existence and operational scope. May also provide current Store policy through `findCurrentPolicy`. |
| `QueueTicketRepositoryPort` | Load QueueTicket by Store scope and id, save called QueueTicket. Existing `findNextCallable` can support future list/call selection but is not required by this manual-id contract. |
| `ReservationRepositoryPort` | Load related Reservation when `queueTicket.reservationId` is present. |
| `BusinessEventRepositoryPort` | Append `queue_ticket.called`. |
| `StateTransitionLogRepositoryPort` | Append QueueTicket `waiting -> called` transition evidence. |
| `AuditLogRepositoryPort` | Append success and required failure audit records. |
| `IdempotencyRepositoryPort` | Start, replay, complete, fail, and detect conflict for `call_queue_ticket`. |

Optional port:

| Port | Optional capability |
| --- | --- |
| `StorePolicyRepositoryPort` | Resolve current Store policy if implementation does not use `StoreRepositoryPort.findCurrentPolicy`. |

Forbidden ports:

- `DiningTableRepositoryPort`
- seating `TableGroupRepositoryPort`
- `TableLockRepositoryPort`
- `SeatingRepositoryPort`
- `CleaningRepositoryPort`
- `TurnoverRepositoryPort`
- broad BI/reporting repositories
- mechanical CRUD ports unrelated to this command

## 13. Required Rules / Policies / Validators

Reuse existing rules and validators where possible:

| Component | Purpose |
| --- | --- |
| `StoreAccessPolicy` | Actor must have Tenant and Store scope access. |
| `QueueTicketStateMachine` | Validate `waiting -> called`. |
| `QueueCallRule` | Accept fresh call only from `waiting`; define already-called behavior; reject other statuses. |
| `QueueCallHoldPolicy` | Resolve `holdUntilAt` from Store policy or default 3 minutes. |
| `AuditRule` | Require and shape `queue.call` audit. |
| `BusinessEventRule` | Require and validate event code and target. |
| `StateTransitionRule` | Require and validate QueueTicket call transition evidence. |
| `IdempotencyRule` | Apply replay, in-progress, failed-key, and conflict behavior. |

Forbidden components:

- `TableAssignmentRule`
- `SeatingSourceValidator`
- `SeatingResourceValidator`
- `DiningTableStateMachine`
- `NoShowPolicy`
- `CancellationPolicy`
- `AutoAssignmentPolicy`
- `CleaningReleasePolicy`
- `TurnoverPolicy`

## 14. State Boundary

QueueTicket:

```text
waiting -> called
```

Reservation when present:

```text
arrived -> arrived
```

No state transition in this slice:

- skips QueueTicket.
- rejoins QueueTicket.
- seats QueueTicket.
- cancels QueueTicket.
- expires QueueTicket.
- creates or updates Seating.
- starts Cleaning.
- creates Turnover.
- mutates DiningTable.
- mutates physical TableGroup occupancy.
- mutates Reservation status.

## 15. Event / Audit Boundary

Minimum required BusinessEvent code:

| Event type | Target type | Target id | Notes |
| --- | --- | --- | --- |
| `queue_ticket.called` | `queue_ticket` | QueueTicket id | QueueTicket has been called and call-hold timer has started. |

Do not write this in V1:

```text
reservation.queue_called
```

Minimum required AuditLog operation:

```text
queue.call
```

Recommended failure audit operation:

```text
queue.call.failed
```

Required audit metadata:

- `tenantId`
- `storeId`
- `queueTicketId`
- `queueTicketNumber`
- previous QueueTicket status
- current QueueTicket status
- `reservationId` when present
- `reservationCode` when present
- current Reservation status when present
- `queueGroupId`
- `businessDate`
- `partySize`
- `queuePosition`
- `calledAt`
- `holdUntilAt`
- `queueCallHoldMinutes`
- `holdPolicySource`
- `reasonCode`
- `note`
- `actorId`
- `actorType`
- `idempotencyKey`

Failure audit is best effort and must not mask the original application error, except that audit write failure for an accepted business mutation remains blocking.

## 16. StateTransitionLog Boundary

QueueTicket:

| Target type | From | To | Transition code |
| --- | --- | --- | --- |
| `queue_ticket` | `waiting` | `called` | `queue_ticket.call` |

Reservation:

- Do not write `arrived -> called`.
- Do not write `arrived -> seated`.
- It is acceptable to omit Reservation transition logging because status is unchanged.

## 17. Idempotency Boundary

Action:

```text
call_queue_ticket
```

Idempotency scope:

```text
tenant_id + store_id + source + action + idempotency_key
```

For this slice:

- `source = staff`
- `action = call_queue_ticket`

Request hash must include normalized command intent:

- `tenantId`
- `storeId`
- `queueTicketId`
- `actorId`
- `actorType`
- `calledAt` if provided, or an explicit absent marker when application clock should be used
- `reasonCode`
- `note`

The request hash must not use the resolved current time when `calledAt` was absent. Otherwise a retry with the same key would conflict only because time advanced.

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| missing | Start command and execute once. | Not applicable. |
| `completed` | Replay stored result with `replayed = true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` or in progress | Return retry-later application error. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |
| `expired` | Require new key unless a later retention policy explicitly allows reuse. | `IDEMPOTENCY_CONFLICT`. |

Completed replay must not update QueueTicket, write BusinessEvent, write StateTransitionLog, write AuditLog, change Reservation, change table state, or create Seating/Cleaning/Turnover records.

Failure behavior:

- Validation failures before idempotency start do not need an IdempotencyRecord.
- Failures after idempotency start should mark the record as `failed`.
- V1 failed idempotency requires a new key.

## 18. AlreadyCalled Boundary

Already called is success-like only when durable call evidence exists.

Condition:

```text
queue_ticket.status = called
and queue_ticket.called_at is present
and queue_ticket.expires_at is present
```

Behavior:

- Return success-like result with `alreadyCalled = true`.
- Return existing `calledAt` and `holdUntilAt`.
- Do not update QueueTicket.
- Do not change Reservation status.
- Do not append duplicate `queue_ticket.called`.
- Do not append duplicate `queue_ticket.call` transition.
- Do not append duplicate `queue.call` success audit.
- Complete the new idempotency record with the already-called response snapshot when duplicate detection happens under a new idempotency key.

If QueueTicket is `called` but `called_at` or `expires_at` is missing:

- Return `QUEUE_CALL_EVIDENCE_INCOMPLETE`.
- Do not write compensating call evidence in this contract.
- Do not append event, transition, or success audit.
- Mark idempotency failed if the failure happens after idempotency start.

## 19. App Gate Future Boundary

This section is for a later API implementation round. It does not authorize a Controller, DTO, route, permission registry change, migration, or frontend entry in this round.

Future API must use:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")
```

or the project-equivalent annotation shape.

Rules:

- `app_key` is fixed as `reservation_queue`.
- `permission` is fixed as `queue.call`.
- Do not create a new app key.
- Do not use `reservation.queue`.
- Do not use `reservation.call`.
- Do not use `queue.ticket.call`.
- `queue.call` is a future permission to add to the `reservation_queue` permission set in a separately approved metadata/code round.
- This contract does not modify `AppGateRequiredPermission`.
- This contract does not select or implement an API path.
- `tenantId` must come from trusted actor/server context.
- `storeId` must come from path or trusted server context.
- Body `tenantId` must not be trusted.
- App Gate deny happens before the application service runs.
- App Gate deny must not change QueueTicket, Reservation, BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord business data.
- App Gate deny must write `app_gate_audit_logs`.
- Deny action must be `APP_GATE_DENIED`.

## 20. Failure Cases

Failures must return stable application-level errors. Raw database exceptions must not cross the application service boundary.

Required failure coverage:

| Case | Application error |
| --- | --- |
| Command is null or missing required fields | `INVALID_COMMAND` |
| Missing idempotency key | `MISSING_IDEMPOTENCY_KEY` |
| Store not found | `STORE_NOT_FOUND` |
| Store scope mismatch | `STORE_SCOPE_MISMATCH` |
| Store access denied | `STORE_ACCESS_DENIED` |
| QueueTicket not found | `QUEUE_TICKET_NOT_FOUND` |
| QueueTicket belongs to another Store | `STORE_SCOPE_MISMATCH` |
| QueueTicket status is `waiting` | Success path. |
| QueueTicket status is `called` with complete evidence | Success-like `alreadyCalled = true`. |
| QueueTicket status is `called` with missing call evidence | `QUEUE_CALL_EVIDENCE_INCOMPLETE` |
| QueueTicket status is `skipped` | `QUEUE_TICKET_STATUS_NOT_WAITING` |
| QueueTicket status is `rejoined` | `QUEUE_TICKET_STATUS_NOT_WAITING` |
| QueueTicket status is `seated` | `QUEUE_TICKET_CANNOT_CALL_SEATED` |
| QueueTicket status is `cancelled` | `QUEUE_TICKET_CANNOT_CALL_CANCELLED` |
| QueueTicket status is `expired` | `QUEUE_TICKET_CANNOT_CALL_EXPIRED` |
| Related Reservation not found | `RESERVATION_NOT_FOUND` |
| Related Reservation not `arrived` | `RESERVATION_STATUS_NOT_ARRIVED` |
| Store policy minute value invalid | `QUEUE_CALL_HOLD_POLICY_INVALID` |
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

## 21. Test Contract

Future implementation tests should cover the following. This round does not write tests.

Success:

- Waiting QueueTicket is called.
- QueueTicket status becomes `called`.
- QueueTicket `calledAt` is returned.
- QueueTicket `holdUntilAt` is returned.
- `holdUntilAt = calledAt + storePolicy.queueCallHoldMinutes`.
- Store policy missing falls back to 3 minutes.
- Reservation status remains `arrived` when QueueTicket has Reservation source.
- `queue_ticket.called` event is written.
- `queue_ticket.call` transition is written.
- AuditLog operation `queue.call` is written.
- Idempotency is completed.

AlreadyCalled:

- QueueTicket already `called` with `called_at` and `expires_at` returns `alreadyCalled = true`.
- Already-called result is not displayed or treated as error by future API/UI.
- No duplicate BusinessEvent is written.
- No duplicate StateTransitionLog is written.
- No duplicate AuditLog is written.
- Missing call evidence returns `QUEUE_CALL_EVIDENCE_INCOMPLETE`.

Idempotency:

- Completed replay returns stored result.
- Completed replay does not update QueueTicket.
- Completed replay does not duplicate event, transition, or audit.
- In-progress same hash returns retry later.
- Failed same hash requires a new key.
- Same key with different hash returns conflict.

Failure:

- QueueTicket not found.
- QueueTicket not waiting.
- QueueTicket skipped.
- QueueTicket rejoined.
- QueueTicket seated.
- QueueTicket cancelled.
- QueueTicket expired.
- Related Reservation not found.
- Related Reservation not arrived.
- Invalid hold policy value.
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
- No Queue Skip behavior.
- No Queue Rejoin behavior.
- No Queue Display Screen behavior.
- No No-show behavior.
- No Cancellation behavior.
- No Reservation state mutation.
- No API implementation.
- No UI implementation.
- No migration change.

Future App Gate API tests:

- Allowed request uses `app_key = reservation_queue` and `permission = queue.call`.
- Tenant not entitled is denied before business handler.
- Store app disabled is denied before business handler.
- Missing `queue.call` permission is denied before business handler.
- Deny writes `APP_GATE_DENIED`.
- Deny does not mutate QueueTicket, Reservation, events, transitions, audit, or idempotency for this business command.

## 22. Implementation Prerequisites

No code is changed in this contract round. A future implementation round should account for these current gaps:

- `QueueTicketEntity` maps `called_at` and `expires_at`, but `QueueTicket` domain currently does not expose `calledAt` or `expiresAt`.
- `DefaultQueueTicketMapper.toDomain` currently drops `called_at` and `expires_at`.
- `DefaultQueueTicketMapper.toEntity` currently writes null for `called_at`, `skipped_at`, `rejoined_at`, and `expires_at`.
- `QueueTicketPersistenceAdapter.save` can update an existing QueueTicket, but the domain/mapper must preserve call fields before Queue Call can persist them.
- `AppGateRequiredPermission` does not currently include `queue.call`; this is expected because this round must not modify App Gate metadata.
- Future API path and DTO shape must be designed separately before controller implementation.

## 23. Next Implementation Notes

Recommended next allowed round:

```text
Queue Call Application Implementation
```

Implementation should:

- Add application tests first.
- Add `CallQueueTicketCommand`.
- Add `QueueCallApplicationService`.
- Add `QueueCallResult`.
- Add `QueueCallError`.
- Add or supplement `QueueCallRule`.
- Add `QueueCallHoldPolicy`.
- Extend QueueTicket domain and mapper to preserve `calledAt` and `expiresAt`.
- Reuse `QueueTicketStateMachine` for `waiting -> called`.
- Reuse Store policy support for `queueCallHoldMinutes`.
- Keep Queue Skip, Queue Rejoin, Queue Display Screen, Seating from Queue, Table assignment, Cleaning, Turnover, No-show, Cancellation, API, UI, App Gate registry change, migration, SQL, seed data, and production data outside the implementation round unless separately approved.

Future API round should:

- Design endpoint path separately.
- Use `@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")`.
- Add `queue.call` to App Gate permission metadata only in an approved metadata/code round.
- Continue ignoring `body.tenantId` for scope.

Future UI round should:

- Design Queue Call UI separately.
- Keep UI from seating the party.
- Keep Queue Display Screen out of scope unless explicitly approved.
- Display backend `error.code` and `error.messageKey` if exposed through future API.

## 24. Open Questions

- Should Queue Call V1 support WalkIn-sourced QueueTickets in the result with WalkIn fields, or keep non-Reservation source output limited to QueueTicket fields until a separate WalkIn Queue contract exists?
- Should `expires_at` be named `holdUntilAt` in future API response while persisting to the existing `expires_at` column, or should future API expose both names for support diagnostics?
- Should Queue Call choose the next callable ticket by QueueGroup in a later list/workbench slice, or should V1 API require explicit `queueTicketId` only?

## 25. Open Conflicts

- The Product Owner input says `called` is not a valid source state for calling, while the idempotency section also requires `alreadyCalled = true`. This contract resolves the distinction as:
  - `called` is not a valid fresh transition source.
  - `called` with durable call evidence is a success-like duplicate result.
  - `called` without durable call evidence is `QUEUE_CALL_EVIDENCE_INCOMPLETE`.

## 26. Not Created In This Round

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
- No Queue Call implementation.
- No Queue Skip.
- No Queue Rejoin.
- No Queue Display Screen.
- No Seating from Queue.
- No table assignment.
- No table lock.
- No Cleaning.
- No Turnover.
- No No-show.
- No Cancellation.
- No seed data.
- No production configuration.
- No production database access.
