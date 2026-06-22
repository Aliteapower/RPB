# Seating From Called Queue API Contract V1

## Purpose

Expose the minimum REST API slice for seating a called `QueueTicket` that originated from an arrived `Reservation`.

```text
QueueTicket.status = called
-> store staff selects exactly one table or table group
-> App Gate authorizes queue.seat
-> SeatingFromCalledQueueApplicationService
-> QueueTicket.status = seated
-> Reservation.status = seated
-> Seating and SeatingResource are created
-> selected table or table-group member tables become occupied
```

This API contract does not implement Queue Skip, Queue Rejoin, Queue Display, Queue list/workbench, Auto assignment, Table map, No-show, Cancellation, Cleaning, Turnover, UI, migrations, SQL, production config, seed data, or production database changes.

## Endpoint

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct
```

Path parameters:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | URL path Store operation boundary. |
| `queueTicketId` | Yes | UUID | URL path called QueueTicket target. |

Headers:

| Name | Required | Purpose |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Supplies Tenant, actor, roles, permissions, and Store scope in production auth. |
| `Idempotency-Key` | Yes | Deduplicates the queue seating command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")
```

App Gate allows the request only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `queue.seat`.

Denied App Gate requests return the existing App Gate error envelope, write `app_gate_audit_logs` with `APP_GATE_DENIED`, and must not mutate QueueTicket, Reservation, Seating, SeatingResource, DiningTable, BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

Only these fields are accepted by the V1 DTO:

| Field | Required | Type | Notes |
| --- | ---: | --- | --- |
| `tableId` | Conditional | UUID | Required when `tableGroupId` is absent. |
| `tableGroupId` | Conditional | UUID | Required when `tableId` is absent. |
| `overrideReasonCode` | No | string | Optional audit context. Does not bypass validation. |
| `overrideNote` | No | string | Optional audit context. Does not bypass validation. |
| `note` | No | string | Optional staff note. |

Resource selection rule:

```text
Exactly one of tableId or tableGroupId is required.
```

Forbidden body fields:

- `tenantId`
- `storeId`
- `queueTicketId`
- `reservationId`
- `walkInId`
- `checkInAt`
- `noShowAt`
- `cancelledAt`
- `cleaningId`
- `turnoverId`
- `queueSkipReason`
- `queueRejoinReason`
- `status`

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `queueTicketId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- The API does not accept a client-provided Reservation id, QueueTicket status, Reservation status, Seating id, or downstream Cleaning/Turnover input.

## Command Mapping

| API source | Application command field |
| --- | --- |
| Actor context tenant | `tenantId` |
| Path `storeId` | `storeId` |
| Path `queueTicketId` | `queueTicketId` |
| Body `tableId` | `tableId` |
| Body `tableGroupId` | `tableGroupId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| Actor context id | `actorId` |
| Actor context type | `actorType` |
| Body `overrideReasonCode` | `overrideReasonCode` |
| Body `overrideNote` | `overrideNote` |
| Body `note` | `note` |

## Success Response

Fresh success, already-seated success-like response, and completed replay return `200 OK`.

