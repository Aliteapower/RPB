# Reservation Table Assignment API Contract

## Purpose

This contract lets an authenticated tenant employee list dining tables eligible for one confirmed Reservation and create its initial table preassignment. A preassignment is planned table intent and is not CheckIn, QueueTicket, Seating, occupancy, Cleaning, or Table Switch.

## Scope And Permissions

Base resource:

```text
/api/v1/stores/{storeId}/reservations/{reservationId}
```

Allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

Both endpoints require the `reservation_queue` App Gate to be entitled and enabled for the requested Store.

| Endpoint | Permission |
|---|---|
| `GET .../assignable-tables` | `table.view` |
| `PUT .../table-assignment` | `reservation.create` |

The authenticated actor's Tenant and accessible Store must match `storeId`. The APIs are not available to public-booking customers or unauthenticated public-share viewers.

## Assignable Tables Query

```text
GET /api/v1/stores/{storeId}/reservations/{reservationId}/assignable-tables
```

The server derives party size, business date, and reserved start/end from the scoped Reservation. The client cannot override availability inputs.

A table is included only when:

- The Reservation exists in the actor's Tenant and requested Store.
- The Reservation status is `confirmed`.
- The Reservation has no active, non-deleted preassignment.
- The resource is a non-deleted `dining_table` in the same Tenant and Store.
- The table status is not `inactive`.
- `capacity_min <= reservation.party_size <= capacity_max`.
- No active, non-deleted preassignment for another `confirmed` or `arrived` Reservation overlaps the requested interval.

Intervals use half-open overlap semantics:

```text
existing.start < requested.end AND existing.end > requested.start
```

Boundary-touching reservations do not overlap. Current `occupied` or `cleaning` status is an immediate operational state and does not permanently exclude a future reservation time slot.

### Success

HTTP `200 OK`

```json
{
  "success": true,
  "reservationId": "2d94cb68-8796-4f01-9cc4-6b3fb166da7d",
  "partySize": 2,
  "tables": [
    {
      "tableId": "fb329d95-73f7-4dce-8570-a6e11dbd26a2",
      "tableCode": "A01",
      "displayName": "A01",
      "areaName": "Main Hall",
      "capacityMin": 1,
      "capacityMax": 4
    }
  ]
}
```

`tables` is an empty array when the Reservation is assignable but no table currently qualifies.

## Assign Table Command

```text
PUT /api/v1/stores/{storeId}/reservations/{reservationId}/table-assignment
Idempotency-Key: reservation:table-assignment:<reservationId>:<unique-value>
Content-Type: application/json
```

Request:

```json
{
  "tableId": "fb329d95-73f7-4dce-8570-a6e11dbd26a2"
}
```

`tableId` is required. Unknown fields do not change the command contract.

The command locks the scoped Reservation and selected table, then repeats status, existing-assignment, capacity, and exact-overlap checks before inserting an active `ReservationPreassignment`.

### Success

HTTP `200 OK`

```json
{
  "success": true,
  "reservationId": "2d94cb68-8796-4f01-9cc4-6b3fb166da7d",
  "tableId": "fb329d95-73f7-4dce-8570-a6e11dbd26a2",
  "tableCode": "A01",
  "assignmentStatus": "active",
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

The command does not change Reservation status. It emits `reservation.table_assigned` and records audit operation `reservation.table_assign`.

## Idempotency And Replay

- Missing or blank `Idempotency-Key` is rejected before application execution.
- Repeating the same key and request payload after completion returns the stored success snapshot with `replayed=true`.
- Repeating a completed assignment using a new key and the same table returns success without inserting a second preassignment.
- A key reused with a different request hash returns `IDEMPOTENCY_CONFLICT`.
- An identical request still in progress returns `COMMAND_IN_PROGRESS` and may be retried later.
- A failed key is not silently restarted; the employee must retry with a new key after refreshing availability.
- If the Reservation already has a different active table, the command returns `RESERVATION_ALREADY_ASSIGNED`.

## Error Envelope

```json
{
  "success": false,
  "error": {
    "code": "TABLE_NOT_AVAILABLE",
    "messageKey": "reservation.table_assignment.table_not_available",
    "details": {}
  },
  "idempotency": {
    "status": "failed",
    "replayed": false
  }
}
```

The query error response may omit `idempotency`. The command response always includes it when application execution has started.

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `INVALID_COMMAND` | Required identifier, body field, or actor context is invalid. |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Command header is absent or blank. |
| 403 | `FORBIDDEN` | Actor role, permission, or App Gate check failed. |
| 403 | `STORE_SCOPE_MISMATCH` | Actor cannot access the requested Store. |
| 404 | `RESERVATION_NOT_FOUND` | Scoped Reservation does not exist. |
| 404 | `TABLE_NOT_FOUND` | Scoped dining table does not exist. |
| 409 | `RESERVATION_NOT_ASSIGNABLE` | Reservation status is not `confirmed`. |
| 409 | `RESERVATION_ALREADY_ASSIGNED` | Reservation has a different active preassignment. |
| 409 | `TABLE_CAPACITY_INSUFFICIENT` | Party size is outside table capacity. |
| 409 | `TABLE_NOT_AVAILABLE` | Table is inactive or now has an overlapping active preassignment. |
| 409 | `IDEMPOTENCY_CONFLICT` | Key was reused with a different payload or failed request. |
| 409 | `COMMAND_IN_PROGRESS` | Matching command has not completed. |
| 500 | `PERSISTENCE_ERROR` | Scoped read or preassignment write failed. |
| 500 | `BUSINESS_EVENT_WRITE_FAILED` | Required business event could not be written. |
| 500 | `AUDIT_WRITE_FAILED` | Required audit record could not be written. |

## Share Compatibility

Both existing share projections resolve the active preassignment and pass its table code to the supported `{{tableCode}}` template variable. Default and seeded templates contain this variable. An unassigned Reservation continues to use the current localized pending-table label.

No share endpoint or public H5 contract changes in this increment.
