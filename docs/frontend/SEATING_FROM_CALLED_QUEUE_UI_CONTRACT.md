# Seating From Called Queue UI Contract V1

## 1. Purpose

Define the minimum future Store Staff UI contract for seating an already-called `QueueTicket` that originated from an arrived `Reservation`.

Target flow:

```text
Store staff opens Seating From Called Queue page
-> enters queueTicketId
-> selects exactly one table or table group
-> optionally enters overrideReasonCode, overrideNote, and note
-> UI generates Idempotency-Key
-> UI calls POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct
-> QueueTicket changes from called to seated
-> Reservation changes from arrived to seated
-> Seating and SeatingResource are created by backend
-> UI displays the seating result
```

This document is contract only. It does not create a Vue page, route, API client, backend API, migration, SQL, seed data, production configuration, runtime fixture data, or production database data.

## 2. Scope

In scope for the future minimum UI:

- Manual `queueTicketId` input.
- Manual table resource selection by `tableId` or `tableGroupId`.
- Exactly-one resource validation.
- Optional `overrideReasonCode` input.
- Optional `overrideNote` input.
- Optional `note` input.
- Automatic `Idempotency-Key` generation.
- Submit Seating From Called Queue for a called QueueTicket.
- Fresh seating success display.
- Already-seated success-like display.
- Error display using `error.code` and `error.messageKey`.
- Staff Home entry rule for a later UI implementation.
- Permission display rule.
- Mobile-first layout rules.
- Future implementation test contract.

## 3. Non-Scope

Out of scope:

- Queue list.
- Queue workbench.
- Queue display screen.
- Queue Skip.
- Queue Rejoin.
- Queue call implementation.
- Table map.
- Drag-and-drop table layout.
- Auto assignment.
- Recommended table.
- Reservation list.
- Reservation calendar.
- Reservation search.
- WalkIn seating UI.
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

## 4. Baseline Confirmation

This contract is based on the completed Seating From Called Queue API slice:

- Endpoint: `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct`.
- App Gate: `app_key = reservation_queue`, `permission = queue.seat`.
- Application action: `seat_called_queue_ticket`.
- Source QueueTicket must be `called`.
- QueueTicket fresh success becomes `seated`.
- Related Reservation fresh success becomes `seated`.
- Backend creates Seating and SeatingResource.
- Backend updates selected Table or TableGroup member tables to occupied.
- Backend remains the final authorization and business-rule enforcement source.

The UI must consume the existing API contract and must not redefine backend state machines.

## 5. Route Contract

Future route:

```text
/stores/:storeId/queue-tickets/seating/direct
```

Future page:

```text
SeatingFromCalledQueuePage.vue
```

Rationale:

- V1 is a manual seating action by explicit `queueTicketId`.
- It is not a full Queue Center.
- It does not display a queue list.
- It does not perform Queue Skip or Queue Rejoin.
- It does not implement Queue Display.
- It does not provide table-map assignment.
- It stays inside the Store Staff workflow.

Do not design these routes for V1:

```text
/stores/:storeId/queue
/stores/:storeId/queue/display
/stores/:storeId/queue/skip
/stores/:storeId/queue/rejoin
/stores/:storeId/queue/seating
/stores/:storeId/table-map
```

## 6. Staff Home Entry Rule

This contract does not modify Staff Home.

In a future UI implementation, Staff Home may add:

```text
排队入座
```

Route target:

```text
/stores/:storeId/queue-tickets/seating/direct
```

Recommended description:

```text
输入已叫号排队票 ID 并安排桌台入座
```

Display condition:

```text
reservation_queue app is visible and enabled for the store
and
reservation_queue permissions contains queue.seat
```

Do not show the entry when:

- `/api/me/apps` does not return `reservation_queue`.
- `reservation_queue.entryVisible` is false.
- `reservation_queue.permissions` does not contain `queue.seat`.

Staff Home must still not add Queue list, Queue workbench, Queue display screen, Queue Skip, Queue Rejoin, Table map, Auto assignment, No-show, Cancellation, Cleaning, or Turnover entries in this UI slice.

## 7. Permission Rule

UI visibility rule:

```text
Show Seating From Called Queue entry only when actor permissions include queue.seat.
Hide Seating From Called Queue entry when actor permissions do not include queue.seat.
```

Security rule:

```text
Frontend permission checks are display hints only.
Backend Seating From Called Queue API plus App Gate remain the final authorization source.
```

The future UI must not assume that hiding a button is sufficient authorization.

Backend gate remains:

```text
@RequireAppGate(appKey = "reservation_queue", permission = "queue.seat")
```

