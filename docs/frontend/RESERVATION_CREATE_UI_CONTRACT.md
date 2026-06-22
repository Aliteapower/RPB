# Reservation Create UI Contract V1

## 1. Purpose

This document defines the minimum Store Staff UI contract for Create Reservation.

Future flow:

```text
Store staff enters reservation information
-> UI calls Create Reservation API
-> backend creates confirmed Reservation
-> UI displays reservationCode, time, party size, customer projection, events, and idempotency status
```

This is a UI contract only. It does not create Vue pages, Vue components, API clients, routes, backend APIs, migrations, SQL, seed data, tests, or runtime mock data.

## 2. Scope

In scope:

- Create Reservation only.
- Store staff form for future Reservation creation.
- Success display for confirmed Reservation result.
- Error display using `error.code` and `error.messageKey`.
- Idempotency-Key handling contract.
- Mobile-first behavior contract.
- Future Staff Home entry note.

Out of scope:

- Reservation CheckIn UI.
- Reservation Cancel UI.
- Reservation No-show UI.
- Reservation Calendar.
- Reservation List/Search.
- Queue UI.
- Seating UI.
- Table assignment UI.
- Table selector.
- Customer search.
- Backend API changes.
- Migration or schema changes.
- OpenAPI generation.

## 3. Route Contract

Future route:

```text
/stores/:storeId/reservations/create
```

Future page component:

```text
ReservationCreatePage.vue
```

Route ownership:

- `storeId` comes from the route param.
- `tenantId` must not be accepted from the form.
- Auth and Store scope remain backend responsibilities.
- Future implementation may read the current Store context from the existing store context helper, but the path `storeId` remains the source for the API call.

## 4. Page Purpose

The page allows store staff to create a future confirmed Reservation.

The page must reinforce the V1 business boundary:

- Reservation holds Store + business date + time range + party-size capacity.
- Reservation does not automatically create QueueTicket.
- Reservation does not create Seating.
- Reservation does not create TableLock.
- Reservation does not create ReservationPreassignment.
- Reservation does not lock a specific Table by default.

## 5. Form Contract

Required fields:

| Field | Type | Rule |
| --- | --- | --- |
| `partySize` | integer | Required, must be greater than 0. |
| `reservedStartAt` | datetime input mapped to ISO8601 instant | Required. |

Optional fields:

| Field | Type | Rule |
| --- | --- | --- |
| `reservedEndAt` | datetime input mapped to ISO8601 instant or null | Optional. If absent, backend derives final value from StorePolicy / fallback. |
| `customerId` | UUID text input or null | Optional. V1 does not design customer search. |
| `customerName` | text | Optional temporary customer identity hint. |
| `customerNickname` | text | Optional staff/customer hint. |
| `phoneE164` | text | Optional; must be E.164 if present. |
| `note` | text | Optional staff/customer note. |

Forbidden form/body fields:

- `tenantId`
- `queueTicketId`
- `seatingId`
- `tableId`
- `tableGroupId`
- `checkInAt`
- `noShowAt`
- `cancelledAt`
- `reservationCode`

Reason:

- `tenantId` comes from server-side auth/scope.
- `reservationCode` is server-owned.
- Create Reservation V1 does not create Queue, Seating, CheckIn, No-show, Cancellation, table assignment, TableLock, or ReservationPreassignment.

## 6. Layout Contract

Mobile-first single-column layout:

1. `partySize`
2. `reservedStartAt`
3. optional `reservedEndAt`
4. collapsible customer section
5. collapsible note section
6. submit button
7. success or error result area

Primary inputs:

- `partySize`
- `reservedStartAt`

Secondary/collapsible inputs:

- `reservedEndAt`
- `customerId`
- `customerName`
- `customerNickname`
- `phoneE164`
- `note`

The UI must not use a large calendar component, table map, table selector, reservation list, or multi-page admin workflow in V1.

## 7. Validation Contract

Frontend minimum validation:

- `partySize > 0`.
- `reservedStartAt` is required.
- If `reservedEndAt` exists, it must be after `reservedStartAt`.
- If `phoneE164` exists, it must match E.164.
- Submit must generate an `Idempotency-Key`.
- Request body must exclude `tenantId`.
- Request body must exclude table, queue, seating, check-in, no-show, cancellation, and reservation-code fields.

Backend remains the final validation source.

## 8. API Client Contract

Future API call:

```text
POST /api/v1/stores/{storeId}/reservations
```

Required header:

```text
Idempotency-Key: <generated-key>
```

Request body:

```json
{
  "partySize": 4,
  "reservedStartAt": "2026-06-20T11:00:00Z",
  "reservedEndAt": null,
  "customerId": null,
  "customerName": "Guest",
  "customerNickname": "Boss friend",
  "phoneE164": "+6591234567",
  "note": "Window seat if possible"
}
```

