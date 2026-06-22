# Reservation Arrived Direct Seating API Contract V1

## Purpose

Expose the minimum REST API slice for seating an already-arrived Reservation directly at a Table or TableGroup.

```text
arrived Reservation
-> POST direct seating endpoint
-> Reservation status becomes seated
-> Seating is created or reused for already-seated idempotent result
-> dining resource is occupied
-> reservation.seated, seating.created, table.occupied events are written
-> reservation.seat audit log is written
-> idempotency is applied
```

This contract does not implement Queue, Reservation arrival/check-in, No-show, Cancellation, Reservation list/calendar APIs, UI, migrations, seed data, or production data changes.

## Endpoint

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct
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
| `Idempotency-Key` | Yes | Deduplicates the direct seating command. |
| `Content-Type: application/json` | Yes | JSON request body. |

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.seat")
```

App Gate must allow only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `reservation.seat`.

App Gate denial returns the existing App Gate error envelope and writes `app_gate_audit_logs` with `APP_GATE_DENIED`. Denied requests must not mutate Reservation, Seating, resource state, BusinessEvent, StateTransitionLog, AuditLog, or IdempotencyRecord business data.

## Request Body

Only these fields are accepted by the V1 DTO:

| Field | Required | Type | Notes |
| --- | ---: | --- | --- |
| `tableId` | Conditional | UUID | Required when `tableGroupId` is absent. Mutually exclusive with `tableGroupId`. |
| `tableGroupId` | Conditional | UUID | Required when `tableId` is absent. Mutually exclusive with `tableId`. |
| `overrideReasonCode` | No | string | Optional override reason code. Trimmed to null when blank. |
| `overrideNote` | No | string | Optional override note. Trimmed to null when blank. |
| `note` | No | string | Optional staff note. Trimmed to null when blank. |

Exactly one of `tableId` and `tableGroupId` must be provided.

Forbidden body fields:

- `tenantId`
- `storeId`
- `reservationId`
- `actorId`
- `actorType`
- `reservationStatus`
- `seatingId`
- `seatingStatus`
- `resourceType`
- `resourceId`
- `queueTicketId`
- `arrivedAt`
- `seatedAt`
- `noShowAt`
- `cancelledAt`
- `status`

Trust boundaries:

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` and `reservationId` come from the path.
- `idempotencyKey` comes from the `Idempotency-Key` header.
- Resource selection comes only from `tableId` or `tableGroupId`.

## Command Mapping

| API source | Application command field |
| --- | --- |
| Actor context tenant | `tenantId` |
| Path `storeId` | `storeId` |
| Path `reservationId` | `reservationId` |
| Body `tableId` | `tableId` |
| Body `tableGroupId` | `tableGroupId` |
| Header `Idempotency-Key` | `idempotencyKey` |
| Actor context id | `actorId` |
| Actor context type | `actorType` |
| Body `overrideReasonCode` | `overrideReasonCode` |
| Body `overrideNote` | `overrideNote` |
| Body `note` | `note` |

## Success Response

Fresh success and completed replay return `200 OK`.

```json
{
  "success": true,
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260620-0007",
  "reservationStatus": "seated",
  "seatingId": "00000000-0000-0000-0000-000000000011",
  "seatingStatus": "seated",
  "resourceType": "table",
  "resourceId": "00000000-0000-0000-0000-000000000021",
  "alreadySeated": false,
  "events": [
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

Resource type mapping:

| Application result `resourceType` | API response `resourceType` |
| --- | --- |
| `dining_table` | `table` |
| `table_group` | `table_group` |

Already-seated success-like response:

- HTTP status: `200 OK`.
- `reservationStatus = seated`.
- `seatingStatus = seated`.
- `alreadySeated = true`.
- `events = []`.
- No duplicate BusinessEvent, StateTransitionLog, AuditLog, Seating, or resource mutation is written.
- New idempotency key may be completed with an already-seated response snapshot.

Completed idempotency replay:

- HTTP status: `200 OK`.
- Same response shape as success.
- `idempotency.replayed = true`.
- No duplicate Reservation, Seating, resource, BusinessEvent, StateTransitionLog, or AuditLog mutation is written.

## Error Response

Reservation API errors keep the existing envelope:

```json
{
  "success": false,
  "error": {
    "code": "RESERVATION_STATUS_NOT_ARRIVED",
    "messageKey": "reservation.status_not_arrived",
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
| `RESERVATION_NOT_FOUND` | `RESERVATION_NOT_FOUND` | 404 |
| `RESERVATION_STATUS_NOT_ARRIVED` | `RESERVATION_STATUS_NOT_ARRIVED` | 409 |
| `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING` | `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING` | 409 |
| `RESERVATION_CANNOT_SEAT_CANCELLED` | `RESERVATION_CANNOT_SEAT_CANCELLED` | 409 |
| `RESERVATION_CANNOT_SEAT_NO_SHOW` | `RESERVATION_CANNOT_SEAT_NO_SHOW` | 409 |
| `RESERVATION_CANNOT_SEAT_COMPLETED` | `RESERVATION_CANNOT_SEAT_COMPLETED` | 409 |
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

Fresh success must write or update:

- `reservations.status = seated`.
- `seatings.status = seated`.
- `seating_resources.resource_type = dining_table` for `tableId`, or `table_group` for `tableGroupId`.
- Resource availability/status is updated by the existing application slice.
- `business_events.event_type` includes `reservation.seated`, `seating.created`, and `table.occupied`.
- `state_transition_logs.transition_code` includes `reservation.seat`, `seating.occupy`, and `dining_table.occupy`.
- `audit_logs.operation_code = reservation.seat`.
- `idempotency_records.action = seat_arrived_reservation`.
- `idempotency_records.status = completed`.

## Test Contract

The API implementation must cover:

- Controller mapping for table seating.
- Controller mapping for table-group seating.
- Request-body XOR validation for `tableId` and `tableGroupId`.
- Missing `Idempotency-Key`.
- Response field names and resource type conversion.
- Already-seated response.
- Completed idempotency replay.
- Application error mapping.
- App Gate annotation and denial behavior.
- PostgreSQL integration evidence for state, event, transition, audit, idempotency, and no-duplicate behavior.
- Local runtime security allowlist coverage if local runtime security is changed.

## Boundary Rules

This API must not:

- Implement Queue API or UI.
- Implement Reservation CheckIn API changes.
- Implement No-show or Cancellation API.
- Add Reservation list/calendar APIs.
- Add Vue/UI files.
- Add or modify Flyway migrations.
- Create a new app key.
- Create a new permission model.
- Touch production database or seed data.
- Change existing Reservation Create or CheckIn API paths.
