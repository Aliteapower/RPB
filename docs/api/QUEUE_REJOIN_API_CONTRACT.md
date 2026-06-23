# Queue Rejoin API Contract V1

## 1. Purpose

Define the future REST API contract for rejoining a skipped QueueTicket.

Business meaning:

```text
Queue Rejoin = 过号后重新入队 / 恢复排队
```

Target flow:

```text
POST rejoin API
-> App Gate reservation_queue / queue.rejoin
-> Queue Rejoin application service
-> QueueTicket skipped -> waiting
-> original ticket number remains unchanged
-> ticket is placed at the tail of the same queue group
-> Reservation remains arrived
-> rejoinedAt is persisted through existing queue_tickets.rejoined_at
-> BusinessEvent / StateTransitionLog / AuditLog written
-> Idempotency completed
-> API returns Queue Rejoin result
```

This document is contract only. It does not implement a backend API, application service, DTO, mapper,
error class, App Gate metadata, local runtime allowlist, frontend API client, UI, migration, SQL, seed
data, runtime fixture, or production data change.

## 2. Read Inputs

Documents read before this contract:

- `docs/api/QUEUE_SKIP_API_CONTRACT.md`
- `docs/api/QUEUE_SKIP_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/QUEUE_SKIP_UI_CONTRACT.md`
- `docs/frontend/QUEUE_LIST_UI_CONTRACT.md`
- `docs/api/QUEUE_CALL_API_IMPLEMENTATION_REPORT.md`
- `docs/api/SEATING_FROM_CALLED_QUEUE_API_IMPLEMENTATION_REPORT.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/api-review/SKILL.md`
- `docs/skills/database-review/SKILL.md`
- `docs/skills/tdd-review/SKILL.md`
- `docs/skills/code-review/SKILL.md`

Implementation patterns inspected without modification:

- `src/main/java/com/rpb/reservation/queue/api/*`
- `src/test/java/com/rpb/reservation/queue/api/*`
- `src/main/java/com/rpb/reservation/appgate/*`
- `src/test/java/com/rpb/reservation/appgate/*`
- `src/main/java/com/rpb/reservation/queue/domain/QueueTicket.java`
- `src/main/java/com/rpb/reservation/queue/status/QueueTicketStatus.java`
- `src/main/java/com/rpb/reservation/queue/state/QueueTicketStateMachine.java`
- `src/main/java/com/rpb/reservation/queue/persistence/entity/QueueTicketEntity.java`
- `src/main/java/com/rpb/reservation/queue/persistence/mapper/DefaultQueueTicketMapper.java`
- `src/main/java/com/rpb/reservation/queue/persistence/adapter/QueueTicketPersistenceAdapter.java`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

## 3. Product Decisions

| Decision | V1 Contract |
| --- | --- |
| Rejoin source status | Only `skipped` tickets may perform a fresh rejoin mutation. |
| Rejoin target status | V1 externally persists and returns `waiting`. |
| Queue position | Rejoin places the ticket at the tail of the same QueueGroup and business date. |
| Ticket number | Keep the original `queueTicketNumber`; do not create a new QueueTicket. |
| Multiple rejoin | Fresh mutation is allowed only when current status is `skipped`. `waiting`, `called`, `seated`, `cancelled`, `expired`, unknown, and terminal states return stable invalid-state behavior unless the already-rejoined recovery branch applies. |
| Reason / note | V1 request body supports optional `note` only. No `reasonCode` in V1. |
| Idempotency | `Idempotency-Key` is required. Completed replay returns `200`. Conflict, in-progress, and failed-key reuse follow existing Queue Skip idempotency behavior. |
| Evidence completeness | Complete rejoin evidence returns stable success without duplicate side effects. Partial evidence returns `QUEUE_REJOIN_EVIDENCE_INCOMPLETE`. |

State-machine note:

- The current `QueueTicketStateMachine` allows `SKIPPED -> REJOINED` and `REJOINED -> WAITING`, not a direct `SKIPPED -> WAITING`.
- This API contract selects the V1 external final state `waiting`.
- A future implementation must either add an approved direct `SKIPPED -> WAITING` transition for the `queue_ticket.rejoin` command, or use an internal `SKIPPED -> REJOINED -> WAITING` transition decision while exposing one API command result.
- This contract slice does not modify state-machine code.

## 4. Endpoint

Method:

```http
POST
```

Path:

```http
/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
```

Path parameters:

