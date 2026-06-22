# Queue Skip API Contract V1

## 1. Purpose

Expose a REST API for skipping an already called QueueTicket.

Flow:

```text
POST skip API
-> App Gate reservation_queue / queue.skip
-> QueueSkipApplicationService
-> QueueTicket called -> skipped
-> Reservation remains arrived
-> skippedAt persisted
-> BusinessEvent / StateTransitionLog / AuditLog written
-> Idempotency completed
-> API returns Queue Skip result
```

This API is a mutation endpoint. It does not implement Queue Rejoin, Queue Display, Queue Workbench, Queue Call from list, Queue Seat from list, Seating, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, UI, migration, SQL, seed data, or production data changes.

## 2. Endpoint

Method:

```http
POST
```

Path:

```http
/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

Path parameters:

| Name | Required | Source |
| --- | --- | --- |
| `storeId` | yes | Store route scope |
| `queueTicketId` | yes | Target QueueTicket |

Do not create these paths in V1:

```text
POST /api/v1/stores/{storeId}/queue/skip
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
GET /api/v1/stores/{storeId}/queue-display
POST /api/v1/stores/{storeId}/queue/workbench/skip
```

## 3. Headers

Required:

```http
Idempotency-Key: <key>
```

Optional standard request headers:

```http
Content-Type: application/json
Accept: application/json
```

Missing or blank `Idempotency-Key` returns `400 MISSING_IDEMPOTENCY_KEY`.

## 4. Request Body

All fields are optional:

```json
{
  "skippedAt": "2026-06-22T11:45:00Z",
  "reasonCode": "NO_RESPONSE",
  "note": "Customer did not return after call"
}
```

Field rules:

| Field | Type | Required | Behavior |
| --- | --- | --- | --- |
| `skippedAt` | ISO8601 instant | no | If absent, application clock supplies the effective skip time. |
| `reasonCode` | string | no | Trimmed and passed through. V1 does not validate reason type. |
| `note` | string | no | Trimmed and passed through. |

Forbidden body fields:

```text
tenantId
storeId
queueTicketId
reservationId
tableId
tableGroupId
seatingId
cleaningId
turnoverId
rejoinReason
noShowAt
cancelledAt
status
actorId
actorType
mutation action
```

## 5. Response Body

Fresh success:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "skipped",
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260622-2296",
  "reservationStatus": "arrived",
  "skippedAt": "2026-06-22T11:45:00Z",
  "alreadySkipped": false,
  "events": ["queue_ticket.skipped"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already skipped with complete evidence:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "skipped",
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260622-2296",
  "reservationStatus": "arrived",
  "skippedAt": "2026-06-22T11:45:00Z",
  "alreadySkipped": true,
  "events": [],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Completed idempotency replay returns `200` with `idempotency.replayed = true` and does not duplicate mutation evidence.

## 6. Error Response

Error format:

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_TICKET_STATUS_NOT_CALLED",
    "messageKey": "queue.skip.queue_ticket_status_not_called",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Stable API errors:

| Application condition | HTTP | API code | messageKey |
| --- | ---: | --- | --- |
| Missing idempotency key | 400 | `MISSING_IDEMPOTENCY_KEY` | `queue.skip.missing_idempotency_key` |
| Invalid command | 400 | `INVALID_COMMAND` | `queue.skip.invalid_command` |
| Store not found | 404 | `STORE_NOT_FOUND` | `queue.skip.store_not_found` |
| Store scope mismatch | 403 | `STORE_SCOPE_MISMATCH` | `queue.skip.store_scope_mismatch` |
| Forbidden / store access denied | 403 | `FORBIDDEN` | `queue.skip.forbidden` |
| QueueTicket not found | 404 | `QUEUE_TICKET_NOT_FOUND` | `queue.skip.queue_ticket_not_found` |
| QueueTicket not called | 409 | `QUEUE_TICKET_STATUS_NOT_CALLED` | `queue.skip.queue_ticket_status_not_called` |
| Already skipped evidence incomplete | 409 | `QUEUE_SKIP_EVIDENCE_INCOMPLETE` | `queue.skip.queue_skip_evidence_incomplete` |
| Reservation not found | 404 | `RESERVATION_NOT_FOUND` | `queue.skip.reservation_not_found` |
| Reservation not arrived | 409 | `RESERVATION_STATUS_NOT_ARRIVED` | `queue.skip.reservation_status_not_arrived` |
| Idempotency conflict | 409 | `IDEMPOTENCY_CONFLICT` | `queue.skip.idempotency_conflict` |
| Idempotency in progress | 409 | `IDEMPOTENCY_IN_PROGRESS` | `queue.skip.idempotency_in_progress` |
| Failed idempotency key reused | 409 | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | `queue.skip.idempotency_failed_requires_new_key` |
| Illegal state transition | 409 | `ILLEGAL_STATE_TRANSITION` | `queue.skip.illegal_state_transition` |
| Business event write failure | 500 | `EVENT_WRITE_FAILED` | `queue.skip.event_write_failed` |
| State transition write failure | 500 | `STATE_TRANSITION_WRITE_FAILED` | `queue.skip.state_transition_write_failed` |
| Audit write failure | 500 | `AUDIT_WRITE_FAILED` | `queue.skip.audit_write_failed` |
| Persistence failure | 500 | `PERSISTENCE_ERROR` | `queue.skip.persistence_error` |

Raw database exceptions must not be exposed.

App Gate denial uses the existing App Gate error envelope and codes such as:

```text
TENANT_APP_NOT_ENABLED
STORE_APP_NOT_ENABLED
PERMISSION_DENIED
```

## 7. App Gate

Controller annotation:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")
```

