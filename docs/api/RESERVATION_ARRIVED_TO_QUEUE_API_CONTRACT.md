# Reservation Arrived To Queue API Contract V1

## Purpose

Expose the minimum REST API slice for sending an already-arrived Reservation into the waiting Queue.

```text
arrived Reservation
-> POST queue endpoint
-> Reservation status remains arrived
-> QueueTicket is created or reused
-> QueueTicket status is waiting
-> reservation.queued and queue_ticket.created events are written
-> queue_ticket.create transition is written
-> reservation.queue audit log is written
-> idempotency is applied
```

This contract does not implement Queue call/skip/rejoin, Seating from Queue, Queue UI, No-show, Cancellation, Reservation list/calendar, Table map, migrations, seed data, or production data changes.

## Endpoint

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
```

Path variables:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | URL path Store operation boundary. |
| `reservationId` | Yes | UUID | URL path Reservation target. |

Headers:

| Name | Required | Purpose |
| --- | ---: | --- |
| `Authorization: Bearer <jwt>` | Yes | Supplies Tenant, actor, role, permission, and Store scope in production auth. |
| `Idempotency-Key` | Yes | Deduplicates the queue command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

App Gate must allow only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `reservation.queue`.

Denied requests return the existing App Gate error envelope, write `app_gate_audit_logs` with `APP_GATE_DENIED`, and must not mutate Reservation, QueueTicket, BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

Only these fields are accepted by the V1 DTO:

| Field | Required | Type | Notes |
| --- | ---: | --- | --- |
| `partySizeGroup` | No | string | Optional QueueGroup code. If absent, backend derives by Reservation party size. Trimmed to null when blank. |
| `reasonCode` | No | string | Optional staff reason code. Trimmed to null when blank. |
| `note` | No | string | Optional staff note. Trimmed to null when blank. |

Forbidden body fields:

- `tenantId`
- `storeId`
- `reservationId`
- `actorId`
- `actorType`
- `tableId`
- `tableGroupId`
- `seatingId`
- `walkInId`
- `cleaningId`
- `turnoverId`
- `noShowAt`
- `cancelledAt`
- `queueTicketId`
- `queueTicketNumber`
- `status`

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `reservationId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- QueueGroup selection comes only from backend derivation or optional `partySizeGroup`.

## Command Mapping

