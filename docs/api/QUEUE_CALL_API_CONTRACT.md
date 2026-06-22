# Queue Call API Contract V1

## Purpose

Expose the minimum REST API slice for calling a waiting QueueTicket.

```text
QueueTicket.status = waiting
-> store staff calls ticket
-> QueueTicket.status = called
-> calledAt is recorded
-> holdUntilAt is recorded through queue_tickets.expires_at
-> Reservation.status remains arrived
-> queue_ticket.called event is written
-> queue_ticket waiting -> called transition is written
-> queue.call audit log is written
-> idempotency is applied
```

This API is only the Queue Call API boundary. It does not implement Queue UI, Queue skip, Queue rejoin, Queue display, Seating from Queue, Table assignment, Auto assignment, No-show, Cancellation, Cleaning, Turnover, migrations, seed data, or production data changes.

## Endpoint

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call
```

Path variables:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | URL path Store operation boundary. |
| `queueTicketId` | Yes | UUID | URL path QueueTicket target. |

Headers:

| Name | Required | Purpose |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Supplies Tenant, actor, role, permission, and Store scope in production auth. |
| `Idempotency-Key` | Yes | Deduplicates the call command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.call")
```

App Gate must allow only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `queue.call`.

Denied App Gate requests return the existing App Gate error envelope, write `app_gate_audit_logs` with `APP_GATE_DENIED`, and must not mutate QueueTicket, Reservation, BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

Only these fields are accepted by the V1 DTO:

| Field | Required | Type | Notes |
| --- | ---: | --- | --- |
| `calledAt` | No | instant | Optional call timestamp. If absent, application uses current UTC time. |
| `reasonCode` | No | string | Optional staff reason code. Trimmed to null when blank. |
| `note` | No | string | Optional staff note. Trimmed to null when blank. |

Forbidden body fields:

- `tenantId`
- `storeId`
- `reservationId`
- `reservationStatus`
- `queueTicketId`
- `queueTicketNumber`
- `queueTicketStatus`
- `actorId`
- `actorType`
- `tableId`
- `tableGroupId`
- `seatingId`
- `cleaningId`
- `turnoverId`
- `skipReason`
- `rejoinReason`
- `noShowAt`
- `cancelledAt`

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `queueTicketId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- The API does not accept any Reservation status or Table assignment input.

## Command Mapping

| API source | Application command field |
| --- | --- |
| Actor context tenant | `tenantId` |
| Path `storeId` | `storeId` |
| Path `queueTicketId` | `queueTicketId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| Actor context id | `actorId` |
| Actor context type | `actorType` |
| Body `calledAt` | `calledAt` |
| Body `reasonCode` | `reasonCode` |
| Body `note` | `note` |

## Success Response

Fresh success, already-called success-like response, and completed replay return `200 OK`.