`/api/me/apps` V1 remains an app-entry source. The current `permissions` field may be used for the approved Staff Home display rule in this contract, but it must not be treated as a complete capability matrix for unapproved buttons.

## 8. API Call Contract

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct
```

Sources:

| Value | UI/API source |
| --- | --- |
| `storeId` | Route path `/stores/:storeId/...` |
| `queueTicketId` | Required form input, used in API path |
| `Idempotency-Key` | Generated by UI per submit |
| `tableId` | Resource selection input, request body |
| `tableGroupId` | Resource selection input, request body |
| `overrideReasonCode` | Optional request body field |
| `overrideNote` | Optional request body field |
| `note` | Optional request body field |

Headers:

```text
Idempotency-Key: queue:seat:<uuid>
Content-Type: application/json
Accept: application/json
```

Request body may contain only:

```json
{
  "tableId": "00000000-0000-0000-0000-000000000031",
  "overrideReasonCode": "MANAGER_APPROVED",
  "overrideNote": "Capacity override approved by manager",
  "note": "Guest is waiting near entrance"
}
```

or:

```json
{
  "tableGroupId": "00000000-0000-0000-0000-000000000041",
  "overrideReasonCode": "MANAGER_APPROVED",
  "overrideNote": "Use joined table group",
  "note": "Party prefers window area"
}
```

When optional fields are blank, the UI should omit them or send `null` only if the API client normalizes them consistently with existing frontend patterns. The UI must not send empty strings as trusted operational values when a field is intentionally absent.

Forbidden request body fields:

```text
tenantId
storeId
queueTicketId
reservationId
reservationCode
reservationStatus
queueTicketStatus
seatingId
seatingStatus
resourceType
resourceId
walkInId
checkInAt
noShowAt
cancelledAt
cleaningId
turnoverId
queueSkipReason
queueRejoinReason
status
actorId
actorType
```

## 9. Form Contract

Required:

```text
queueTicketId
```

Resource Selection:

```text
tableId
or
tableGroupId
```

Optional:

```text
overrideReasonCode
overrideNote
note
```

System generated:

```text
Idempotency-Key
```

Form behavior:

- `queueTicketId` is the primary field and should be visually strongest.
- Resource selection appears immediately after `queueTicketId`.
- The UI must require exactly one of `tableId` or `tableGroupId`.
- `overrideReasonCode`, `overrideNote`, and `note` are optional and visually secondary.
- Submit is disabled while the request is in flight.
- Submit is disabled when `queueTicketId` is blank.
- Submit is disabled when resource selection is missing or conflicting.
- A new submit attempt uses a new `Idempotency-Key` unless intentionally retrying the same in-flight command.

Forbidden form/body fields:

```text
tenantId
storeId in body
queueTicketId in body
reservationId
reservationStatus
queueTicketStatus
seatingId
seatingStatus
resourceType
resourceId
walkInId
checkInAt
noShowAt
cancelledAt
cleaningId
turnoverId
queueSkipReason
queueRejoinReason
status
actorId
actorType
```

## 10. Resource Selection Rule

The future UI must enforce:

```text
Exactly one of tableId or tableGroupId is required.
```

Valid selections:

```text
tableId present, tableGroupId absent
tableId absent, tableGroupId present
```

Invalid selections:

```text
tableId absent, tableGroupId absent
tableId present, tableGroupId present
```

Local validation behavior:

- Missing resource selection may render a local `RESOURCE_SELECTION_REQUIRED` style error before submit.
- Conflicting resource selection may render a local `RESOURCE_SELECTION_CONFLICT` style error before submit.
- Backend remains final and may return the same API error codes.
- The UI must not auto-select a table.
- The UI must not recommend a table.
- The UI must not provide a table map.
- The UI must not bypass capacity, availability, lock, group validity, tenant scope, or store scope rules.

## 11. Success Display Contract

The future UI must display:

```text
queueTicketId
queueTicketNumber
queueTicketStatus
reservationId
reservationCode
reservationStatus
seatingId
seatingStatus
resourceType
resourceId
alreadySeated
events
idempotency
```

Priority display:

```text
queueTicketNumber
queueTicketStatus = seated
reservationCode
reservationStatus = seated
seatingId
seatingStatus
resourceType
resourceId
alreadySeated
```

Fresh success:

```text
queueTicketStatus = seated
reservationStatus = seated
seatingStatus = occupied
alreadySeated = false
events contains queue_ticket.seated
events contains reservation.seated
events contains seating.created
events contains table.occupied
idempotency.status = completed
idempotency.replayed = false
```

Completed idempotency replay:

```text
queueTicketStatus = seated
reservationStatus = seated
idempotency.status = completed
idempotency.replayed = true
events may be empty
```

The success response is an API DTO projection. The UI must not assume it is a domain object or persistence entity.

## 12. AlreadySeated Display Contract

Already seated is success-like, not failure.

Display:

```text
queueTicketStatus = seated
reservationStatus = seated
alreadySeated = true
queueTicketNumber
reservationCode
seatingId
seatingStatus
resourceType
resourceId
events
idempotency.status = completed
```

Rules:

- `alreadySeated=true` must not render a failure panel.
- `events` may be empty.
- The result should still highlight `queueTicketStatus=seated` and `reservationStatus=seated`.
- The UI should show the existing `seatingId`, `resourceType`, and `resourceId`.
- The UI may show `idempotency.replayed` when present.
- The UI must not create or change Seating evidence locally.

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
RESOURCE_SELECTION_REQUIRED
RESOURCE_SELECTION_CONFLICT
STORE_NOT_FOUND
STORE_SCOPE_MISMATCH
FORBIDDEN
QUEUE_TICKET_NOT_FOUND
QUEUE_TICKET_STATUS_NOT_CALLED
QUEUE_TICKET_SOURCE_NOT_RESERVATION
QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE
QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING
QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE
QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED
QUEUE_TICKET_CANNOT_SEAT_CANCELLED
QUEUE_TICKET_CANNOT_SEAT_EXPIRED
RESERVATION_NOT_FOUND
RESERVATION_STATUS_NOT_ARRIVED
RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED
TABLE_NOT_FOUND
TABLE_NOT_AVAILABLE
TABLE_CAPACITY_INSUFFICIENT
TABLE_LOCK_CONFLICT
TABLE_GROUP_NOT_FOUND
TABLE_GROUP_INVALID
TABLE_GROUP_MEMBER_UNAVAILABLE
TABLE_GROUP_CAPACITY_INSUFFICIENT
SEATING_SOURCE_INVALID
SEATING_RESOURCE_INVALID
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
QUEUE_TICKET_NOT_FOUND
QUEUE_TICKET_STATUS_NOT_CALLED
RESERVATION_NOT_FOUND
RESERVATION_STATUS_NOT_ARRIVED
TABLE_NOT_FOUND
TABLE_NOT_AVAILABLE
TABLE_CAPACITY_INSUFFICIENT
TABLE_LOCK_CONFLICT
TABLE_GROUP_NOT_FOUND
TABLE_GROUP_INVALID
TABLE_GROUP_MEMBER_UNAVAILABLE
TABLE_GROUP_CAPACITY_INSUFFICIENT
RESOURCE_SELECTION_REQUIRED
RESOURCE_SELECTION_CONFLICT
IDEMPOTENCY_CONFLICT
IDEMPOTENCY_IN_PROGRESS
IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY
APP_GATE_DENIED
FORBIDDEN
STORE_SCOPE_MISMATCH
UNKNOWN_ERROR
```