| Name | Required | Source |
| --- | --- | --- |
| `storeId` | yes | Store route scope |
| `queueTicketId` | yes | Target QueueTicket |

Do not create these paths in V1:

```text
POST /api/v1/stores/{storeId}/queue/rejoin
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
POST /api/v1/stores/{storeId}/queue/workbench/rejoin
GET /api/v1/stores/{storeId}/queue-display
```

## 5. Headers

Required:

```http
Idempotency-Key: <key>
```

Optional standard request headers:

```http
Content-Type: application/json
Accept: application/json
```

Missing or blank `Idempotency-Key` returns:

```text
400 MISSING_IDEMPOTENCY_KEY
```

## 6. Request Body

V1 request body allowlist:

```json
{
  "note": "Customer returned and should wait again"
}
```

Field rules:

| Field | Type | Required | Behavior |
| --- | --- | --- | --- |
| `note` | string | no | Trim before use. Blank note is treated as absent. Stored only in metadata/audit if provided. |

An empty object is valid:

```json
{}
```

Forbidden body fields:

```text
tenantId
storeId
actorId
actorType
queueTicketId
reservationId
tableId
tableGroupId
seatingId
status
targetStatus
queuePosition
ticketNumber
rejoinedAt
skippedAt
calledAt
reasonCode
skip
rejoin
noShow
cancellation
cleaning
turnover
mutation action
```

Request body must not decide Tenant, Store, actor, status, target status, queue placement, ticket number,
reservation state, table assignment, seating, no-show, cancellation, cleaning, or turnover behavior.

## 7. Response Body

V1 response shape aligns with the existing Queue Skip response style: flat top-level fields plus `events`
and `idempotency`. It intentionally does not introduce a nested `queueTicket` / `reservation` DTO shape.

