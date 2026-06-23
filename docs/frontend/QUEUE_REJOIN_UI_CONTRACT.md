# Queue Rejoin UI Contract V1

## 1. Purpose

Define the future frontend UI contract for rejoining a skipped QueueTicket from the existing Store Staff
Queue Ticket List surface.

Business meaning:

```text
Queue Rejoin = 过号后重新入队 / 恢复排队
```

Target flow:

```text
Store staff opens Queue Ticket List
-> UI displays skipped QueueTicket cards when included by the current list filter
-> Staff confirms Rejoin on an eligible skipped ticket
-> UI sends POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
-> Backend App Gate checks reservation_queue / queue.rejoin
-> QueueTicket skipped -> waiting
-> Original ticket number remains unchanged
-> Ticket is placed at the tail of the same queue group
-> UI refreshes the current Queue List query
```

This document is contract only. It does not implement a Vue page, frontend API client, frontend type file,
router change, Staff Home change, backend API, App Gate metadata, migration, SQL, seed data, runtime
fixture, production configuration, or production database change.

## 2. Scope

In scope for a future UI implementation:

- Queue Rejoin action placement on the existing `QueueTicketListPage.vue` queue ticket card.
- Status eligibility for showing the Rejoin action.
- App Gate visibility rule using `reservation_queue.queue.rejoin`.
- Confirmation flow before mutation.
- Request body allowlist and forbidden frontend body fields.
- Loading, duplicate-click, success, `alreadyRejoined`, error, and App Gate denied states.
- Idempotency key generation and retry behavior.
- Refresh behavior after success and selected stale-state errors.
- Mobile-first, accessible basic UX expectations.
- Future implementation test contract.

## 3. Non-Scope

Out of scope for this UI contract:

- Queue Rejoin UI implementation.
- Queue Rejoin frontend API client implementation.
- Queue Rejoin frontend type implementation.
- Vue Router changes.
- Staff Home changes.
- Backend API changes.
- App Gate metadata changes.
- Migration or SQL changes.
- Queue Skip behavior changes.
- Queue Display.
- Queue Workbench mutation.
- Queue Call from list.
- Queue Seat from list.
- Seating.
- SeatingResource.
- Table status change.
- Table map.
- Reservation Calendar.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning changes.
- Turnover.
- Production database access.
- Seed data.
- GitHub remote or push.
- Maven Wrapper.

## 4. Baseline

Completed backend API:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
```

Backend guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.rejoin")
```

Completed backend request DTO:

```text
RejoinQueueTicketRequest(note)
```

Completed backend response DTO:

```text
success
queueTicketId
queueTicketNumber
queueTicketStatus
queuePosition
reservationId
reservationCode
reservationStatus
rejoinedAt
alreadyRejoined
events
idempotency
```

Current frontend baseline:

- Existing page: `src/pages/QueueTicketListPage.vue`.
- Existing route: `/stores/:storeId/queue-tickets`.
- Existing page title: `排队列表`.
- Existing page is mobile-first and card-based.
- Existing page reads QueueTicket list data through `GET /api/v1/stores/{storeId}/queue-tickets`.
- Existing page already loads `/api/me/apps` through `fetchMeApps`.
- Existing page already implements Queue Skip with a small local client, native confirmation, idempotency,
  request body `{}`, stable error display, and list refresh behavior.

The future Queue Rejoin UI must extend this approved list page only after a separate implementation
approval.

## 5. UI Placement

Future placement:

```text
QueueTicketListPage.vue
-> each eligible skipped QueueTicket card
-> secondary action area
-> Rejoin action
```

Recommended label:

```text
重新入队
```

Placement rules:

- Show the action only on QueueTicket cards in the Queue List page.
- Keep the action visually secondary to the queue number, status, and reservation summary.
- Do not create a standalone Queue Rejoin page in V1.
- Do not add Staff Home entry `重新入队` in V1.
- Do not add Queue Workbench, Queue Display, table map, Call button, or Seat button to the list as part of
  Rejoin.
- Do not expose Rejoin from Queue Skip success messages or any separate toast action in V1.

## 6. Status Eligibility

Allowed status:

```text
skipped
```

Only `queueTicketStatus = skipped` may expose an active Rejoin action.

Disallowed statuses:

```text
waiting
called
seated
rejoined
expired
cancelled
no_show
completed
closed
unknown future statuses
```