```json
{
  "success": true,
  "queueTicketId": "00000000-0000-0000-0000-000000000011",
  "queueTicketNumber": 12,
  "queueTicketStatus": "called",
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260620-0007",
  "reservationStatus": "arrived",
  "calledAt": "2030-06-20T03:30:00Z",
  "holdUntilAt": "2030-06-20T03:33:00Z",
  "alreadyCalled": false,
  "events": [
    "queue_ticket.called"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already-called response:

- HTTP status: `200 OK`.
- `queueTicketStatus = called`.
- `reservationStatus = arrived`.
- `alreadyCalled = true`.
- `events = []`.
- Requires existing call evidence: `calledAt` and `holdUntilAt` / `expiresAt`.
- No duplicate QueueTicket mutation, BusinessEvent, StateTransitionLog, or AuditLog is written.
- New idempotency key may be completed with an already-called response snapshot.

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate QueueTicket mutation, BusinessEvent, StateTransitionLog, AuditLog, or Reservation mutation is written.

## Error Response

Queue Call API errors use this envelope:

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_TICKET_STATUS_NOT_WAITING",
    "messageKey": "queue.call.queue_ticket_status_not_waiting",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

## Error Mapping

| Application error | API error code | HTTP |
| --- | --- | ---: |
| `INVALID_COMMAND` | `INVALID_COMMAND` | 400 |
| `MISSING_IDEMPOTENCY_KEY` | `MISSING_IDEMPOTENCY_KEY` | 400 |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` | 404 |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` | 403 |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` | 403 |
| `QUEUE_TICKET_NOT_FOUND` | `QUEUE_TICKET_NOT_FOUND` | 404 |
| `QUEUE_TICKET_STATUS_NOT_WAITING` | `QUEUE_TICKET_STATUS_NOT_WAITING` | 409 |
| `QUEUE_CALL_EVIDENCE_INCOMPLETE` | `QUEUE_CALL_EVIDENCE_INCOMPLETE` | 409 |
| `QUEUE_TICKET_CANNOT_CALL_SEATED` | `QUEUE_TICKET_CANNOT_CALL_SEATED` | 409 |
| `QUEUE_TICKET_CANNOT_CALL_CANCELLED` | `QUEUE_TICKET_CANNOT_CALL_CANCELLED` | 409 |
| `QUEUE_TICKET_CANNOT_CALL_EXPIRED` | `QUEUE_TICKET_CANNOT_CALL_EXPIRED` | 409 |
| `RESERVATION_NOT_FOUND` | `RESERVATION_NOT_FOUND` | 404 |
| `RESERVATION_STATUS_NOT_ARRIVED` | `RESERVATION_STATUS_NOT_ARRIVED` | 409 |
| `QUEUE_CALL_HOLD_POLICY_INVALID` | `QUEUE_CALL_HOLD_POLICY_INVALID` | 409 |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` | 409 |
| `IDEMPOTENCY_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` | 409 |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | 409 |
| `ILLEGAL_STATE_TRANSITION` | `ILLEGAL_STATE_TRANSITION` | 409 |
| `BUSINESS_EVENT_WRITE_FAILED` | `EVENT_WRITE_FAILED` | 500 |
| `STATE_TRANSITION_WRITE_FAILED` | `STATE_TRANSITION_WRITE_FAILED` | 500 |
| `AUDIT_WRITE_FAILED` | `AUDIT_WRITE_FAILED` | 500 |
| `PERSISTENCE_ERROR` | `PERSISTENCE_ERROR` | 500 |

Idempotency status in error responses:

- `IDEMPOTENCY_IN_PROGRESS` returns `idempotency.status = started`.
- `IDEMPOTENCY_CONFLICT` returns `idempotency.status = conflict`.
- Other command failures return `idempotency.status = failed`.

## Persistence Evidence

Fresh success must write:

- `queue_tickets.status = called`.
- `queue_tickets.called_at = calledAt`.
- `queue_tickets.expires_at = holdUntilAt`.
- `business_events.event_type = queue_ticket.called`.
- `state_transition_logs.target_type = queue_ticket`.
- `state_transition_logs.from_status = waiting`.
- `state_transition_logs.to_status = called`.
- `state_transition_logs.transition_code = queue_ticket.call`.
- `audit_logs.operation_code = queue.call`.
- `idempotency_records.action = call_queue_ticket`.
- `idempotency_records.target_type = queue_ticket`.
- `idempotency_records.status = completed`.

Fresh success must not change Reservation status; it remains `arrived`.

## Hold Policy

The hold duration is resolved by the application layer:

- Prefer `StorePolicy.queueCallHoldMinutes` / `queue_call_hold_minutes` when configured.
- Use the default `3 minutes` when StorePolicy is missing or no value is configured.
- Persist `holdUntilAt` through `queue_tickets.expires_at`.

## Boundary Rules

This API must not:

- Create Queue skip/rejoin/display APIs.
- Create Queue UI.
- Seat the party.
- Assign a Table or TableGroup.
- Change dining table status.
- Create No-show or Cancellation behavior.
- Create Cleaning or Turnover behavior.
- Modify migrations or SQL files.
- Touch production config, production database, or seed data.
