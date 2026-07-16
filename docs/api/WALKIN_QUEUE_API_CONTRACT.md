# WalkIn Queue API Contract V1

## Purpose

Expose the store staff "现场取号" flow:

```text
WalkIn arrival
-> staff creates an on-site queue number
-> optional Customer is resolved or created
-> WalkIn.status = queued
-> QueueTicket.status = waiting
-> QueueTicket.reservationId = null
-> QueueTicket.walkInId is set
-> ticket joins the same QueueTicket list used by reservation queue tickets
-> BusinessEvent / StateTransitionLog / AuditLog are written
-> Idempotency is applied
```

This API does not seat the party directly, call the ticket, skip the ticket, cancel the ticket, create a
reservation, or mutate DiningTable / Seating / Cleaning state.

## Endpoint

```http
POST /api/v1/stores/{storeId}/walk-ins/queue
```

Path variables:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | Store operation boundary. |

Headers:

| Name | Required | Purpose |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Supplies Tenant, actor, role, permission, and Store scope. |
| `Idempotency-Key` | Yes | Deduplicates the walk-in queue command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "walkin.queue.create")
```

The permission is registered in the `reservation_queue` entry permission set.

Denied App Gate requests must not create or mutate Customer, WalkIn, QueueTicket, BusinessEvent,
StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

```json
{
  "partySize": 3,
  "customerId": null,
  "customerName": "Zhao Xiansheng",
  "customerNickname": null,
  "phoneE164": "+6591234567",
  "note": "Window seat if possible"
}
```

Field contract:

| Field | Required | Type | Rule |
| --- | ---: | --- | --- |
| `partySize` | Yes | integer | Must be greater than 0. |
| `customerId` | No | UUID or null | If present, must belong to the authenticated tenant. |
| `customerName` | No | string or null | Used for optional guest profile context. |
| `customerNickname` | No | string or null | Optional staff lookup/display context. |
| `phoneE164` | No | string or null | If present, must be valid E.164. |
| `note` | No | string or null | Stored on the queue ticket metadata where supported. |

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` comes from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- The request body must not decide queue ticket number, queue position, status, reservation, seating, or table assignment.

## Success Response

Fresh success returns `201 Created`. Completed idempotency replay returns `200 OK`.

```json
{
  "success": true,
  "walkInId": "00000000-0000-0000-0000-000000000021",
  "queueTicketId": "00000000-0000-0000-0000-000000000031",
  "queueTicketNumber": 18,
  "queueTicketStatus": "waiting",
  "partySize": 3,
  "partySizeGroup": "3-4",
  "businessDate": "2026-06-24",
  "queuePosition": 5,
  "alreadyQueued": false,
  "events": [
    "walk_in.queued",
    "queue_ticket.created"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate Customer refresh/create, WalkIn, QueueTicket, BusinessEvent, StateTransitionLog, or AuditLog is written.

## Error Response

```json
{
  "success": false,
  "error": {
    "code": "INVALID_PARTY_SIZE",
    "messageKey": "walkin.queue.invalid_party_size",
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
| `INVALID_PARTY_SIZE` | `INVALID_PARTY_SIZE` | 400 |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` | 404 |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` | 403 |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` | 403 |
| `INVALID_CUSTOMER_IDENTITY` | `INVALID_CUSTOMER_IDENTITY` | 400 |
| `QUEUE_GROUP_NOT_FOUND` | `QUEUE_GROUP_NOT_FOUND` | 404 |
| `QUEUE_GROUP_PARTY_SIZE_MISMATCH` | `QUEUE_GROUP_PARTY_SIZE_MISMATCH` | 409 |
| `QUEUE_TICKET_NUMBER_CONFLICT` | `QUEUE_TICKET_NUMBER_CONFLICT` | 409 |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` | 409 |
| `IDEMPOTENCY_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` | 409 |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | 409 |
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

- `walk_ins.status = queued`.
- `queue_tickets.status = waiting`.
- `queue_tickets.reservation_id = null`.
- `queue_tickets.walk_in_id = walkInId`.
- `queue_tickets.ticket_number` and `queue_tickets.queue_position` from the active same-group queue.
- `business_events.event_type in (walk_in.queued, queue_ticket.created)`.
- `state_transition_logs.transition_code in (walk_in.queue, queue_ticket.create)`.
- `audit_logs.operation_code = walk_in.queue`.
- `idempotency_records.action = queue_walk_in`.

## Queue Behavior

- Walk-in queue tickets use the same QueueTicket list and status model as reservation queue tickets.
- Reservation-backed QueueTickets keep `reservationId`; walk-in QueueTickets keep `walkInId`.
- Later one-click Queue List actions operate on the QueueTicket regardless of whether it came from a reservation or walk-in.

## Migration

No migration is required for V1. The implementation uses the existing `walk_ins`, `queue_tickets`,
Customer, audit, transition, business event, and idempotency persistence surfaces.

## Non-Scope

This API must not:

- create a Reservation;
- call, skip, rejoin, cancel, or seat a queue ticket;
- assign a table or table group;
- create Seating or SeatingResource;
- mutate DiningTable status;
- create Cleaning or Turnover state;
- add migrations or seed data;
- touch production data.
