# Reservation Today View API Contract V1

## Purpose

Expose a read-only API for Store Staff to view reservations for one Store-local business date.

This API does not mutate Reservation state, does not write BusinessEvent / StateTransitionLog / AuditLog / IdempotencyRecord, and does not implement Queue, No-show, Cancellation, Reservation list/calendar, Table map, UI, migration, seed data, or production data changes.

## Endpoint

```http
GET /api/v1/stores/{storeId}/reservations/today
```

Path variables:

| Name | Required | Type | Source |
| --- | ---: | --- | --- |
| `storeId` | Yes | UUID | URL path Store operation boundary. |

Query parameters:

| Name | Required | Type | Default | Notes |
| --- | ---: | --- | --- | --- |
| `businessDate` | No | `yyyy-MM-dd` | Today in Store timezone | Uses `stores.timezone`. |
| `status` | No | string | `operational` | Supported: `operational`, `all`, `confirmed`, `arrived`, `seated`, `cancelled`, `no_show`, `completed`. |

`operational` means:

```text
confirmed
arrived
seated
```

`all` means:

```text
confirmed
arrived
seated
cancelled
no_show
completed
```

`draft` is not returned by V1 Today View.

## App Gate

The endpoint is guarded by:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")
```

App Gate must allow only when:

- Platform app `reservation_queue` is active.
- Tenant is entitled to `reservation_queue`.
- Store has `reservation_queue` enabled.
- Actor can access the path Store.
- Actor has permission `reservation.today_view`.

App Gate denial returns the existing App Gate error envelope and writes `app_gate_audit_logs` with `APP_GATE_DENIED`.

## Trust Boundaries

- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store access come from server actor context.
- `storeId` comes from the path.
- `businessDate` and `status` come from query parameters.
- No request body is accepted.
- No `Idempotency-Key` is required or used.

## Sorting

Items are sorted by:

```text
reservedStartAt ASC
createdAt ASC
```

## Success Response

Success returns `200 OK`.

```json
{
  "success": true,
  "storeId": "20000000-0000-0000-0000-000000000991",
  "businessDate": "2030-06-20",
  "storeTimezone": "Asia/Singapore",
  "statusFilter": "operational",
  "items": [
    {
      "reservationId": "50000000-0000-0000-0000-000000000991",
      "reservationCode": "R-TV-0001",
      "status": "confirmed",
      "partySize": 4,
      "reservedStartAt": "2030-06-20T03:00:00Z",
      "reservedEndAt": "2030-06-20T04:30:00Z",
      "holdUntilAt": "2030-06-20T03:15:00Z",
      "businessDate": "2030-06-20",
      "customerName": "Phone Guest",
      "customerNickname": "VIP",
      "phoneMasked": "****4567",
      "note": "Window seat"
    }
  ]
}
```

Phone masking:

```text
+6591234567 -> ****4567
null -> null / omitted from JSON
```

Forbidden response fields:

```text
phoneE164
canCheckIn
canSeat
capabilities
internal audit ids
```

## Error Response

Today View API errors use a non-idempotent Reservation API envelope:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATUS_FILTER",
    "messageKey": "reservation.today_view.invalid_status_filter",
    "details": {}
  }
}
```

App Gate denials use the App Gate envelope:

```json
{
  "success": false,
  "error": {
    "code": "PERMISSION_DENIED",
    "messageKey": "appgate.permission_denied",
    "details": {}
  }
}
```

## Error Mapping

| Application error | API error code | HTTP |
| --- | --- | ---: |
| `INVALID_COMMAND` | `INVALID_COMMAND` | 400 |
| `STORE_NOT_FOUND` | `STORE_NOT_FOUND` | 404 |
| `STORE_SCOPE_MISMATCH` | `STORE_SCOPE_MISMATCH` | 403 |
| `STORE_ACCESS_DENIED` | `FORBIDDEN` | 403 |
| `INVALID_BUSINESS_DATE` | `INVALID_BUSINESS_DATE` | 400 |
| `INVALID_STATUS_FILTER` | `INVALID_STATUS_FILTER` | 400 |
| `PERSISTENCE_ERROR` | `PERSISTENCE_ERROR` | 500 |

Message keys:

```text
reservation.today_view.invalid_business_date
reservation.today_view.invalid_status_filter
```

## Persistence

The API reads:

- `stores.timezone`
- `reservations`
- `customers`

The API must not write:

- `reservations`
- `business_events`
- `state_transition_logs`
- `audit_logs`
- `idempotency_records`
- `queue_tickets`
- `seatings`
- `table_locks`

Only App Gate denial may write:

```text
app_gate_audit_logs
```

## Boundary Rules

This API must not:

- Create QueueTicket.
- Create Seating.
- Assign Table or TableGroup.
- Create or use TableLock.
- Create ReservationPreassignment.
- Implement CheckIn, Seating, No-show, or Cancellation.
- Return full customer phone.
- Return action capabilities.
- Add Vue/UI files.
- Change Flyway migrations.
- Touch production database or seed data.
- Change existing Reservation Create, CheckIn, or Seating API paths.
