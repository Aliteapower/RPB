# Reservation Arrived To Queue UI Contract V1

## 1. Purpose

Define the minimum future Store Staff UI contract for sending an already-arrived Reservation into the waiting Queue when no table is available.

Target flow:

```text
Store staff opens Reservation Queue page
-> enters reservationId
-> optionally enters partySizeGroup, reasonCode, and note
-> UI generates Idempotency-Key
-> UI calls POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
-> QueueTicket is created or existing active ticket is returned
-> Reservation remains arrived
-> QueueTicket status is waiting
-> UI displays queue result
```

This document is contract only. It does not create a Vue page, route, API client, backend API, migration, SQL, seed data, production configuration, runtime fixture data, or production database data.

## 2. Scope

In scope for the future minimum UI:

- Manual `reservationId` input.
- Optional `partySizeGroup` input or selection.
- Optional `reasonCode` input.
- Optional `note` input.
- Automatic `Idempotency-Key` generation.
- Submit arrived Reservation to Queue.
- Fresh queue success display.
- Already-queued success-like display.
- Error display using `error.code` and `error.messageKey`.
- Staff Home entry rule for a later UI implementation.
- Today View arrived-card entry rule for a later UI implementation.
- Permission display rule.
- Mobile-first layout rules.
- Future implementation test contract.

## 3. Non-Scope

Out of scope:

- Queue list.
- Queue call.
- Queue skip.
- Queue rejoin.
- Queue display screen.
- Seating from queue.
- Auto assignment.
- Recommended table.
- Table map.
- Drag-and-drop table layout.
- Reservation list.
- Reservation calendar.
- Reservation search.
- Reservation edit or delete.
- No-show UI.
- Cancellation UI.
- Cleaning UI changes.
- Turnover UI.
- Backend API changes.
- App Gate metadata changes.
- Migration changes.
- SQL changes.
- Database structure changes.
- Production database access.
- Production seed data.

## 4. Route Contract

Future route:

```text
/stores/:storeId/reservations/queue
```

Future page:

```text
ReservationArrivedToQueuePage.vue
```

Rationale:

- This is a Reservation branch operation after arrival, not a full Queue Center.
- V1 supports manual `reservationId` entry or query prefill.
- The page stays inside the Store Staff workflow.
- The route does not create Queue list, call, display, or seating workbench behavior.

Do not design these routes for V1:

```text
/stores/:storeId/queue
/stores/:storeId/queue/call
/stores/:storeId/queue/display
/stores/:storeId/seating/from-queue
```

## 5. Staff Home Entry Rule

This contract does not modify Staff Home.

In a future UI implementation, Staff Home may add:

```text
预约排队
```

Route target:

```text
/stores/:storeId/reservations/queue
```

Recommended description:

```text
为已到店且暂无空桌的预约创建排队号
```

Display condition:

```text
reservation_queue app is visible and enabled for the store
and
reservation_queue permissions contains reservation.queue
```

Do not show the entry when:

- `/api/me/apps` does not return `reservation_queue`.
- `reservation_queue.entryVisible` is false.
- `reservation_queue.permissions` does not contain `reservation.queue`.

Staff Home must still not add Queue list, Queue call, Queue skip, Queue rejoin, display screen, Seating from queue, table map, No-show, Cancellation, Reservation list, Reservation calendar, or auto-assignment entries in this UI slice.

## 6. Today View Entry Rule

This contract does not modify Today View.

In a future UI implementation, Today View card actions should be:

| Reservation status | Future Today View behavior |
| --- | --- |
| `confirmed` | Show `预约到店`; do not show `进入排队`. |
| `arrived` | Show `预约入座` and `进入排队`. |
| `seated` | Show `已入座`; no mutation button. |
| `cancelled` | Read-only. |
| `no_show` | Read-only. |
| `completed` | Read-only. |

The `进入排队` action must navigate to:

```text
/stores/:storeId/reservations/queue?reservationId=...
```

Rules:

- Today View must not submit the Queue API directly.
- Today View must preserve the current `storeId`.
- Today View must pass only `reservationId` as query prefill.
- The future Queue page may read `route.query.reservationId` and prefill the input.
- Navigation must not create a QueueTicket until staff submits the Queue page form.

## 7. Permission Rule

UI visibility rule:

```text
Show Reservation Queue entry only when actor permissions include reservation.queue.
Hide Reservation Queue entry when actor permissions do not include reservation.queue.
```

