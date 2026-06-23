# Reservation Calendar Summary API Contract

## Goal

Provide a read-only monthly reservation count summary for the staff reservation calendar.

The staff reservation page uses this endpoint to mark dates that have reservations and show the count below each calendar day.

## Endpoint

```http
GET /api/v1/stores/{storeId}/reservations/calendar-summary?month=YYYY-MM
```

Example:

```http
GET /api/v1/stores/20000000-0000-0000-0000-000000000991/reservations/calendar-summary?month=2030-06
```

## App Gate

```text
appKey: reservation_queue
permission: reservation.today_view
```

Allowed actor roles:

```text
tenant_admin
store_manager
store_staff
```

The controller also checks the current actor store scope before calling the application service.

## Query Parameters

| Name | Required | Format | Notes |
| --- | --- | --- | --- |
| `month` | No | `YYYY-MM` | If omitted, the backend uses the current month in the store timezone. |

Invalid month formats return the existing reservation today-view business-date error envelope.

## Success Response

```json
{
  "success": true,
  "storeId": "20000000-0000-0000-0000-000000000991",
  "month": "2030-06",
  "storeTimezone": "Asia/Singapore",
  "days": [
    {
      "businessDate": "2030-06-20",
      "reservationCount": 6
    },
    {
      "businessDate": "2030-06-21",
      "reservationCount": 1
    }
  ]
}
```

`days` only includes dates with at least one counted reservation.

## Count Rules

Included reservation statuses:

```text
confirmed
arrived
seated
cancelled
no_show
completed
```

Excluded:

```text
draft
deleted reservations
reservations outside the requested month
reservations from another tenant or store
```

## Error Response

The endpoint reuses the existing reservation error envelope:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_BUSINESS_DATE",
    "messageKey": "reservation.today_view.invalid_business_date",
    "details": {}
  }
}
```

Common errors:

| HTTP | Code | Message Key |
| --- | --- | --- |
| 400 | `INVALID_BUSINESS_DATE` | `reservation.today_view.invalid_business_date` |
| 403 | `FORBIDDEN` | `reservation.forbidden` |
| 403 | `STORE_SCOPE_MISMATCH` | `reservation.store_scope_mismatch` |
| 404 | `STORE_NOT_FOUND` | `reservation.store_not_found` |
| 500 | `PERSISTENCE_ERROR` | `reservation.persistence_error` |

App Gate denial errors keep the existing App Gate envelope and audit behavior.

## Read-Only Boundary

This API must not write:

```text
idempotency_records
business_events
state_transition_logs
audit_logs
queue_tickets
seatings
table_locks
reservations
customers
```

No idempotency key is required.

## Frontend Usage

The staff reservation page calls:

```typescript
getReservationCalendarSummary(storeId, visibleMonthKey)
```

Then passes:

```vue
<ReservationMonthCalendar :reservation-counts="reservationCounts" />
```

`ReservationMonthCalendar` displays a small count badge below dates where `reservationCount > 0`.

## Migration Boundary

Migration changed: No

This endpoint reads existing `reservations.business_date`, `reservations.status`, `tenant_id`, `store_id`, and `deleted_at` data.