Client behavior:

- Generate a new idempotency key per fresh submit.
- Reuse the same key only for retrying the same in-flight business intent.
- Do not include `tenantId`.
- Do not include `reservationCode`.
- Do not include table, queue, seating, check-in, no-show, or cancellation fields.
- Treat non-2xx responses as API error envelopes when possible.
- Display network failures as a technical failure area without inventing business result text.

## 9. Success Display Contract

On successful creation, display:

- `reservationId`
- `reservationCode`
- `status`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- `customer` projection
- `events`
- `idempotency.status`
- `idempotency.replayed`

Primary display:

- `reservationCode`
- `status = confirmed`
- `reservedStartAt`
- `reservedEndAt`
- `partySize`

Secondary display:

- `holdUntilAt`
- `businessDate`
- `customer.displayName`
- `customer.phoneE164`
- `events`
- `idempotency`

Event codes may be shown as:

- `reservation.created`
- `reservation.confirmed`

The success response is an API DTO projection. The UI must not assume it is a domain object or persistence entity.

## 10. Error Display Contract

The UI must display:

- `error.code`
- `error.messageKey`

At minimum, the UI must handle:

- `MISSING_IDEMPOTENCY_KEY`
- `INVALID_PARTY_SIZE`
- `INVALID_TIME_RANGE`
- `RESERVATION_START_IN_PAST`
- `INVALID_PHONE_E164`
- `CUSTOMER_NOT_FOUND`
- `RESERVATION_DUPLICATE_ACTIVE`
- `RESERVATION_CAPACITY_INSUFFICIENT`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `FORBIDDEN`
- `STORE_SCOPE_MISMATCH`
- `PERSISTENCE_ERROR`

Rules:

- Do not replace `messageKey` with hardcoded display copy.
- Do not expose backend stack traces, SQL details, entity names, repository names, or internal constraint names.
- Capacity errors should show `RESERVATION_CAPACITY_INSUFFICIENT` and `reservation.capacity_insufficient`; do not expose complex capacity policy in V1 UI.

## 11. Idempotency Behavior

Header:

```text
Idempotency-Key
```

Behavior:

- Missing key: backend returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success: return `201 Created`, `idempotency.status = completed`, `replayed = false`.
- Completed replay: return `200 OK`, `idempotency.status = completed`, `replayed = true`.
- In progress: show `IDEMPOTENCY_IN_PROGRESS`.
- Failed same key: show `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` and require a fresh submit key.
- Same key different hash: show `IDEMPOTENCY_CONFLICT`.

The UI must not use idempotency as a table lock, auth bypass, or capacity override.

## 12. Mobile-First Requirement

The page must be optimized for store staff on phone-sized screens.

Requirements:

- Single-column layout.
- `partySize` and `reservedStartAt` appear first.
- Optional customer fields are collapsible.
- Note is collapsed or visually secondary.
- Submit button is obvious and reachable.
- Success result highlights `reservationCode`.
- Error result clearly shows `error.code` and `error.messageKey`.
- No large calendar.
- No table map.
- No drag-and-drop table layout.
- No dense admin grid.

## 13. Staff Home Integration Note

Future implementation may add a Staff Home link:

```text
Create Reservation
```

Target:

```text
/stores/:storeId/reservations/create
```

The Staff Home must not add entries for:

- CheckIn.
- Queue.
- Seating.
- No-show.
- Cancellation.
- Reservation Calendar.
- Reservation List/Search.
- Table assignment.

## 14. Test Contract

Future UI implementation tests should cover:

Form:

- Page renders.
- `partySize` required.
- `reservedStartAt` required.
- invalid `reservedEndAt` rejected.
- invalid `phoneE164` rejected.
- `reservedEndAt` may be empty.
- `Idempotency-Key` generated.
- body excludes `tenantId`, `tableId`, `queueTicketId`, and `seatingId`.

Success:

- Create reservation success.
- Displays `reservationCode`.
- Displays `status = confirmed`.
- Displays final `reservedEndAt`.
- Displays `holdUntilAt`.
- Displays idempotency completed.
- Displays replay flag when replayed.

Error:

- Displays `error.code`.
- Displays `error.messageKey`.
- Duplicate active Reservation error shown.
- Capacity insufficient error shown.
- Idempotency conflict shown.

Boundary:

- No CheckIn UI.
- No Queue UI.
- No Seating UI.
- No No-show UI.
- No Cancellation UI.
- No Table assignment UI.
- No Calendar/List UI.
- No backend API change.
- No migration change.

## 15. Next Implementation Notes

The next approved implementation round may create:

- `ReservationCreatePage.vue`
- Reservation create API client.
- Reservation create TypeScript types.
- Staff Home link to Create Reservation.

The implementation round must still avoid CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, Calendar/List, backend API changes, migration changes, and OpenAPI generation unless explicitly approved.
