# Queue Cancel API Contract V1

## Purpose

Expose one-click cancellation for a QueueTicket from the staff queue list:

```text
QueueTicket waiting / called / skipped / rejoined
-> staff cancels queue ticket
-> QueueTicket.status = cancelled
-> linked Reservation or WalkIn is not seated by this command
-> queue_ticket.cancelled event is written
-> queue_ticket cancel transition is written
-> queue.cancel audit log is written
-> idempotency is applied
```

This API cancels the queue ticket only. It does not mark a reservation as no-show, complete a reservation,
seat a party, free a table, or start cleaning.

## Endpoint

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/cancel
```

Path variables:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | Store operation boundary. |
| `queueTicketId` | Yes | UUID | Target QueueTicket. |

Headers:

| Name | Required | Purpose |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Supplies Tenant, actor, role, permission, and Store scope. |
| `Idempotency-Key` | Yes | Deduplicates the cancel command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.cancel")
```

The permission is registered in the `reservation_queue` entry permission set.

Denied App Gate requests must not mutate QueueTicket, Reservation, WalkIn, BusinessEvent,
StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

```json
{
  "cancelledAt": "2026-06-24T10:30:00Z",
  "reasonCode": "guest_left",
  "note": "Guest left before call"
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | ---: | --- | --- |
| `cancelledAt` | No | instant | If absent, the application clock supplies the cancellation time. |
| `reasonCode` | No | string or null | Trimmed to null when blank. |
| `note` | No | string or null | Trimmed and stored in audit metadata where supported. |

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `queueTicketId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- The body must not decide final status, reservation status, seating, table, cleaning, or queue placement.

## Success Response

Fresh success, already-cancelled recovery, and completed idempotency replay return `200 OK`.

```json
{
  "success": true,
  "queueTicketId": "00000000-0000-0000-0000-000000000031",
  "queueTicketNumber": 18,
  "queueTicketStatus": "cancelled",
  "reservationId": null,
  "reservationCode": null,
  "reservationStatus": null,
  "walkInId": "00000000-0000-0000-0000-000000000021",
  "cancelledAt": "2026-06-24T10:30:00Z",
  "cancellationReasonCode": "guest_left",
  "alreadyCancelled": false,
  "events": [
    "queue_ticket.cancelled"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already-cancelled recovery:

- HTTP status: `200 OK`.
- `queueTicketStatus = cancelled`.
- `alreadyCancelled = true`.
- `events = []`.
- Completes the new idempotency key without duplicating QueueTicket mutation, BusinessEvent,
  StateTransitionLog, or AuditLog.

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate side effects are written.

## Error Response

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_TICKET_CANNOT_CANCEL_SEATED",
    "messageKey": "queue.cancel.queue_ticket_cannot_cancel_seated",
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
| `QUEUE_TICKET_CANNOT_CANCEL_SEATED` | `QUEUE_TICKET_CANNOT_CANCEL_SEATED` | 409 |
| `QUEUE_TICKET_CANNOT_CANCEL_EXPIRED` | `QUEUE_TICKET_CANNOT_CANCEL_EXPIRED` | 409 |
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

Fresh success writes:

- `queue_tickets.status = cancelled`.
- `business_events.event_type = queue_ticket.cancelled`.
- `state_transition_logs.target_type = queue_ticket`.
- `state_transition_logs.to_status = cancelled`.
- `state_transition_logs.transition_code = queue_ticket.cancel`.
- `audit_logs.operation_code = queue.cancel`.
- `idempotency_records.action = cancel_queue_ticket`.

Fresh success must not create Seating, mutate DiningTable status, or create Cleaning state.

## Status Rules

- `waiting`, `called`, `skipped`, and `rejoined` may cancel when the QueueTicket state machine allows it.
- `cancelled` returns success-like already-cancelled behavior.
- `seated` returns `QUEUE_TICKET_CANNOT_CANCEL_SEATED`.
- `expired` returns `QUEUE_TICKET_CANNOT_CANCEL_EXPIRED`.
- Other disallowed transitions return `ILLEGAL_STATE_TRANSITION`.

## Migration

No migration is required for V1. The implementation uses the existing QueueTicket status, audit,
transition, business event, and idempotency persistence surfaces.

## Non-Scope

This API must not:

- cancel or no-show the linked Reservation;
- change WalkIn status beyond the queue ticket outcome;
- seat the party;
- assign or free a table;
- create Seating or SeatingResource;
- mutate DiningTable status;
- create Cleaning or Turnover state;
- regenerate queue ticket number or queue position;
- add migrations or seed data;
- touch production data.