```json
{
  "success": true,
  "queueTicketId": "00000000-0000-0000-0000-000000000011",
  "queueTicketNumber": 12,
  "queueTicketStatus": "seated",
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260622-2296",
  "reservationStatus": "seated",
  "seatingId": "00000000-0000-0000-0000-000000000021",
  "seatingStatus": "occupied",
  "resourceType": "table",
  "resourceId": "00000000-0000-0000-0000-000000000031",
  "alreadySeated": false,
  "events": [
    "queue_ticket.seated",
    "reservation.seated",
    "seating.created",
    "table.occupied"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Wire resource type mapping:

| Application resource type | API response `resourceType` |
| --- | --- |
| `dining_table` | `table` |
| `table_group` | `table_group` |

V1 does not expose event ids.

## AlreadySeated Behavior

Already-seated behavior is success-like only when durable evidence exists:

- QueueTicket status is `seated`.
- Related Reservation status is `seated`.
- Active Seating exists with source `queue_ticket`.
- Active SeatingResource exists for the Seating.

Response behavior:

- HTTP status is `200 OK`.
- `alreadySeated = true`.
- `events = []`.
- No duplicate Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, QueueTicket mutation, Reservation mutation, or table mutation is written.
- A new idempotency key may be completed with the already-seated response snapshot.

## Error Response

Queue seating API errors use this envelope:

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_TICKET_STATUS_NOT_CALLED",
    "messageKey": "queue.seat.queue_ticket_status_not_called",
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
| `RESOURCE_SELECTION_CONFLICT` | `RESOURCE_SELECTION_CONFLICT` | 400 |
| `RESOURCE_SELECTION_REQUIRED` | `RESOURCE_SELECTION_REQUIRED` | 400 |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` | 404 |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` | 403 |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` | 403 |
| `QUEUE_TICKET_NOT_FOUND` | `QUEUE_TICKET_NOT_FOUND` | 404 |
| `QUEUE_TICKET_STATUS_NOT_CALLED` | `QUEUE_TICKET_STATUS_NOT_CALLED` | 409 |
| `QUEUE_TICKET_SOURCE_NOT_RESERVATION` | `QUEUE_TICKET_SOURCE_NOT_RESERVATION` | 409 |
| `QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE` | `QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE` | 409 |
| `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING` | `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING` | 409 |
| `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE` | `QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE` | 409 |
| `QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED` | `QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED` | 409 |
| `QUEUE_TICKET_CANNOT_SEAT_CANCELLED` | `QUEUE_TICKET_CANNOT_SEAT_CANCELLED` | 409 |
| `QUEUE_TICKET_CANNOT_SEAT_EXPIRED` | `QUEUE_TICKET_CANNOT_SEAT_EXPIRED` | 409 |
| `RESERVATION_NOT_FOUND` | `RESERVATION_NOT_FOUND` | 404 |
| `RESERVATION_STATUS_NOT_ARRIVED` | `RESERVATION_STATUS_NOT_ARRIVED` | 409 |
| `RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED` | `RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED` | 409 |
| `TABLE_NOT_FOUND` | `TABLE_NOT_FOUND` | 404 |
| `TABLE_NOT_AVAILABLE` | `TABLE_NOT_AVAILABLE` | 409 |
| `TABLE_CAPACITY_INSUFFICIENT` | `TABLE_CAPACITY_INSUFFICIENT` | 409 |
| `TABLE_LOCK_CONFLICT` | `TABLE_LOCK_CONFLICT` | 409 |
| `TABLE_GROUP_NOT_FOUND` | `TABLE_GROUP_NOT_FOUND` | 404 |
| `TABLE_GROUP_INVALID` | `TABLE_GROUP_INVALID` | 409 |
| `TABLE_GROUP_MEMBER_UNAVAILABLE` | `TABLE_GROUP_MEMBER_UNAVAILABLE` | 409 |
| `TABLE_GROUP_CAPACITY_INSUFFICIENT` | `TABLE_GROUP_CAPACITY_INSUFFICIENT` | 409 |
| `INVALID_SEATING_SOURCE` | `SEATING_SOURCE_INVALID` | 409 |
| `INVALID_SEATING_RESOURCE` | `SEATING_RESOURCE_INVALID` | 409 |
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

Raw database exceptions must not be exposed.

## Idempotency

Header:

```text
Idempotency-Key
```

Application action:

```text
seat_called_queue_ticket
```

Behavior:

| Existing idempotency state | Same hash behavior | Different hash behavior |
| --- | --- | --- |
| missing | Execute once and complete. | Not applicable. |
| `completed` | Replay stored result, `idempotency.replayed = true`. | `IDEMPOTENCY_CONFLICT`. |
| `started` | `IDEMPOTENCY_IN_PROGRESS`, retry later. | `IDEMPOTENCY_CONFLICT`. |
| `failed` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`. | `IDEMPOTENCY_CONFLICT`. |

Completed replay must not create duplicate QueueTicket mutations, Reservation mutations, Seating, SeatingResource, BusinessEvent, StateTransitionLog, AuditLog, table status updates, or group member table status updates.

## Persistence Evidence

Fresh success must write:

- `queue_tickets.status = seated`.
- `reservations.status = seated`.
- `seatings.queue_ticket_id = queueTicketId`.
- `seatings.reservation_id = null`.
- `seatings.walk_in_id = null`.
- `seating_resources.resource_type = dining_table` or `table_group`.
- Selected table or TableGroup member tables become `occupied`.
- Business events: `queue_ticket.seated`, `reservation.seated`, `seating.created`, `table.occupied`.
- State transitions: `queue_ticket.seat`, `reservation.seat`, `seating.occupy`, `dining_table.occupy`.
- Audit operation: `queue.seat`.
- Idempotency action: `seat_called_queue_ticket`.

## Local Runtime Security

Local/test runtime may explicitly allow:

```http
POST /api/v1/stores/*/queue-tickets/*/seating/direct
```

This allowlist is only for local/test runtime security and must not weaken production security.

## Test Contract

Required tests cover:

- Controller request-to-command mapping.
- DTO field allowlist and forbidden body fields.
- App Gate annotation and `queue.seat` metadata.
- Local runtime allowlist.
- Table success through API.
- TableGroup success through API.
- AlreadySeated through API.
- Idempotency replay, in-progress, failed-key, conflict, and missing key.
- Application failure mappings.
- App Gate deny audit and no business mutation.
- Boundary: no Queue Skip, Queue Rejoin, Queue Display, Queue list/workbench, Auto assignment, No-show, Cancellation, Cleaning, Turnover, UI, migration, production data, or seed data.
