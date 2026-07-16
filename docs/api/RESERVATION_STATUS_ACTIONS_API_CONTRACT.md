# Reservation Status Actions API Contract

## Scope

This contract adds explicit staff actions for terminal reservation status transitions:

- Mark a confirmed or arrived reservation as no-show.
- Mark a seated reservation as completed when the guest has finished dining.

Cleaning remains a table resource workflow. Completing a reservation does not make a table available; the existing Cleaning API still moves the table from occupied to cleaning and then available.

## Endpoints

### Mark No-Show

`POST /api/v1/stores/{storeId}/reservations/{reservationId}/no-show`

Permission: `reservation.no_show`

Request body:

```json
{
  "noShowAt": "2030-06-20T03:20:00Z",
  "reasonCode": "guest_no_show",
  "note": "Past hold time"
}
```

Success response: `200 OK`

```json
{
  "success": true,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20300620-0007",
  "status": "no_show",
  "noShowAt": "2030-06-20T03:20:00Z",
  "noShowReasonCode": "guest_no_show",
  "alreadyNoShow": false,
  "events": ["reservation.no_show"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Allowed statuses:

- `confirmed -> no_show`
- `arrived -> no_show`

Rejected statuses:

- `seated`
- `cancelled`
- `completed`

### Complete Reservation

`POST /api/v1/stores/{storeId}/reservations/{reservationId}/complete`

Permission: `reservation.complete`

Request body:

```json
{
  "completedAt": "2030-06-20T04:30:00Z",
  "reasonCode": "guest_finished",
  "note": "Guest left table"
}
```

Success response: `200 OK`

```json
{
  "success": true,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20300620-0007",
  "status": "completed",
  "completedAt": "2030-06-20T04:30:00Z",
  "seatingId": "60000000-0000-0000-0000-000000000001",
  "seatingStatus": "completed",
  "alreadyCompleted": false,
  "events": ["reservation.completed", "seating.completed"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Allowed statuses:

- `seated -> completed`

Rejected statuses:

- `confirmed`
- `arrived`
- `cancelled`
- `no_show`

The command must locate the active Seating for the reservation. A reservation may have been seated directly or through a queue ticket; both paths must be supported.

## Idempotency

Both endpoints require `Idempotency-Key`.

- Completed replay returns the stored success response with `idempotency.replayed=true`.
- Same key with different body returns `IDEMPOTENCY_CONFLICT`.
- In-progress keys return `IDEMPOTENCY_IN_PROGRESS`.
- Failed keys require a new idempotency key.

## Errors

Errors use the existing reservation error envelope:

```json
{
  "success": false,
  "error": {
    "code": "RESERVATION_NOT_FOUND",
    "messageKey": "reservation.not_found",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

Expected new conflict codes:

- `RESERVATION_CANNOT_NO_SHOW_SEATED`
- `RESERVATION_CANNOT_NO_SHOW_CANCELLED`
- `RESERVATION_CANNOT_NO_SHOW_COMPLETED`
- `RESERVATION_CANNOT_COMPLETE_CONFIRMED`
- `RESERVATION_CANNOT_COMPLETE_ARRIVED`
- `RESERVATION_CANNOT_COMPLETE_CANCELLED`
- `RESERVATION_CANNOT_COMPLETE_NO_SHOW`
- `RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING`