Compatibility and display notes:

| Alias or fixture code | Display behavior |
| --- | --- |
| `APP_GATE_DENIED` | Operational denial category and audit action; if it appears in a response or fixture, display it like other `error.code` values. |
| `UNKNOWN_ERROR` | Frontend fallback for invalid response shape, network failure, or unclassified response. |
| `FORBIDDEN` | Controller or authorization fallback; display returned code and message key without replacing them. |
| `TABLE_LOCKED` | If any local fixture still uses this older wording, normalize future UI tests to backend `TABLE_LOCK_CONFLICT` unless a later API contract changes it. |

## 14. Mobile-first Rules

The future Seating From Called Queue page must be mobile-first:

- Single-column layout.
- `queueTicketId` field appears first and is visually prominent.
- Resource selection appears before optional operational notes.
- The currently selected resource mode is obvious.
- Exactly-one resource validation is visible before submit.
- `overrideReasonCode`, `overrideNote`, and `note` are visually secondary.
- Submit button is clear and thumb-friendly.
- Loading state prevents duplicate accidental taps.
- Disabled state is obvious when required values are missing, resource selection is invalid, or the request is in flight.
- Success result highlights `queueTicketNumber`.
- Success result shows `queueTicketStatus=seated`.
- Success result shows `reservationStatus=seated`.
- Success result shows `seatingId`, `resourceType`, and `resourceId`.
- `alreadySeated=true` is visible and treated as success-like.
- Error panel clearly shows `error.code` and `error.messageKey`.
- Text must fit within cards, buttons, and result panels on mobile.
- Do not add complex back-office page layout.
- Do not add Queue list, Queue workbench, Queue skip, Queue rejoin, display screen, table map, drag-and-drop, No-show, or Cancellation UI.

## 15. Future Implementation Test Contract

