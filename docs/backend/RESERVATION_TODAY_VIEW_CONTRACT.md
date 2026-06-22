# Reservation Today View Contract V1

## 1. Purpose

Design the minimum read-only backend contract for a store-staff Today Reservation View.

The view solves the current operator gap:

```text
Staff needs reservationId
-> staff opens Today View
-> staff sees today's reservations for the current store
-> staff copies reservationId or jumps to existing follow-up pages
```

This contract is documentation only. It does not implement a Controller, DTO, Application Service, Repository, Vue page, router entry, App Gate metadata change, Flyway migration, SQL file, seed data, or production configuration.

## 2. Scope

In scope:

- Read current store reservations for one business date.
- Default business date is today in the Store timezone.
- Sort reservations by reservation start time.
- Support an optional status filter.
- Return enough fields for a staff-facing read-only card/list.
- Declare future App Gate permission `reservation.today_view`.
- Keep the result read-only and route staff to existing CheckIn / Direct Seating pages for actions.

## 3. Non-Scope

Out of scope:

- Queue API or UI.
- Seating decision.
- Auto table assignment.
- Calendar week/month view.
- Table map.
- No-show handling.
- Cancellation handling.
- Reservation edit.
- Reservation delete.
- Direct CheckIn or Direct Seating mutation inside Today View.
- New database table or materialized view.
- Flyway migration.
- App Gate metadata implementation.
- Production database access or seed data.

## 4. Endpoint Contract

Future endpoint:

```http
GET /api/v1/stores/{storeId}/reservations/today
```

Rules:

- `storeId` comes from the path.
- The request must not accept trusted `tenantId` from the query string or body.
- This endpoint is read-only.
- This endpoint must not require `Idempotency-Key`.
- This endpoint must not use `POST`, `PUT`, `PATCH`, or `DELETE`.
- This endpoint must not change Reservation status, QueueTicket state, Seating state, Table state, Cleaning state, audit business state, or idempotency records.

## 5. Query Params

| Param | Required | Type | Default | Rule |
| --- | ---: | --- | --- | --- |
| `businessDate` | No | `YYYY-MM-DD` | Store-local today | Interpreted as a Store-local business date, not a UTC date. |
| `status` | No | string | `operational` | Allowed values are listed below. |

Selected V1 status filter contract:

| Query value | Meaning |
| --- | --- |
| omitted | Same as `operational`. |
| `operational` | Return `confirmed`, `arrived`, and `seated`. |
| `all` | Return all supported statuses. |
| `confirmed` | Return confirmed reservations only. |
| `arrived` | Return arrived reservations only. |
| `seated` | Return seated reservations only. |
| `cancelled` | Return cancelled reservations only. |
| `no_show` | Return no-show reservations only. |
| `completed` | Return completed reservations only. |

Pagination:

- V1 does not require pagination.
- Future-compatible `limit` / `cursor` may be added in a separate approved implementation contract.
- This contract does not require `limit` / `cursor` in the initial implementation.

## 6. Business Date Rule

If `businessDate` is omitted:

```text
backend resolves Store by path storeId
-> reads stores.timezone
-> computes today in that timezone
-> queries reservations.business_date
```

Rules:

- Store timezone comes from `stores.timezone`.
- Store-local business date is used for grouping.
- Time instants remain ISO8601/UTC at the API boundary.
- The response must return the resolved `businessDate` and `storeTimezone`.

Invalid `businessDate`:

- Return `400 Bad Request`.
- Error code: `INVALID_BUSINESS_DATE`.
- Message key: `reservation.today_view.invalid_business_date`.

## 7. Response Contract

Recommended success response:

```json
{
  "success": true,
  "storeId": "20000000-0000-0000-0000-000000000701",
  "businessDate": "2026-06-21",
  "storeTimezone": "Asia/Singapore",
  "statusFilter": "operational",
  "items": [
    {
      "reservationId": "50000000-0000-0000-0000-000000000701",
      "reservationCode": "R-20260621-0007",
      "status": "confirmed",
      "partySize": 4,
      "reservedStartAt": "2026-06-21T11:00:00Z",
      "reservedEndAt": "2026-06-21T12:30:00Z",
      "holdUntilAt": "2026-06-21T11:15:00Z",
      "businessDate": "2026-06-21",
      "customerName": "Guest",
      "customerNickname": "Window seat",
      "phoneMasked": "****4567",
      "note": "Birthday"
    }
  ]
}
```

Response item fields:

| Field | Required | Meaning |
| --- | ---: | --- |
| `reservationId` | Yes | Raw Reservation UUID used by existing CheckIn / Seating pages. |
| `reservationCode` | Yes | Staff-friendly reservation code. |
| `status` | Yes | Raw Reservation status code. |
| `partySize` | Yes | Guest count. |
| `reservedStartAt` | Yes | UTC instant. |
| `reservedEndAt` | Yes | UTC instant. |
| `holdUntilAt` | Yes | UTC instant. |
| `businessDate` | Yes | Store-local business date. |
| `customerName` | No | Safe customer display hint, nullable. |
| `customerNickname` | No | Safe customer lookup hint, nullable. |
| `phoneMasked` | No | Masked phone only, nullable. |
| `note` | No | Staff/customer note, nullable. |

V1 selected capability rule:

- Do not return `canCheckIn` or `canSeat` in V1.
- Frontend derives jump-button visibility from `status`.
- `confirmed` means show the jump to CheckIn.
- `arrived` means show the jump to Direct Seating.
- `seated`, `cancelled`, `no_show`, and `completed` are read-only display statuses.

Rationale:

- Avoid introducing a capability matrix before a separate capability-response contract exists.
- Backend App Gate remains the final authorization for the mutation endpoints.
- Today View is a read-only query, not an action executor.

## 8. Error Response Contract

Use the existing stable error envelope:

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

Recommended error mappings:

| Error code | Message key | HTTP |
| --- | --- | ---: |
| `INVALID_BUSINESS_DATE` | `reservation.today_view.invalid_business_date` | 400 |
| `INVALID_STATUS_FILTER` | `reservation.today_view.invalid_status_filter` | 400 |
| `STORE_NOT_FOUND` | `reservation.store_not_found` | 404 |
| `STORE_SCOPE_MISMATCH` | `reservation.store_scope_mismatch` | 403 |
| `FORBIDDEN` | `reservation.forbidden` | 403 |
| `PERSISTENCE_ERROR` | `reservation.persistence_error` | 500 |

App Gate denial should continue to use the existing App Gate error envelope and write `app_gate_audit_logs` with `APP_GATE_DENIED`.

## 9. Data Source

Data comes from existing tables only:

- `reservations`
- `customers`
- `stores`

Required joins / lookups:

- `reservations.tenant_id` and `reservations.store_id` enforce store operation scope.
- `reservations.customer_id` may left join to `customers.id`.
- `stores.timezone` resolves default business date.

No new table, materialized view, migration, seed data, SQL file, or denormalized projection is part of this contract.

## 10. Sorting

Selected V1 sort:

```text
reservedStartAt ASC
createdAt ASC
```

Implementation note:

- Database columns are `reserved_start_at` and `created_at`.
- If a later implementation needs a final deterministic tie-breaker, use `reservation_code ASC` without changing the visible contract.

## 11. Status Filter

Supported statuses:

```text
confirmed
arrived
seated
cancelled
no_show
completed
```

Default status filter:

```text
operational = confirmed + arrived + seated
```

Reason:

- Staff primarily needs active operational reservations for CheckIn / Seating.
- Terminal statuses remain queryable through explicit filters.
- Today View does not create No-show or Cancellation operations.

## 12. Permission Contract

Future App Gate mapping:

```text
app_key = reservation_queue
permission = reservation.today_view
```

Future endpoint annotation:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.today_view")
```

or the project-equivalent annotation format if changed later.

This contract does not modify:

- `AppGateRequiredPermission`
- permission metadata
- `/api/me/apps`
- `V002__app_gate_foundation.sql`
- any App Gate database schema

Future API implementation must add:

- `AppGateRequiredPermission.RESERVATION_TODAY_VIEW = "reservation.today_view"`.
- `reservation.today_view` to the `reservation_queue` entry permission registry or the approved future capability contract.
- App Gate allow/deny tests for `reservation.today_view`.

## 13. App Gate Boundary

App Gate remains a pre-business-handler boundary:

- Platform app must be active.
- Tenant entitlement must be active or valid trial.
- Store app setting must be enabled and visible where applicable.
- Actor must access the path Store.
- Actor must hold `reservation.today_view`.

Denied requests must not:

- Query and leak reservation data.
- Mutate Reservation, QueueTicket, Seating, Table, Cleaning, AuditLog, BusinessEvent, StateTransitionLog, or IdempotencyRecord business data.
- Create Queue, No-show, Cancellation, Calendar, Table map, or Seating decision artifacts.

Denied requests must:

- Return the existing App Gate denial envelope.
- Write `app_gate_audit_logs` with `APP_GATE_DENIED`.

## 14. Test Contract

Future backend tests must cover:

- Today reservations return current Store only.
- Tenant isolation.
- Store isolation.
- `businessDate` default uses Store timezone.
- Optional `businessDate` filters by Store-local business date.
- Default omitted `status` returns `confirmed`, `arrived`, `seated`.
- `status=operational` returns `confirmed`, `arrived`, `seated`.
- `status=all` returns all supported statuses.
- Each supported status filter works.
- Invalid status returns `INVALID_STATUS_FILTER` and message key.
- Invalid date returns `INVALID_BUSINESS_DATE` and message key.
- Sorting is `reservedStartAt ASC`, then `createdAt ASC`.
- Response includes `reservationId`, `reservationCode`, `status`, `partySize`, `reservedStartAt`, `reservedEndAt`, `holdUntilAt`, and `businessDate`.
- Customer fields are nullable and safe.
- Phone is masked.
- `canCheckIn` and `canSeat` are not required in V1 response.
- App Gate allowed request succeeds.
- App Gate denied request writes `app_gate_audit_logs`.

Boundary tests must confirm:

- No Queue API/UI is created.
- No Seating decision is created.
- No Reservation status mutation happens.
- No No-show API/UI is created.
- No Cancellation API/UI is created.
- No Calendar or Table map is created.
- No migration is changed.
- No production database is touched.

## 15. Boundary Check

Status mutation designed: No

Queue designed: No

Seating decision designed: No

No-show designed: No

Cancellation designed: No

Calendar designed: No

Table map designed: No

API implemented: No

UI implemented: No

Migration changed: No

Production database touched: No

Seed data inserted: No

## 16. Open Questions

- Should a later implementation add optional `limit` / `cursor` immediately, or defer pagination until real volume requires it?
- Should future UI prefill support be added to CheckIn and Direct Seating pages in the Today View UI implementation round?

## 17. Open Conflicts

- Earlier App Gate metadata guidance records `/api/me/apps` as app-level entry visibility, not a complete capability matrix. This contract declares a future Staff Home display rule using `permissions contains reservation.today_view`. The later implementation round must either align `/api/me/apps` with this specific entry-permission usage or introduce an approved capability-level response contract before binding more UI controls to permissions.

## 18. Next Step Recommendation

- Implement the backend read-only API in a separately approved API implementation round.
- Then implement the mobile-first Today View UI and Staff Home entry in a separately approved UI implementation round.
- Keep Queue, Seating decision, No-show, Cancellation, Calendar, Table map, and migrations out unless explicitly opened by a later contract.