Security rule:

```text
Frontend permission checks are display hints only.
Backend Reservation Arrived To Queue API plus App Gate remain the final authorization source.
```

The future UI must not assume that hiding a button is sufficient authorization.

Backend gate remains:

```text
@RequireAppGate(appKey = "reservation_queue", permission = "reservation.queue")
```

`/api/me/apps` V1 remains an app-entry source. The current `permissions` field may be used for the approved Staff Home and Today View display rules in this contract, but it must not be treated as a complete capability matrix for unapproved buttons.

## 8. API Call Contract

Endpoint:

```http
POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue
```

Sources:

| Value | UI/API source |
| --- | --- |
| `storeId` | Route path `/stores/:storeId/...` |
| `reservationId` | Required form input, used in API path |
| `Idempotency-Key` | Generated by UI per submit |
| `partySizeGroup` | Optional request body field |
| `reasonCode` | Optional request body field |
| `note` | Optional request body field |

Headers:

```text
Idempotency-Key: reservation:queue:<uuid>
Content-Type: application/json
Accept: application/json
```

Request body may contain only:

```json
{
  "partySizeGroup": "3-4",
  "reasonCode": "NO_TABLE_AVAILABLE",
  "note": "Customer is waiting near entrance"
}
```

When optional fields are blank, the UI should omit them or send `null` only if the API client normalizes them consistently with existing frontend patterns. The UI must not send empty strings as trusted operational values when a field is intentionally absent.

Forbidden request body fields:

```text
tenantId
storeId
reservationId
actorId
actorType
tableId
tableGroupId
seatingId
walkInId
cleaningId
turnoverId
noShowAt
cancelledAt
queueTicketId
queueTicketNumber
status
```

## 9. Form Fields

Required:

```text
reservationId
```

Optional:

```text
partySizeGroup
reasonCode
note
```

System generated:

```text
Idempotency-Key
```

Form behavior:

- `reservationId` is the primary field and should be visually strongest.
- `partySizeGroup` is optional and should support `自动推导`, `1-2`, `3-4`, `5-6`, and `7+`.
- Selecting `自动推导` means the request body does not include `partySizeGroup`; the backend derives QueueGroup from the Reservation party size.
- `reasonCode` is optional operational evidence.
- `note` is optional and visually secondary.
- Submit is disabled while the request is in flight.
- A new submit attempt uses a new `Idempotency-Key` unless intentionally retrying the same in-flight command.

Forbidden form/body fields:

```text
tenantId
storeId in body
reservationId in body
tableId
tableGroupId
seatingId
walkInId
cleaningId
turnoverId
noShowAt
cancelledAt
queueTicketNumber
status
```

## 10. partySizeGroup Behavior

Recommended V1 options:

```text
自动推导
1-2
3-4
5-6
7+
```

Rules:

- Default option is `自动推导`.
- `自动推导` sends no `partySizeGroup` field in the body.
- Explicit `partySizeGroup` sends the selected code in the body.
- Backend remains responsible for validating the group against the Store QueueGroup and Reservation party size.
- Backend errors such as `QUEUE_GROUP_NOT_FOUND`, `QUEUE_GROUP_CANNOT_BE_DERIVED`, and `QUEUE_GROUP_PARTY_SIZE_MISMATCH` must be displayed with raw `error.code` and `error.messageKey`.

## 11. Success Display Contract

The future UI must display:

```text
reservationId
reservationCode
reservationStatus
queueTicketId
queueTicketNumber
queueTicketStatus
queueGroupId
queueGroupCode
partySize
partySizeGroup
businessDate
queuePosition
alreadyQueued
events
idempotency
```

Priority display:

```text
queueTicketNumber
queueTicketStatus = waiting
reservationCode
reservationStatus = arrived
partySizeGroup
alreadyQueued
```

Fresh success:

```text
reservationStatus = arrived
queueTicketStatus = waiting
alreadyQueued = false
events contains reservation.queued
events contains queue_ticket.created
idempotency.status = completed
idempotency.replayed = false
```

Completed idempotency replay:

```text
reservationStatus = arrived
queueTicketStatus = waiting
idempotency.status = completed
idempotency.replayed = true
events may be empty
```

The success response is an API DTO projection. The UI must not assume it is a domain object or persistence entity.

## 12. AlreadyQueued Display Contract

Already queued is success-like, not failure.