| API source | Application command field |
| --- | --- |
| Actor context tenant | `tenantId` |
| Path `storeId` | `storeId` |
| Path `reservationId` | `reservationId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| Actor context id | `actorId` |
| Actor context type | `actorType` |
| Body `partySizeGroup` | `partySizeGroup` |
| Body `reasonCode` | `reasonCode` |
| Body `note` | `note` |

## Success Response

Fresh success, already-queued success-like response, and completed replay return `200 OK`.

```json
{
  "success": true,
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260620-0007",
  "reservationStatus": "arrived",
  "queueTicketId": "00000000-0000-0000-0000-000000000011",
  "queueTicketNumber": 1,
  "queueTicketStatus": "waiting",
  "queueGroupId": "00000000-0000-0000-0000-000000000021",
  "queueGroupCode": "3-4",
  "partySize": 4,
  "partySizeGroup": "3-4",
  "businessDate": "2030-06-20",
  "queuePosition": 1,
  "alreadyQueued": false,
  "events": [
    "reservation.queued",
    "queue_ticket.created"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already-queued response:

- HTTP status: `200 OK`.
- `reservationStatus = arrived`.
- `queueTicketStatus = waiting`.
- `alreadyQueued = true`.
- `events = []`.
- No duplicate QueueTicket, BusinessEvent, StateTransitionLog, or AuditLog is written.
- New idempotency key may be completed with an already-queued response snapshot.

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate QueueTicket, BusinessEvent, StateTransitionLog, AuditLog, or Reservation mutation is written.

## Error Response

Reservation API errors keep the existing envelope:

```json
{
  "success": false,
  "error": {
    "code": "QUEUE_GROUP_PARTY_SIZE_MISMATCH",
    "messageKey": "reservation.queue_group_party_size_mismatch",
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
| `RESERVATION_NOT_FOUND` | `RESERVATION_NOT_FOUND` | 404 |
| `RESERVATION_STATUS_NOT_ARRIVED` | `RESERVATION_STATUS_NOT_ARRIVED` | 409 |
| `RESERVATION_CANNOT_QUEUE_SEATED` | `RESERVATION_CANNOT_QUEUE_SEATED` | 409 |
| `RESERVATION_CANNOT_QUEUE_CANCELLED` | `RESERVATION_CANNOT_QUEUE_CANCELLED` | 409 |
| `RESERVATION_CANNOT_QUEUE_NO_SHOW` | `RESERVATION_CANNOT_QUEUE_NO_SHOW` | 409 |
| `RESERVATION_CANNOT_QUEUE_COMPLETED` | `RESERVATION_CANNOT_QUEUE_COMPLETED` | 409 |
| `QUEUE_GROUP_NOT_FOUND` | `QUEUE_GROUP_NOT_FOUND` | 404 |
| `QUEUE_GROUP_CANNOT_BE_DERIVED` | `QUEUE_GROUP_CANNOT_BE_DERIVED` | 409 |
| `QUEUE_GROUP_PARTY_SIZE_MISMATCH` | `QUEUE_GROUP_PARTY_SIZE_MISMATCH` | 409 |
| `QUEUE_TICKET_NUMBER_CONFLICT` | `QUEUE_TICKET_NUMBER_CONFLICT` | 409 |
| `ACTIVE_QUEUE_TICKET_CONFLICT` | `ACTIVE_QUEUE_TICKET_CONFLICT` | 409 |
| `IDEMPOTENCY_CONFLICT` | `IDEMPOTENCY_CONFLICT` | 409 |
| `COMMAND_IN_PROGRESS` | `IDEMPOTENCY_IN_PROGRESS` | 409 |
| `FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY` | `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | 409 |
| `ILLEGAL_STATE_TRANSITION` | `ILLEGAL_STATE_TRANSITION` | 409 |
| `BUSINESS_EVENT_WRITE_FAILED` | `EVENT_WRITE_FAILED` | 500 |
| `STATE_TRANSITION_WRITE_FAILED` | `STATE_TRANSITION_WRITE_FAILED` | 500 |
| `AUDIT_WRITE_FAILED` | `AUDIT_WRITE_FAILED` | 500 |
| `REPOSITORY_SAVE_FAILED` | `PERSISTENCE_ERROR` | 500 |
| `PERSISTENCE_ERROR` | `PERSISTENCE_ERROR` | 500 |

Idempotency status in error responses:

- `IDEMPOTENCY_IN_PROGRESS` returns `idempotency.status = started`.
- `IDEMPOTENCY_CONFLICT` returns `idempotency.status = conflict`.
- Other command failures return `idempotency.status = failed`.

## Persistence Evidence

Fresh success must write:

- `queue_tickets.reservation_id = {reservationId}`.
- `queue_tickets.walk_in_id is null`.
- `queue_tickets.status = waiting`.
- `queue_tickets.ticket_number` and `queue_position`.
- `business_events.event_type` includes `reservation.queued` and `queue_ticket.created`.
- `state_transition_logs.transition_code = queue_ticket.create`.
- `audit_logs.operation_code = reservation.queue`.
- `idempotency_records.action = queue_arrived_reservation`.
- `idempotency_records.target_type = queue_ticket`.
- `idempotency_records.status = completed`.

Fresh success must not change Reservation status; it remains `arrived`.

## Boundary Rules

This API must not:

- Create Queue call/skip/rejoin APIs.
- Create Queue UI.
- Seat the party.
- Assign a Table or TableGroup.
- Change dining table status.
- Create No-show or Cancellation behavior.
- Create Reservation list/calendar or Table map.
- Modify migrations or SQL files.
- Touch production config, production database, or seed data.