Rules:

- Waiting tickets must not show Rejoin.
- Called tickets must not show Rejoin.
- Seated tickets must not show Rejoin.
- Cancelled, no-show, expired, completed, closed, and terminal tickets must not show Rejoin.
- Unknown status values must default to no active Rejoin action.
- Backend remains authoritative. If stale UI state sends a skipped-looking ticket that is no longer skipped,
  the API returns `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` or another stable backend error and the UI must show
  the returned `error.code` and `error.messageKey`.

## 7. App Gate Behavior

Permission shorthand:

```text
reservation_queue.queue.rejoin
```

Frontend metadata shape:

```text
appKey = reservation_queue
permission = queue.rejoin
```

Visibility rule:

```text
Show Rejoin only when /api/me/apps contains a visible reservation_queue entry
and that entry permissions contains queue.rejoin.
```

Deny or unavailable cases:

- If tenant is not entitled to `reservation_queue`, no active Rejoin control.
- If Store has `reservation_queue` disabled, no active Rejoin control.
- If permissions do not contain `queue.rejoin`, no active Rejoin control.
- If `/api/me/apps` cannot be loaded, no active Rejoin control.
- If backend returns App Gate denial anyway, show the returned `error.code` and `error.messageKey`.

Security rule:

```text
Frontend permission checks are display hints only.
Backend Queue Rejoin API plus App Gate remain the final authorization source.
```

The future UI must not infer authorization from role name alone.

## 8. Confirmation Flow

User flow:

```text
Tap Rejoin
-> confirmation modal, sheet, or existing simple confirmation opens
-> staff confirms the selected skipped ticket should rejoin the queue
-> UI sends POST request
```

V1 confirmation copy meaning:

```text
确认将此过号排队票重新入队？
```

Recommended minimal copy:

```text
确认重新入队 #<queueTicketNumber>？
```

Use the same simple confirmation approach as Queue Skip unless the implementation slice finds a better
existing project confirmation pattern.

Confirmation should show or include enough context to prevent accidental rejoin when the UI pattern allows:

- Queue ticket number.
- Queue ticket status.
- Reservation code if present.
- Party size and party size group.
- Customer name if present.
- Masked phone if present.
- `skippedAt` if returned by a future list API.
- `rejoinedAt` only as read-only historical context if already returned.

Cancel behavior:

- Closing or cancelling the confirmation sends no request.
- No idempotency key is generated until the staff confirms the mutation.