Route tests:

- `/stores/:storeId/queue-tickets/seating/direct` opens the future page.
- Staff Home link points to the same store route when the entry is permitted.
- The route does not use `/stores/:storeId/queue`, `/queue/display`, `/queue/skip`, `/queue/rejoin`, or `/table-map`.

Staff Home tests:

- Staff Home shows `排队入座` when `reservation_queue` is visible and `permissions` contains `queue.seat`.
- Staff Home hides `排队入座` when `permissions` does not contain `queue.seat`.
- Staff Home hides the entry when `/api/me/apps` does not return `reservation_queue`.
- UI permission logic is display-only; backend App Gate denial still renders returned API error.
- Staff Home does not add Queue list, display, skip, rejoin, table map, auto assignment, No-show, Cancellation, Cleaning, or Turnover entries.

Form tests:

- `queueTicketId` is required.
- Exactly one of `tableId` or `tableGroupId` is required.
- Missing resource selection blocks submit or renders resource-required error.
- Conflicting resource selection blocks submit or renders resource-conflict error.
- `overrideReasonCode` may be blank.
- `overrideNote` may be blank.
- `note` may be blank.
- `Idempotency-Key` is generated automatically.
- Request body excludes forbidden fields.

API tests:

- Calls `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct`.
- Sends `storeId` only in the path.
- Sends `queueTicketId` only in the path.
- Sends `Idempotency-Key`.
- Sends only `tableId`, `tableGroupId`, `overrideReasonCode`, `overrideNote`, and `note` in the body.
- Omits blank optional fields or sends normalized nulls consistently with the existing API client pattern.

Success tests:

- Called QueueTicket seating success displays `queueTicketStatus=seated`.
- Displays `queueTicketNumber`.
- Displays `reservationStatus=seated`.
- Displays `reservationCode`.
- Displays `seatingId`.
- Displays `seatingStatus`.
- Displays `resourceType`.
- Displays `resourceId`.
- Displays `alreadySeated=false`.
- Displays `events`.
- Displays `idempotency.status=completed`.

Already-seated tests:

- Displays `alreadySeated=true`.
- Treats the response as success-like.
- Does not show a failure panel.
- Allows empty `events`.
- Displays existing `seatingId`, `resourceType`, and `resourceId`.
- Displays `idempotency.status=completed`.

Error tests:

- Displays `error.code`.
- Displays `error.messageKey`.
- Does not hardcode translated business copy instead of `messageKey`.
- Handles QueueTicket missing and invalid status backend errors.
- Handles related Reservation backend errors.
- Handles table and table-group backend errors.
- Handles resource-selection backend errors.
- Handles idempotency backend errors.
- Handles App Gate denial backend errors.
- Handles unknown or network fallback as `UNKNOWN_ERROR`.

Boundary tests:

- No Queue list UI.
- No Queue workbench UI.
- No Queue skip UI.
- No Queue rejoin UI.
- No Queue display screen.
- No table map.
- No drag-and-drop table layout.
- No auto assignment.
- No No-show UI.
- No Cancellation UI.
- No Cleaning UI changes.
- No Turnover UI changes.
- No backend API changes.
- No App Gate metadata changes.
- No migration changes.

## 16. Boundary

This contract does not implement:

- `SeatingFromCalledQueuePage.vue`.
- `seatingFromCalledQueueApi.ts`.
- `seatingFromCalledQueue.ts`.
- Vue Router changes.
- `StoreStaffHomePage.vue` changes.
- Backend Controller changes.
- Backend DTO changes.
- Application Service changes.
- App Gate metadata changes.
- Flyway migration changes.
- SQL changes.
- Database structure changes.
- Production database access.
- Queue list.
- Queue workbench.
- Queue skip.
- Queue rejoin.
- Queue display screen.
- Table map.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.

## 17. Open Questions

- Should the future UI support optional `?queueTicketId=` route query prefill after a Queue Call success page links into seating?
- Should a later approved implementation expose table and table-group lookup helpers, or should V1 remain manual ID entry only?

## 18. Next Step Recommendation

Next approved round may implement the minimum Seating From Called Queue UI:

- Add `SeatingFromCalledQueuePage.vue`.
- Add `seatingFromCalledQueueApi.ts`.
- Add typed request/response/error types.
- Add route `/stores/:storeId/queue-tickets/seating/direct`.
- Add Staff Home `排队入座` entry behind `queue.seat`.

The implementation round must still keep Queue list, Queue skip/rejoin/display, table map, auto assignment, No-show, Cancellation, Cleaning, Turnover, backend API changes, App Gate metadata changes, migrations, SQL, and production data out of scope unless separately approved.