Fresh success:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "waiting",
  "queuePosition": 42,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260623-1001",
  "reservationStatus": "arrived",
  "rejoinedAt": "2026-06-23T10:00:00Z",
  "alreadyRejoined": false,
  "events": ["queue_ticket.rejoined"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already rejoined with complete evidence:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "waiting",
  "queuePosition": 42,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260623-1001",
  "reservationStatus": "arrived",
  "rejoinedAt": "2026-06-23T10:00:00Z",
  "alreadyRejoined": true,
  "events": [],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Completed idempotency replay returns `200` with `idempotency.replayed = true` and must not duplicate
mutation evidence.

Response field rules:

| Field | Required | Notes |
| --- | --- | --- |
| `success` | yes | `true` for fresh success, already-rejoined success, and completed replay. |
| `queueTicketId` | yes | Target QueueTicket id. |
| `queueTicketNumber` | yes | Original ticket number; never regenerated by rejoin. |
| `queueTicketStatus` | yes | `waiting` for V1 success response. |
| `queuePosition` | yes | Effective tail position after rejoin. |
| `reservationId` | yes for V1 | V1 follows current Queue Skip response style and requires a related Reservation. |
| `reservationCode` | yes for V1 | Returned for Store staff context. |
| `reservationStatus` | yes for V1 | Remains `arrived`. |
| `rejoinedAt` | yes | Effective UTC instant from application clock. Client does not send it. |
| `alreadyRejoined` | yes | `true` only for complete-evidence recovery branch. |
| `events` | yes | Fresh success contains `queue_ticket.rejoined`; already-rejoined and replay contain no new event. |
| `idempotency` | yes | Uses the existing API idempotency response shape. |

WalkIn-backed QueueTicket rejoin is not included in V1 because the selected response follows the approved
Queue Skip Reservation-summary response style. A later contract may add source-polymorphic response fields.

## 8. Error Response

Error format:

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_TICKET_STATUS_NOT_SKIPPED",
    "messageKey": "queue.rejoin.queue_ticket_status_not_skipped",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Raw database exceptions must not be exposed.

Stable API errors:

| Application condition | HTTP | API code | messageKey |
| --- | ---: | --- | --- |
| Missing idempotency key | 400 | `MISSING_IDEMPOTENCY_KEY` | `queue.rejoin.missing_idempotency_key` |
| Invalid command | 400 | `INVALID_COMMAND` | `queue.rejoin.invalid_command` |
| Store not found | 404 | `STORE_NOT_FOUND` | `queue.rejoin.store_not_found` |
| Actor store access denied | 403 | `STORE_SCOPE_MISMATCH` | `queue.rejoin.store_scope_mismatch` |
| QueueTicket not found | 404 | `QUEUE_TICKET_NOT_FOUND` | `queue.rejoin.queue_ticket_not_found` |
| QueueTicket belongs to another Store scope | 403 | `QUEUE_TICKET_STORE_SCOPE_MISMATCH` | `queue.rejoin.queue_ticket_store_scope_mismatch` |
| QueueTicket not skipped for fresh rejoin | 409 | `QUEUE_TICKET_STATUS_NOT_SKIPPED` | `queue.rejoin.queue_ticket_status_not_skipped` |
| Skip or rejoin evidence incomplete | 409 | `QUEUE_REJOIN_EVIDENCE_INCOMPLETE` | `queue.rejoin.queue_rejoin_evidence_incomplete` |
| Reservation not found | 404 | `RESERVATION_NOT_FOUND` | `queue.rejoin.reservation_not_found` |
| Reservation not arrived | 409 | `RESERVATION_STATUS_NOT_ARRIVED` | `queue.rejoin.reservation_status_not_arrived` |
| Queue ordering conflict | 409 | `QUEUE_REJOIN_POSITION_CONFLICT` | `queue.rejoin.queue_rejoin_position_conflict` |
| Illegal state transition | 409 | `ILLEGAL_STATE_TRANSITION` | `queue.rejoin.illegal_state_transition` |
| Idempotency completed with different request hash | 409 | `IDEMPOTENCY_CONFLICT` | `queue.rejoin.idempotency_conflict` |
| Idempotency key is already in progress | 409 | `IDEMPOTENCY_IN_PROGRESS` | `queue.rejoin.idempotency_in_progress` |
| Failed idempotency key reused | 409 | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `queue.rejoin.idempotency_failed_requires_new_key` |
| Business event / transition / audit write failure | 500 | `QUEUE_REJOIN_WRITE_FAILED` | `queue.rejoin.write_failed` |
| QueueTicket or idempotency persistence failure | 500 | `QUEUE_REJOIN_PERSISTENCE_FAILED` | `queue.rejoin.persistence_failed` |
| Unknown server error | 500 | `UNKNOWN_ERROR` | `queue.rejoin.unknown_error` |

Product shorthand `failed-key reuse` maps to the existing Queue Skip naming pattern:

```text
IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY
```

App Gate denial uses the existing App Gate error envelope and codes:

```text
TENANT_APP_NOT_ENABLED
STORE_APP_NOT_ENABLED
PERMISSION_DENIED
```

## 9. App Gate

Future controller annotation:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.rejoin")
```

Permission metadata:

```text
queue.rejoin belongs to reservation_queue
```

Denial behavior:

| Condition | HTTP | Behavior |
| --- | ---: | --- |
| Tenant app not enabled | 403 | Existing App Gate denial response. |
| Store app disabled | 403 | Existing App Gate denial response. |
| Actor lacks `queue.rejoin` | 403 | Existing App Gate denial response. |

Denied requests must:

- write deny audit through the existing App Gate denial path;
- not call the Queue Rejoin application service;
- not create or update QueueTicket business data;
- not write BusinessEvent, StateTransitionLog, AuditLog, or idempotency records for the rejoin command;
- not mutate Reservation, Seating, SeatingResource, Table, Cleaning, Turnover, No-show, or Cancellation state.

Metadata impact for future implementation:

- Add `queue.rejoin` to App Gate permission metadata in a separately approved implementation slice.
- Do not create a new `app_key`.
- Do not create a new permission model.
- Do not implement metadata changes in this contract slice.

## 10. Status and Queue Placement Behavior

Fresh rejoin source:

```text
queue_tickets.status = skipped
```

Fresh rejoin output:

```text
queue_tickets.status = waiting
queue_tickets.rejoined_at = effectiveRejoinedAt
queue_tickets.queue_position = same QueueGroup tail
queue_tickets.ticket_number unchanged
```

Call-window fields:

- Since V1 final status is `waiting`, `called_at` and `expires_at` no longer represent an active call window.
- Future implementation should clear `called_at` and `expires_at` or otherwise ensure Queue List and future call logic do not treat the old call window as active.
- Historical call/skip evidence remains in BusinessEvent, StateTransitionLog, AuditLog, and `skipped_at`.

Reservation:

```text
reservation.status remains arrived
```

The API must not persist:

```text
reservation.status = rejoined
reservation.status = waiting
reservation.status = cancelled
reservation.status = no_show
reservation.status = seated
```

Queue group:

- Rejoin stays in the existing QueueGroup.
- Rejoin uses the existing business date.
- Rejoin does not move party-size group.
- Rejoin does not create a new queue number.

## 11. rejoinedAt Behavior

Current schema support:

- V001 `queue_tickets.rejoined_at timestamptz null` exists.
- `QueueTicketEntity` exposes `rejoinedAt`.
- No migration is required for durable `rejoinedAt`.

Current source-code gap:

- `QueueTicket` domain currently does not expose `rejoinedAt`.
- `DefaultQueueTicketMapper.toEntity(...)` currently writes `rejoined_at = null`.
- Future implementation must update the domain/mapper/persistence boundary or use a dedicated repository update method that preserves durable `rejoined_at`.
- Future implementation must not use the current generic mapper path in a way that clears existing `rejoined_at`.

V1 decision:

- Frontend and API clients do not send `rejoinedAt`.
- Application clock supplies `effectiveRejoinedAt`.
- Fresh success persists `queue_tickets.rejoined_at = effectiveRejoinedAt`.
- Response returns the effective UTC `rejoinedAt`.

If a future deployment somehow lacks `queue_tickets.rejoined_at`, implementation must stop and report a
schema conflict. It must not add a migration inside the API implementation slice unless a later approved
migration round explicitly allows it.

## 12. Idempotency Behavior

Header:

```text
Idempotency-Key
```

Application action:

```text
rejoin_queue_ticket
```

Stable request hash fields:

- `tenantId` from actor context.
- `storeId` from path / trusted server context.
- `queueTicketId` from path.
- `actorId` from actor context.
- normalized `actorType`.
- normalized `note`.
- literal `application_clock` for `rejoinedAt` because clients do not send it.

Rules:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| none | Start idempotency, execute command, complete snapshot. | Not applicable. |
| `completed` | Replay stored result with HTTP 200 and `idempotency.replayed = true`. | Return `IDEMPOTENCY_CONFLICT`. |
| `started` / `in_progress` | Return `IDEMPOTENCY_IN_PROGRESS`. | Return `IDEMPOTENCY_CONFLICT`. |
| `failed` | Return `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | Return `IDEMPOTENCY_CONFLICT`. |
| `expired` | Follow the shared idempotency rule; do not mutate without an explicit accepted rule. | Return conflict unless the shared rule says otherwise. |

Completed replay must not duplicate:

- QueueTicket mutation.
- Queue position movement.
- BusinessEvent.
- StateTransitionLog.
- AuditLog.
- Reservation mutation.
- Seating / SeatingResource.
- DiningTable mutation.
- Cleaning.
- Turnover.
- No-show.
- Cancellation.

Response snapshot must include at least:

- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus = waiting`
- `queuePosition`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `rejoinedAt`
- `alreadyRejoined`

## 13. alreadyRejoined Behavior

V1 does not allow duplicate fresh rejoin mutations. However, it must handle already-completed rejoin
evidence safely.

Complete already-rejoined evidence requires:

- `queue_tickets.status = waiting`;
- `queue_tickets.rejoined_at is not null`;
- original `queue_tickets.ticket_number` is unchanged;
- current `queue_position` is present and represents an active waiting placement;
- existing `queue_ticket.rejoined` BusinessEvent;
- existing `queue_ticket.rejoin` StateTransitionLog;
- existing `queue.rejoin` AuditLog;
- no later terminal QueueTicket transition after the rejoin evidence.

If complete evidence exists:

- Return HTTP `200`.
- `alreadyRejoined = true`.
- `events = []`.
- Complete the new idempotency key with an already-rejoined response snapshot.
- Do not rewrite QueueTicket.
- Do not move queue position again.
- Do not duplicate BusinessEvent, StateTransitionLog, or AuditLog.
- Do not mutate Reservation, Seating, SeatingResource, Table, Cleaning, Turnover, No-show, or Cancellation.

If evidence is incomplete:

```text
QUEUE_REJOIN_EVIDENCE_INCOMPLETE
```

If current status is `waiting` but there is no rejoin evidence, return:

```text
QUEUE_TICKET_STATUS_NOT_SKIPPED
```

If current status is `called`, `seated`, `cancelled`, `expired`, or unknown, return:

```text
QUEUE_TICKET_STATUS_NOT_SKIPPED
```

## 14. Side-Effect Contract

Successful fresh rejoin writes exactly one new command outcome:

- QueueTicket status changes from `skipped` to `waiting`.
- QueueTicket `rejoined_at` is persisted.
- QueueTicket `queue_position` is moved to same-group tail.
- QueueTicket original `ticket_number` remains unchanged.
- Reservation remains `arrived`.
- BusinessEvent is written once.
- StateTransitionLog is written once.
- AuditLog is written once.
- IdempotencyRecord is written once and completed.

BusinessEvent:

```text
event_type = queue_ticket.rejoined
target_type = queue_ticket
target_id = queueTicketId
```

Recommended metadata:

- `queueTicketId`
- `queueTicketNumber`
- `beforeQueueTicketStatus = skipped`
- `afterQueueTicketStatus = waiting`
- `reservationId`
- `reservationCode`
- `reservationStatus = arrived`
- `queueGroupId`
- `businessDate`
- `partySize`
- `previousQueuePosition`
- `newQueuePosition`
- `skippedAt`
- `rejoinedAt`
- `note`
- `idempotencyKey`
- `alreadyRejoined`

StateTransitionLog:

```text
target_type = queue_ticket
from_status = skipped
to_status = waiting
transition_code = queue_ticket.rejoin
```

AuditLog:

```text
operation_code = queue.rejoin
target_type = queue_ticket
target_id = queueTicketId
```

Failure audit, if written by the future application service:

```text
operation_code = queue.rejoin.failed
target_type = queue_ticket
target_id = queueTicketId
```

Failure audit must not hide the original stable API error.

## 15. Forbidden Side Effects

Queue Rejoin V1 must not:

- create a new QueueTicket;
- regenerate or change the queue ticket number;
- move to a different QueueGroup;
- create Queue Display state;
- create Queue Workbench state;
- change Queue Skip behavior;
- change existing Queue Call or Seating endpoints;
- cancel Reservation;
- mark Reservation as no-show;
- seat Reservation or QueueTicket;
- create Seating;
- create SeatingResource;
- mutate DiningTable;
- change Table status;
- create or mutate Cleaning;
- create or mutate Turnover;
- create No-show behavior;
- create Cancellation behavior;
- change migrations;
- change seed data;
- access production database;
- change existing API paths.

## 16. Future Implementation Test Contract

Controller and DTO tests:

- Controller route maps `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`.
- `Idempotency-Key` is required.
- Null body and `{}` are accepted.
- Request body allowlist contains only `note`.
- Forbidden body fields are rejected or impossible through DTO.
- Path `storeId` and `queueTicketId` map to command.
- Actor tenant, actor id, actor type, roles, permissions, and Store access come from current actor context.
- Response mapping follows the flat Queue Skip style.
- Error mapping returns stable `queue.rejoin.*` message keys.

App Gate tests:

- Controller method has `@RequireAppGate(appKey = "reservation_queue", permission = "queue.rejoin")`.
- `queue.rejoin` is added to `RESERVATION_QUEUE_ENTRY_PERMISSIONS` in the future implementation slice.
- `/api/me/apps` exposes `queue.rejoin` under `reservation_queue` when actor has it.
- Tenant denied returns 403 and writes deny audit.
- Store disabled returns 403 and writes deny audit.
- Permission denied returns 403 and writes deny audit.
- App Gate denial does not call application service and does not mutate business data.

Integration tests:

- Skipped QueueTicket rejoins successfully.
- QueueTicket status becomes `waiting`.
- `queue_tickets.rejoined_at` is persisted.
- Queue position moves to same QueueGroup tail.
- Original ticket number is unchanged.
- Reservation remains `arrived`.
- BusinessEvent `queue_ticket.rejoined` is written once.
- StateTransitionLog `queue_ticket.rejoin` is written once.
- AuditLog `queue.rejoin` is written once.
- IdempotencyRecord action `rejoin_queue_ticket` is completed.
- AlreadyRejoined with complete evidence returns success without duplicate writes.
- AlreadyRejoined with incomplete evidence returns `QUEUE_REJOIN_EVIDENCE_INCOMPLETE`.
- Completed idempotency replay returns 200 and does not duplicate evidence.
- Idempotency conflict returns stable 409.
- Idempotency in progress returns stable 409.
- Failed-key reuse returns stable 409.
- Missing idempotency key returns 400.
- QueueTicket not found returns 404.
- QueueTicket Store scope mismatch returns 403.
- Waiting/called/seated/cancelled/expired/unknown statuses return stable invalid-state error.
- Reservation not found and Reservation not arrived are mapped.
- Queue ordering conflict is mapped.
- Persistence/write failure is mapped without raw database exception exposure.

DB side-effect boundary tests:

- No new QueueTicket is created.
- No ticket number is regenerated.
- No duplicate BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord on replay.
- No Reservation cancellation.
- No no-show mutation.
- No Seating mutation.
- No SeatingResource creation.
- No DiningTable mutation.
- No Table status mutation.
- No Cleaning mutation.
- No Turnover mutation.
- No Queue Display or Queue Workbench mutation.
- No migration or seed data.

Local runtime security tests, if the future implementation updates local/test allowlist:

- Local/test profile allows the route only for a configured local actor with `queue.rejoin`.
- Local/test profile does not broaden unrelated queue, seating, table, cleaning, no-show, cancellation, or turnover paths.

Boundary tests:

- Existing approved boundary tests are updated only to allow approved Queue Rejoin API artifacts.
- Queue Rejoin frontend API client remains forbidden until a separate frontend slice.
- Queue Rejoin UI remains forbidden until a separate UI slice.
- Queue Display, Workbench, Queue Call from list, Queue Seat from list, Seating/Table/Calendar, No-show, Cancellation, Cleaning, Turnover, and migrations remain forbidden.

## 17. Non-Scope

This contract does not implement:

- Queue Rejoin backend API.
- Queue Rejoin service.
- Queue Rejoin DTOs.
- Queue Rejoin mapper.
- Queue Rejoin error code classes.
- Queue Rejoin tests.
- Queue Rejoin UI.
- Queue Rejoin frontend API client.
- Queue Display API.
- Queue Workbench mutation.
- Queue Call from list.
- Queue Seat from list.
- Seating UI.
- Table status.
- Table map.
- Reservation Calendar.
- No-show API.
- Cancellation API.
- Cleaning changes.
- Turnover API.
- Migration.
- Seed data.
- Production database changes.
- GitHub remote.
- GitHub push.
- Maven Wrapper.

## 18. Future Implementation Notes

Future backend implementation should:

- Add API layer classes mirroring Queue Skip naming style, such as `RejoinQueueTicketRequest`, `RejoinQueueTicketResponse`, `QueueRejoinController`, `QueueRejoinApiErrorCode`, `QueueRejoinApiErrorMapper`, and `QueueRejoinApiMapper`.
- Add application layer classes only in an approved implementation slice.
- Add `queue.rejoin` to App Gate permission metadata only in that implementation slice.
- Add a narrow local/test runtime allowlist only if local runtime security tests require it.
- Reuse shared idempotency, Store scope, audit, event, state transition, and queue ordering patterns.
- Resolve the `rejoined_at` domain/mapper/persistence gap before claiming durable rejoin behavior.
- Avoid migrations unless a future approved schema slice discovers a real mismatch.

## 19. Open Questions

- Should a later version support WalkIn-backed QueueTicket rejoin with source-polymorphic response fields?
- Should a later UI expose staff note for Rejoin, or should the UI send `{}` like Queue Skip UI V1?
- Should future implementation write one `skipped -> waiting` StateTransitionLog or two internal logs (`skipped -> rejoined`, `rejoined -> waiting`) if the state machine keeps `rejoined` as an intermediate state? This API contract selects one external command outcome log.

## 20. Open Conflicts

- Current `QueueTicketStateMachine` does not directly allow `SKIPPED -> WAITING`; this contract selects `waiting` as the V1 external final state and leaves the state-machine implementation adjustment to a future approved implementation slice.
- Current `QueueTicket` domain and `DefaultQueueTicketMapper` do not preserve `rejoinedAt`; future implementation must resolve this before durable rejoin can be claimed.
- `queue.rejoin` is referenced by governance and handoff docs but is not currently present in `AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS`; future implementation must add it.

## 21. Boundary Confirmation

Queue Rejoin API implemented: No  
Queue Rejoin UI implemented: No  
Queue Rejoin frontend API client implemented: No  
Queue Display API implemented: No  
Queue Workbench mutation implemented: No  
Queue Call from list implemented: No  
Queue Seat from list implemented: No  
Seating implemented: No  
Table status changed: No  
Table map implemented: No  
Reservation Calendar implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
Cleaning changed: No  
Turnover API implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No  
GitHub remote added: No  
GitHub push performed: No  
Maven Wrapper added: No  
