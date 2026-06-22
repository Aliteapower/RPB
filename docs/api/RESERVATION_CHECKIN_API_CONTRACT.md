# Reservation CheckIn API Contract V1

## Purpose

Expose the minimum REST API slice for Reservation CheckIn.

```text
confirmed Reservation
-> POST CheckIn endpoint
-> Reservation status becomes arrived
-> reservation.arrived business event is written
-> confirmed -> arrived state transition is written
-> reservation.check_in audit log is written
-> idempotency is applied
```

This contract does not implement Queue, Seating, Table assignment, No-show, Cancellation, list/search/calendar APIs, UI, migrations, seed data, `CheckInEntity`, or a `check_ins` table.

## Endpoint

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
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
| `Idempotency-Key` | Yes | Deduplicates the CheckIn command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint must be guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.check_in")
```

App Gate must allow only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `reservation.check_in`.

App Gate denial returns the existing App Gate error envelope and writes `app_gate_audit_logs` with `APP_GATE_DENIED`.

## Request Body

Only these fields are accepted by the V1 DTO:

| Field | Required | Type | Notes |
| --- | ---: | --- | --- |
| `arrivedAt` | No | ISO8601 instant | If omitted, application clock resolves arrival time. |
| `reasonCode` | No | string | Optional operational reason/evidence code. |
| `note` | No | string | Optional staff note. |

Forbidden body fields:

- `tenantId`
- `storeId`
- `reservationId`
- `actorId`
- `actorType`
- `queueTicketId`
- `seatingId`
- `tableId`
- `tableGroupId`
- `noShowAt`
- `cancelledAt`
- `status`

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `reservationId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.

## Command Mapping

| API source | Application command field |
| --- | --- |
| Actor context tenant | `tenantId` |
| Path `storeId` | `storeId` |
| Path `reservationId` | `reservationId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| Actor context id | `actorId` |
| Actor context type | `actorType` |
| Body `arrivedAt` | `arrivedAt` |
| Body `reasonCode` | `reasonCode` |
| Body `note` | `note` |

## Success Response

Fresh success and completed replay return `200 OK`.

```json
{
  "success": true,
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260620-0007",
  "status": "arrived",
  "arrivedAt": "2026-06-20T11:10:00Z",
  "alreadyArrived": false,
  "events": [
    "reservation.arrived"
  ],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already-arrived success-like response:

- HTTP status: `200 OK`.
- `status = arrived`.
- `alreadyArrived = true`.
- `events = []`.
- No duplicate BusinessEvent, StateTransitionLog, or AuditLog is written.
- New idempotency key may be completed with an already-arrived response snapshot.

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate Reservation mutation, BusinessEvent, StateTransitionLog, or AuditLog is written.

## Error Response

Reservation API errors keep the existing envelope:

```json
{
  "success": false,
  "error": {
    "code": "RESERVATION_CANNOT_CHECK_IN_CANCELLED",
    "messageKey": "reservation.cannot_check_in_cancelled",
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
| `RESERVATION_STATUS_NOT_CONFIRMED` | `RESERVATION_STATUS_NOT_CONFIRMED` | 409 |
| `RESERVATION_CANNOT_CHECK_IN_CANCELLED` | same | 409 |
| `RESERVATION_CANNOT_CHECK_IN_NO_SHOW` | same | 409 |
| `RESERVATION_CANNOT_CHECK_IN_COMPLETED` | same | 409 |
| `RESERVATION_CANNOT_CHECK_IN_SEATED` | same | 409 |
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

- `reservations.status = arrived`.
- `reservations.updated_at = arrivedAt`.
- `business_events.event_type = reservation.arrived`.
- `state_transition_logs.from_status = confirmed`.
- `state_transition_logs.to_status = arrived`.
- `state_transition_logs.transition_code = reservation.check_in`.
- `audit_logs.operation_code = reservation.check_in`.
- `idempotency_records.action = check_in_reservation`.
- `idempotency_records.status = completed`.

Because V001 has no physical `reservations.arrived_at` column, `arrivedAt` is returned in the API response and recorded in event/transition/audit/idempotency metadata.

## Boundary Rules

This API must not:

- Create QueueTicket.
- Create Seating.
- Assign Table or TableGroup.
- Create or use TableLock.
- Create ReservationPreassignment.
- Create `CheckInEntity`.
- Create `check_ins` table.
- Implement No-show or Cancellation API.
- Add Vue/UI files.
- Change Flyway migrations.
- Touch production database or seed data.
- Change existing Reservation Create API path.