## 9. API Binding

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
```

Sources:

| Value | Source |
| --- | --- |
| `storeId` | Route path `/stores/:storeId/queue-tickets` |
| `queueTicketId` | QueueTicket card data |
| `Idempotency-Key` | Generated by frontend for the confirmed attempt |
| `note` | Not exposed in V1 unless a clean existing note UI pattern is separately approved |

Headers:

```http
Accept: application/json
Content-Type: application/json
Idempotency-Key: <generated-key>
```

V1 request body:

```json
{}
```

Optional staff note:

- Backend supports `note`.
- V1 UI does not expose note input by default.
- If a future implementation slice finds an existing clean staff-note pattern, it may send:

```json
{
  "note": "Customer returned"
}
```

- Blank note must be omitted or normalized away.

Forbidden frontend body fields:

```text
tenantId
storeId
actorId
actorType
queueTicketId
reservationId
tableId
tableGroupId
seatingId
status
targetStatus
queuePosition
ticketNumber
rejoinedAt
skippedAt
calledAt
reasonCode
skip
rejoin
noShow
cancellation
cleaning
turnover
mutation action
```

The UI must not trust or send tenant, actor, reservation, table, seating, status, target status, queue
placement, ticket number, rejoin time, no-show, cancellation, cleaning, or turnover fields.

## 10. Response Handling

Fresh success:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "waiting",
  "queuePosition": 42,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260623-1001",
  "reservationStatus": "arrived",
  "rejoinedAt": "2026-06-23T10:00:00Z",
  "alreadyRejoined": false,
  "events": ["queue_ticket.rejoined"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Already rejoined with complete evidence:

```json
{
  "success": true,
  "queueTicketId": "91000000-0000-0000-0000-000000000001",
  "queueTicketNumber": 12,
  "queueTicketStatus": "waiting",
  "queuePosition": 42,
  "reservationId": "50000000-0000-0000-0000-000000000001",
  "reservationCode": "R-20260623-1001",
  "reservationStatus": "arrived",
  "rejoinedAt": "2026-06-23T10:00:00Z",
  "alreadyRejoined": true,
  "events": [],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

Rules:

- Treat HTTP `200` and `success = true` as success.
- Treat `alreadyRejoined = false` as normal success.
- Treat `alreadyRejoined = true` as terminal success.
- Do not show duplicate fresh-mutation messaging for `alreadyRejoined = true`.
- Do not attempt to reconstruct missing evidence in the frontend.
- Do not infer queue placement beyond what the API response and refreshed list show.

## 11. Idempotency Behavior

The future UI must send `Idempotency-Key`.

Rules:

- Generate one idempotency key per user-confirmed Rejoin attempt.
- Generate the key after confirmation, not when the list renders.
- Reuse the same key only for the same in-flight request retry.
- Use a new key for a new user-confirmed attempt.
- Do not reuse a failed key after `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Do not auto-loop on `IDEMPOTENCY_IN_PROGRESS`.
- Do not send a request without `Idempotency-Key`.
- Duplicate click while loading must not send a duplicate request.

Recommended local helper scope:

- If no shared frontend idempotency helper exists, implement the smallest helper inside the future
  Queue Rejoin API client or page scope.
- Do not introduce a broad platform idempotency framework in the UI implementation slice.

Error handling:

| API code | UI behavior |
| --- | --- |
| `MISSING_IDEMPOTENCY_KEY` | Show code and messageKey. Treat as client bug. |
| `IDEMPOTENCY_CONFLICT` | Show code and messageKey. Require a new confirmed attempt. |
| `IDEMPOTENCY_IN_PROGRESS` | Show code and messageKey. Staff may refresh or retry deliberately after delay. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | Show code and messageKey. New confirmed attempt must use a new key. |

## 12. UI States

Loading:

- Disable the Rejoin action for the target card while the request is in flight.
- Keep the rest of the list readable.
- Show a local loading indicator near the action or confirmation submit button.
- Prevent double submit for the same confirmation.
- Do not block unrelated tickets unless the existing page pattern does so.

Fresh success:

- Treat `success = true` and `alreadyRejoined = false` as completed rejoin.
- Show a short success state using the project i18n/message pattern when available.
- Refresh the current Queue List query.
- Do not navigate away from the Queue List page.

Already rejoined:

- Treat `success = true` and `alreadyRejoined = true` as terminal success.
- Do not show duplicate success messaging as if a fresh mutation happened.
- Refresh the current Queue List query.
- Do not retry endlessly.
- Do not attempt to reconstruct missing evidence in the frontend.

Error:

- Show `error.code`.
- Show `error.messageKey`.
- Keep the affected ticket card visible until the list refreshes.
- Allow a new confirmed attempt only when the error is actionable and the ticket still appears eligible after
  refresh.

Network or invalid response:

- Show a frontend fallback code such as `UNKNOWN_ERROR` or the project-standard network fallback.
- Show a message key, not only free text.

## 13. Refresh Behavior

After fresh success or `alreadyRejoined`:

```text
reload current Queue List query
```

The refresh must preserve:

- Current status filter.
- Current `limit`.
- Current `offset`, unless the current page becomes empty.

Empty page rule:

```text
If refreshed current page is empty and offset/page > 0, move back one page and refresh again.
```

Expected behavior by filter:

| Current filter | Expected after successful rejoin |
| --- | --- |
| `skipped` if exposed later | rejoined ticket usually disappears from the filtered list after refresh. |
| `all` | ticket may remain visible as `waiting` if the list API returns it in the current query result. |
| `waiting` | Rejoin action should not have been available. |
| `called` | Rejoin action should not have been available. |
| `seated` | Rejoin action should not have been available. |

No optimistic status change is required in V1. If used later, it must be reconciled with the API response
and refresh.

Refresh after errors:

- `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` should refresh the list because ticket status may have changed.
- App Gate `403` denial should show graceful error and should not mutate UI as success.
- For App Gate denial, refresh only if the existing page pattern deliberately refreshes metadata/list after
  permission errors.

## 14. Error Display

The UI must preserve backend error identity:

```text
error.code
error.messageKey
```

Do not replace `messageKey` with hardcoded English-only or Chinese-only business copy. Fixed labels such as
`错误代码` and `消息键` are allowed, but the returned values must remain visible.

Minimum codes to handle:

| Code | Expected UI behavior |
| --- | --- |
| `MISSING_IDEMPOTENCY_KEY` | Show client/idempotency error. |
| `QUEUE_TICKET_STATUS_NOT_SKIPPED` | Show stale/invalid-state error and refresh the list. |
| `QUEUE_REJOIN_EVIDENCE_INCOMPLETE` | Show blocking evidence error; no silent success. |
| `IDEMPOTENCY_CONFLICT` | Show conflict; require new confirmed attempt. |
| `IDEMPOTENCY_IN_PROGRESS` | Show retry-later state; no endless retry. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | Show failed-key state; new attempt uses new key. |
| `TENANT_APP_NOT_ENABLED` | Show App Gate denial; active Rejoin control should be unavailable after metadata refresh. |
| `STORE_APP_NOT_ENABLED` | Show App Gate denial; active Rejoin control should be unavailable for the Store. |
| `PERMISSION_DENIED` | Show permission denial; active Rejoin control should be unavailable for the actor. |
| `FORBIDDEN` | Show authorization failure. |
| `STORE_SCOPE_MISMATCH` | Show store scope failure. |
| `QUEUE_TICKET_NOT_FOUND` | Show not-found error and allow refresh. |
| `QUEUE_TICKET_STORE_SCOPE_MISMATCH` | Show store scope failure if returned by a future backend variant. |
| `RESERVATION_NOT_FOUND` | Show backend code and message key. |
| `RESERVATION_STATUS_NOT_ARRIVED` | Show backend code and message key. |
| `ILLEGAL_STATE_TRANSITION` | Show invalid transition error and allow refresh. |
| `QUEUE_REJOIN_WRITE_FAILED` | Show server write failure if returned by a future backend variant. |
| `QUEUE_REJOIN_PERSISTENCE_FAILED` | Show server persistence failure if returned by a future backend variant. |
| `EVENT_WRITE_FAILED` | Show server write failure. |
| `STATE_TRANSITION_WRITE_FAILED` | Show server write failure. |
| `AUDIT_WRITE_FAILED` | Show server write failure. |
| `PERSISTENCE_ERROR` | Show server persistence failure. |
| `UNKNOWN_ERROR` | Show frontend fallback for unknown/network/invalid response. |

The current backend implementation uses `EVENT_WRITE_FAILED`, `STATE_TRANSITION_WRITE_FAILED`,
`AUDIT_WRITE_FAILED`, and `PERSISTENCE_ERROR`. The UI must still display any stable backend `code` and
`messageKey` returned by the API without normalizing them away.

## 15. Accessibility and Mobile UX

The future UI must remain mobile-first:

- Single-column Queue List cards remain the primary layout.
- Rejoin action target is thumb-friendly.
- Confirmation modal, sheet, or native confirm has an obvious cancel path.
- Destructive-action styling is not required; Rejoin is a recovery action but still mutates state.
- Buttons expose accessible names.
- Loading state is announced with `aria-live` or project-equivalent pattern.
- Error state is announced and keeps `error.code` / `error.messageKey` readable.
- Long IDs, codes, and message keys wrap without clipping.
- Queue number and raw status remain prominent.
- Do not add complex tables, drag-and-drop, table map, or display-screen layout.

## 16. Future Frontend Artifacts

If later approved, implementation files should be scoped to:

```text
src/pages/QueueTicketListPage.vue
src/api/queueRejoinApi.ts
src/types/queueRejoin.ts
```

Do not create these files in this contract slice:

```text
src/api/queueRejoinApi.ts
src/types/queueRejoin.ts
```

Future client behavior:

- Call only `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`.
- Send `Idempotency-Key`.
- Send request body `{}` by default.
- Parse success and error envelopes using the current Queue Skip client style.
- Do not create a broad shared mutation framework unless separately approved.

## 17. Future Implementation Test Contract

Visibility and permission tests:

- Rejoin action appears only for skipped ticket with visible `reservation_queue` and `queue.rejoin`.
- Rejoin is hidden when `queue.rejoin` is absent.
- Rejoin is hidden when `/api/me/apps` does not return visible `reservation_queue`.
- Rejoin is hidden for waiting ticket.
- Rejoin is hidden for called ticket.
- Rejoin is hidden for seated ticket.
- Rejoin is hidden for cancelled ticket.
- Rejoin is hidden for no-show ticket.
- Rejoin is hidden for terminal or unknown status.
- Backend `403` / App Gate denial displays `error.code` and `error.messageKey`.

Confirmation tests:

- Clicking Rejoin opens confirmation.
- Cancelling confirmation sends no request.
- Confirming sends one request.
- No idempotency key is generated before confirmation.

API client tests:

- Uses `POST`.
- Calls `/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`.
- Sends `Accept: application/json`.
- Sends `Content-Type: application/json`.
- Sends `Idempotency-Key`.
- Request body is exactly `{}` unless a separately approved note UI is implemented.
- Does not send forbidden fields.

Idempotency tests:

- Generates one idempotency key per confirmed attempt.
- Reuses the same key only for the same in-flight retry.
- Uses a new key for a new confirmed attempt.
- Duplicate click while loading sends no duplicate request.
- Handles `MISSING_IDEMPOTENCY_KEY`.
- Handles `IDEMPOTENCY_CONFLICT`.
- Handles `IDEMPOTENCY_IN_PROGRESS`.
- Handles failed-key-requires-new-key behavior.

Success tests:

- Fresh success refreshes the current Queue List query.
- Fresh success does not navigate away.
- `alreadyRejoined = true` is treated as terminal success.
- `alreadyRejoined = true` refreshes the list.
- `alreadyRejoined = true` does not show duplicate fresh-mutation messaging.
- Empty refreshed page with previous page available decrements page/offset and refreshes again.

Error tests:

- `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` displays `error.code` and `error.messageKey`.
- `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` refreshes the list.
- `QUEUE_REJOIN_EVIDENCE_INCOMPLETE` displays `error.code` and `error.messageKey`.
- App Gate denial displays `error.code` and `error.messageKey`.
- App Gate denial does not mutate local UI as success.
- Network or invalid response displays a stable fallback code and message key.
- No hardcoded business copy replaces the backend `messageKey`.

Boundary tests:

- No Queue Rejoin UI outside `QueueTicketListPage.vue`.
- No Queue Display API or UI.
- No Queue Workbench mutation.
- No Queue Call from list.
- No Queue Seat from list.
- No Seating or SeatingResource creation.
- No Table status change.
- No Table map.
- No Reservation Calendar.
- No Auto assignment.
- No No-show.
- No Cancellation.
- No Cleaning or Turnover change.
- No backend API change.
- No migration.

## 18. Boundary Confirmation

Queue Rejoin UI implemented: No  
Queue Rejoin frontend API client implemented: No  
Queue Rejoin frontend types implemented: No  
Queue Rejoin API changed: No  
Queue Display API/UI implemented: No  
Queue Workbench mutation implemented: No  
Queue Call from list implemented: No  
Queue Seat from list implemented: No  
Seating implemented: No  
Table status changed: No  
Table map implemented: No  
Reservation Calendar implemented: No  
No-show API/UI implemented: No  
Cancellation API/UI implemented: No  
Cleaning changed: No  
Turnover API/UI implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No  
GitHub remote added: No  
GitHub push performed: No  
Maven Wrapper added: No  

## 19. Open Questions

- Should a later UI expose an optional staff note for Rejoin, or should V1 always send `{}` like Queue Skip
  UI V1?
- Should the future Queue List UI expose a `skipped` filter before Rejoin UI ships, or should Rejoin appear
  only when skipped tickets are visible through `all` or a later expanded filter?

## 20. Open Conflicts

- None for the UI contract. The backend API is already implemented and committed, while this document only
  governs the future frontend UI/client slice.

## 21. Next Step Recommendation

Next approved slice:

```text
Queue Rejoin UI Implementation V1
```

Recommended future implementation files:

- `src/api/queueRejoinApi.ts`
- `src/types/queueRejoin.ts`
- minimal additions to `src/pages/QueueTicketListPage.vue`

The implementation slice must keep Queue Display, Queue Workbench, Queue Call from list, Queue Seat from
list, Seating, Table map, Reservation Calendar, Auto assignment, No-show, Cancellation, Cleaning, Turnover,
backend API changes, App Gate metadata changes, migrations, SQL, production config, production database
access, seed data, GitHub remote/push, and Maven Wrapper out of scope unless separately approved.