Display:

```text
reservationStatus = arrived
queueTicketStatus = waiting
alreadyQueued = true
queueTicketNumber
reservationCode
partySizeGroup
events
idempotency.status = completed
```

Rules:

- `alreadyQueued=true` must not render a failure panel.
- `events` may be empty.
- The result should still highlight `queueTicketStatus=waiting` and the existing `queueTicketNumber`.
- The UI may show `idempotency.replayed` when present.
- The UI must not create a new QueueTicket number locally.

## 13. Error Display Contract

The future UI must display:

```text
error.code
error.messageKey
```

The UI must not replace `messageKey` with hardcoded business copy.

Actual backend errors and App Gate denial codes to support include:

```text
MISSING_IDEMPOTENCY_KEY
INVALID_COMMAND
STORE_NOT_FOUND
STORE_SCOPE_MISMATCH
FORBIDDEN
RESERVATION_NOT_FOUND
RESERVATION_STATUS_NOT_ARRIVED
RESERVATION_CANNOT_QUEUE_SEATED
RESERVATION_CANNOT_QUEUE_CANCELLED
RESERVATION_CANNOT_QUEUE_NO_SHOW
RESERVATION_CANNOT_QUEUE_COMPLETED
QUEUE_GROUP_NOT_FOUND
QUEUE_GROUP_CANNOT_BE_DERIVED
QUEUE_GROUP_PARTY_SIZE_MISMATCH
QUEUE_TICKET_NUMBER_CONFLICT
ACTIVE_QUEUE_TICKET_CONFLICT
IDEMPOTENCY_CONFLICT
IDEMPOTENCY_IN_PROGRESS
IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY
ILLEGAL_STATE_TRANSITION
EVENT_WRITE_FAILED
STATE_TRANSITION_WRITE_FAILED
AUDIT_WRITE_FAILED
PERSISTENCE_ERROR
TENANT_APP_NOT_ENABLED
STORE_APP_NOT_ENABLED
PERMISSION_DENIED
APP_GATE_DENIED
UNKNOWN_ERROR
```

Minimum error coverage requested by Product Owner:

```text
MISSING_IDEMPOTENCY_KEY
RESERVATION_NOT_FOUND
RESERVATION_INVALID_STATUS
RESERVATION_ALREADY_QUEUED
QUEUE_GROUP_NOT_FOUND
QUEUE_GROUP_CANNOT_BE_DERIVED
QUEUE_TICKET_NUMBER_CONFLICT
IDEMPOTENCY_CONFLICT
IDEMPOTENCY_IN_PROGRESS
APP_GATE_DENIED
FORBIDDEN
STORE_SCOPE_MISMATCH
UNKNOWN_ERROR
```

Compatibility and display notes:

| Alias or fixture code | Display behavior |
| --- | --- |
| `RESERVATION_INVALID_STATUS` | Same grouping as `RESERVATION_STATUS_NOT_ARRIVED` or terminal `RESERVATION_CANNOT_QUEUE_*`; still show the returned code and message key. |
| `RESERVATION_ALREADY_QUEUED` | Prefer success-like `alreadyQueued=true`; if returned as an error by a fixture, still show the returned code and message key. |
| `APP_GATE_DENIED` | Operational denial category and audit action; if it appears in a response or fixture, display it like other `error.code` values. |
| `UNKNOWN_ERROR` | Frontend fallback for invalid response shape, network failure, or unclassified response. |

## 14. Mobile-first Rules

The future Reservation Queue page must be mobile-first:

- Single-column layout.
- `reservationId` field appears first and is visually prominent.
- `partySizeGroup` is optional and secondary to `reservationId`.
- `reasonCode` and `note` are visually secondary.
- Submit button is clear and thumb-friendly.
- Loading state prevents duplicate accidental taps.
- Disabled state is obvious when required values are missing or the request is in flight.
- Success result highlights `queueTicketNumber`.
- Success result shows `queueTicketStatus=waiting`.
- Success result shows `reservationStatus=arrived`.
- `alreadyQueued=true` is visible and treated as success-like.
- Error panel clearly shows `error.code` and `error.messageKey`.
- Text must fit within cards, buttons, and result panels on mobile.
- Do not add complex back-office page layout.
- Do not add Queue list, Queue call, Queue skip, Queue rejoin, display screen, Seating from queue, table map, drag-and-drop, No-show, or Cancellation UI.