Permission metadata:

```text
queue.skip belongs to reservation_queue
```

Frontend visibility, if added in a future UI round, is only a hint. The API App Gate remains authoritative.

## 8. Idempotency Behavior

Header:

```text
Idempotency-Key
```

Application action:

```text
skip_queue_ticket
```

Rules:

| State | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| none | execute and complete | not applicable |
| completed | replay 200 | conflict 409 |
| started / in progress | retry later 409 | conflict 409 |
| failed | require new key 409 | conflict 409 |

Replay must not duplicate:

- QueueTicket mutation.
- BusinessEvent.
- StateTransitionLog.
- AuditLog.
- Reservation mutation.
- Seating / SeatingResource.
- Table mutation.
- Cleaning.
- Turnover.

## 9. skippedAt Behavior

- Request `skippedAt` is optional.
- If present, it is passed to the application service.
- If absent, the application service uses its clock.
- Response returns effective `skippedAt`.
- Successful fresh skip persists `queue_tickets.skipped_at`.

## 10. alreadySkipped Behavior

If QueueTicket is already `skipped` and complete evidence exists:

- Return `200`.
- `alreadySkipped = true`.
- `events = []`.
- Complete the new idempotency key.
- Do not duplicate BusinessEvent, StateTransitionLog, or AuditLog.
- Do not change Reservation, Seating, Table, Cleaning, or Turnover.

If evidence is incomplete:

- Return application-level error `QUEUE_SKIP_EVIDENCE_INCOMPLETE`.
- Do not silently succeed.

## 11. Non-Scope

This API does not implement:

- Vue UI.
- Vue Router.
- Staff Home.
- Queue Rejoin.
- Queue Display.
- Queue Workbench mutation.
- Queue Call from list.
- Queue Seat from list.
- Seating.
- SeatingResource.
- Table status change.
- Table map.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- Migration.
- SQL changes.
- Database structure changes.
- Production config.
- Seed data.

## 12. Test Contract

Controller tests:

- Endpoint path and method.
- Request body allowlist.
- `Idempotency-Key` required.
- Path `storeId` and `queueTicketId` mapped to command.
- Actor tenant, actor id, and actor type come from current actor.
- App Gate annotation uses `reservation_queue` and `queue.skip`.
- Forbidden role, missing permission, and store mismatch do not call the application service.
- Application errors map to stable API errors and idempotency statuses.

App Gate tests:

- `queue.skip` is stable.
- `queue.skip` appears in `RESERVATION_QUEUE_ENTRY_PERMISSIONS`.
- `/api/me/apps` / visible apps includes `queue.skip` when actor has it.

Integration tests:

- Called QueueTicket skipped through API.
- `QueueTicket.status = skipped`.
- `QueueTicket.skippedAt` persisted.
- Reservation remains `arrived`.
- BusinessEvent, StateTransitionLog, AuditLog, and idempotency completion written.
- AlreadySkipped returns success without duplicate evidence.
- Completed replay, in-progress, failed, hash conflict, and missing key.
- Queue ticket not found.
- Queue ticket not called.
- Reservation not found.
- Reservation not arrived.
- Already skipped without evidence.
- App Gate tenant disabled, store disabled, and permission denied write deny audit and do not mutate business state.

Boundary tests:

- No Queue Rejoin.
- No Queue Display.
- No Queue Workbench.
- No Queue Call from list.
- No Queue Seat from list.
- No Seating / SeatingResource.
- No Table status change.
- No Table map.
- No Auto assignment.
- No No-show.
- No Cancellation.
- No Cleaning / Turnover.
- No UI.
- No migration.
