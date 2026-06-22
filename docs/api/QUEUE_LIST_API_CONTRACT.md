# Queue List Read API Contract V1

## 1. Purpose

Queue List Read API lets store staff read queue tickets for the current store.

This API is read-only. It supports the future Queue Workbench, Queue Skip, Queue Rejoin, and Queue Display slices by exposing queue ticket summary data without changing business state.

This contract does not implement Queue Skip, Queue Rejoin, Queue Display, Queue Workbench UI, Seating, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, migrations, or database schema changes.

## 2. Endpoint

```http
GET /api/v1/stores/{storeId}/queue-tickets
```

## 3. Method

```text
GET
```

The endpoint has no request body and does not require an idempotency key.

## 4. Path Params

| Name | Required | Description |
| --- | --- | --- |
| `storeId` | yes | Store id from the route. Tenant id is resolved from the current actor, not from the request. |

## 5. Query Params

| Name | Required | Default | Rules |
| --- | --- | --- | --- |
| `status` | no | none | Must match a current `QueueTicketStatus` enum code. |
| `limit` | no | `50` | Positive integer, maximum `100`. |
| `offset` | no | `0` | Non-negative integer. |

V1 status values are the current queue ticket enum codes:

```text
waiting
called
skipped
rejoined
seated
cancelled
expired
```

## 6. App Gate Permission

```text
app_key = reservation_queue
permission = queue.view
```

The controller must be guarded with:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.view")
```

Do not reuse `reservation.queue`, `queue.call`, or `queue.seat` for this read-only list.

## 7. Response Body

Success response:

```json
{
  "success": true,
  "items": [
    {
      "queueTicketId": "91000000-0000-0000-0000-000000000001",
      "queueTicketNumber": 12,
      "queueTicketStatus": "called",
      "partySize": 4,
      "partySizeGroup": "3-4",
      "reservationId": "50000000-0000-0000-0000-000000000001",
      "reservationCode": "R-20260622-001",
      "reservationStatus": "arrived",
      "customerName": "Queue Guest",
      "customerPhoneMasked": "****5432",
      "createdAt": "2026-06-22T10:00:00Z",
      "calledAt": "2026-06-22T10:05:00Z",
      "holdUntilAt": "2026-06-22T10:08:00Z",
      "expiresAt": "2026-06-22T10:08:00Z"
    }
  ],
  "page": {
    "limit": 50,
    "offset": 0,
    "total": 1
  }
}
```

`holdUntilAt` is mapped from `queue_tickets.expires_at`.

## 8. Error Response

Errors use the existing stable API style:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATUS",
    "messageKey": "queue.list.invalid_status",
    "details": {}
  }
}
```

Required error coverage:

- `INVALID_QUERY`
- `INVALID_STATUS`
- `INVALID_LIMIT`
- `INVALID_OFFSET`
- `STORE_NOT_FOUND`
- `STORE_SCOPE_MISMATCH`
- `FORBIDDEN`
- `PERSISTENCE_ERROR`
- App Gate deny errors from the shared App Gate layer

Raw database exceptions must not be returned to clients.

## 9. Pagination

Defaults:

```text
limit = 50
offset = 0
```

Maximum:

```text
limit = 100
```

The response page includes the effective `limit`, `offset`, and total matching rows.

## 10. Sorting

V1 sorting is stable and fixed:

```text
createdAt asc
queueTicketNumber asc
```

No client-controlled sort field is supported in V1.

## 11. Privacy / Masking Rules

- `customerPhoneMasked` is returned as `****` plus the final four characters.
- Raw `phone_e164` is not exposed.
- Tenant-internal fields, deleted markers, and raw database columns are not exposed.

## 12. Non-Scope

This API must not:

- Change `QueueTicket.status`.
- Change `Reservation.status`.
- Change table status.
- Create `Seating` or `SeatingResource`.
- Write `BusinessEvent`.
- Write `StateTransitionLog`.
- Write business `AuditLog`.
- Write `IdempotencyRecord`.
- Implement Queue Skip, Queue Rejoin, Queue Display, Queue Workbench UI, Table map, Auto assignment, No-show, Cancellation, Cleaning, or Turnover.
- Add or modify migrations.
- Touch production data.

## 13. Test Contract

Required tests:

- Application tests for query validation, status filter parsing, default/max pagination, phone masking, store access, and repository failures.
- Controller tests for route, query mapping, App Gate annotation, actor authorization, and stable API error mapping.
- Integration tests using local temporary PostgreSQL for success, filtering, pagination, sorting, reservation summary, `expiresAt` to `holdUntilAt`, App Gate deny audit, and read-only boundaries.
- Local runtime security regression for `GET /api/v1/stores/*/queue-tickets`.
- App Gate metadata tests for `queue.view` in `reservation_queue`.