## 15. Future Implementation Test Contract

Route tests:

- `/stores/:storeId/reservations/queue` opens the future page.
- Staff Home link points to the same store route when the entry is permitted.
- Today View `进入排队` link points to `/stores/:storeId/reservations/queue?reservationId=...`.

Staff Home tests:

- Staff Home shows `预约排队` when `reservation_queue` is visible and `permissions` contains `reservation.queue`.
- Staff Home hides `预约排队` when `permissions` does not contain `reservation.queue`.
- Staff Home hides the entry when `/api/me/apps` does not return `reservation_queue`.
- UI permission logic is display-only; backend App Gate denial still renders returned API error.

Today View tests:

- `arrived` card displays `进入排队`.
- `confirmed` card does not display `进入排队`.
- `seated` card does not display `进入排队`.
- Clicking `进入排队` navigates with `reservationId` query prefilled.
- Today View does not call the Queue API directly.

Form tests:

- `reservationId` is required.
- `partySizeGroup` may be blank or `自动推导`.
- `reasonCode` may be blank.
- `note` may be blank.
- `Idempotency-Key` is generated automatically.
- Request body excludes forbidden fields.

API tests:

- Calls `POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue`.
- Sends `storeId` only in the path.
- Sends `reservationId` only in the path.
- Sends `Idempotency-Key`.
- Sends only `partySizeGroup`, `reasonCode`, and `note` in the body.
- Omits `partySizeGroup` when `自动推导` is selected.

Success tests:

- Arrived reservation queue success displays `reservationStatus=arrived`.
- Displays `queueTicketStatus=waiting`.
- Displays `queueTicketNumber`.
- Displays `reservationCode`.
- Displays `partySizeGroup`.
- Displays `alreadyQueued=false`.
- Displays `events`.
- Displays `idempotency.status=completed`.

Already-queued tests:

- Displays `alreadyQueued=true`.
- Treats the response as success-like.
- Does not show a failure panel.
- Allows empty `events`.
- Displays `idempotency.status=completed`.

Error tests:

- Displays `error.code`.
- Displays `error.messageKey`.
- Does not hardcode translated business copy instead of `messageKey`.
- Handles reservation state backend errors.
- Handles QueueGroup backend errors.
- Handles ticket number conflict.
- Handles idempotency backend errors.
- Handles App Gate denial backend errors.
- Handles unknown or network fallback as `UNKNOWN_ERROR`.

Boundary tests:

- No Queue list UI.
- No Queue call UI.
- No Queue skip UI.
- No Queue rejoin UI.
- No Queue display screen.
- No Seating from queue.
- No table map.
- No drag-and-drop table layout.
- No auto assignment.
- No No-show UI.
- No Cancellation UI.
- No backend API changes.
- No App Gate metadata changes.
- No migration changes.

## 16. Boundary

This contract does not implement:

- `ReservationArrivedToQueuePage.vue`.
- `reservationArrivedToQueueApi.ts`.
- Vue Router changes.
- `StoreStaffHomePage.vue` changes.
- `ReservationTodayViewPage.vue` changes.
- Backend Controller changes.
- Backend DTO changes.
- Application Service changes.
- App Gate metadata changes.
- Flyway migration changes.
- SQL changes.
- Database structure changes.
- Production database access.
- Queue list.
- Queue call.
- Queue skip.
- Queue rejoin.
- Queue display screen.
- Seating from queue.
- Table map.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.

## 17. Open Questions

- Should `partySizeGroup` be rendered as a select, segmented control, or compact radio group in the future implementation? The contract only fixes the allowed values and backend behavior.
- Should the future Queue page offer a post-success link back to Today View? This would be a navigation convenience only and must not add Queue list behavior.

## 18. Next Step Recommendation

Next approved round may implement the minimum Reservation Arrived To Queue UI:

- Add `ReservationArrivedToQueuePage.vue`.
- Add `reservationArrivedToQueueApi.ts`.
- Add typed response/request/error types.
- Add route `/stores/:storeId/reservations/queue`.
- Add Staff Home `预约排队` entry behind `reservation.queue`.
- Add Today View `进入排队` navigation for `arrived` cards.

The implementation round must still keep Queue list, Queue call/skip/rejoin, Seating from queue, table map, No-show, Cancellation, backend API changes, App Gate metadata changes, migrations, SQL, and production data out of scope unless separately approved.
