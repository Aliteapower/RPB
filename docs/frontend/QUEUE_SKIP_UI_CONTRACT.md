# Queue Skip UI Contract V1

## 1. Purpose

Define the future frontend UI contract for skipping a called QueueTicket from the existing Store Staff Queue Ticket List surface.

Target flow:

```text
Store staff opens Queue Ticket List
-> UI displays called QueueTicket cards
-> Staff confirms Skip on an eligible called ticket
-> UI sends POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
-> Backend App Gate checks reservation_queue / queue.skip
-> QueueTicket called -> skipped
-> Reservation remains arrived
-> UI refreshes the current Queue List query
```

This document is contract only. It does not implement a Vue page, frontend API client, router change, Staff Home change, backend API, App Gate metadata, migration, SQL, seed data, or production data change.

## 2. Scope

In scope for a future UI implementation:

- Queue Skip action placement on the existing `QueueTicketListPage.vue` queue ticket card.
- Status eligibility for showing or enabling the Skip action.
- Confirmation flow before mutation.
- Optional `skippedAt`, `reasonCode`, and `note` handling.
- Loading, success, `alreadySkipped`, error, and App Gate denied states.
- Idempotency key generation and retry behavior.
- Refresh behavior after success.
- Mobile-first, accessible basic UX expectations.
- Future implementation test contract.

## 3. Non-Scope

Out of scope for this UI contract:

- Queue Skip UI implementation.
- Queue Skip frontend API client implementation.
- Vue Router changes.
- Staff Home changes.
- Backend API changes.
- App Gate metadata changes.
- Migration or SQL changes.
- Queue Rejoin.
- Queue Display.
- Queue Workbench mutation.
- Queue Call from list.
- Queue Seat from list.
- Seating.
- SeatingResource.
- Table status change.
- Table map.
- Auto assignment.
- No-show.
- Cancellation.
- Cleaning.
- Turnover.
- Production database access.
- Seed data.

## 4. Baseline

Completed backend API:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

Backend guard:

```java
@RequireAppGate(appKey = "reservation_queue", permission = "queue.skip")
```

Completed read UI baseline:

- Existing page: `src/pages/QueueTicketListPage.vue`.
- Existing route: `/stores/:storeId/queue-tickets`.
- Existing page title: `排队列表`.
- Existing page is mobile-first and card-based.
- Existing page reads QueueTicket list data through `GET /api/v1/stores/{storeId}/queue-tickets`.
- Existing page already displays `queueTicketNumber`, `queueTicketStatus`, `partySize`, `partySizeGroup`, reservation summary, `calledAt`, and `holdUntilAt`.

The future Queue Skip UI must extend this approved list page only after a separate implementation approval.

## 5. UI Placement

Future placement:

```text
QueueTicketListPage.vue
-> each eligible QueueTicket card
-> secondary action area
-> Skip action
```

Recommended label:

```text
过号
```

Placement rules:

- Show the action only on QueueTicket cards in the Queue List page.
- Keep the action visually secondary to the queue number, status, and hold time.
- Do not create a standalone Queue Skip page in V1.
- Do not add Staff Home entry `过号处理` in V1.
- Do not add Queue Workbench, Queue Display, table map, Call button, or Seat button to the list as part of Skip.

## 6. Status Eligibility

Allowed status:

```text
called
```

Only `queueTicketStatus = called` may expose an active Skip action.

Disallowed statuses:

```text
waiting
seated
skipped
rejoined
expired
cancelled
no_show
completed
closed
unknown future statuses
```

Rules:

- Waiting tickets must not be skipped from the UI.
- Seated tickets must not be skipped from the UI.
- Already skipped tickets must not show an active Skip action.
- Unknown status values must default to no active Skip action.
- Backend remains authoritative. If stale UI state sends a called-looking ticket that is no longer called, the API returns `409 QUEUE_TICKET_STATUS_NOT_CALLED` and the UI must show the returned `error.code` and `error.messageKey`.

## 7. Confirmation Flow

User flow:

```text
Tap Skip
-> confirmation modal or bottom sheet opens
-> staff reviews ticket summary
-> staff optionally enters reasonCode and note
-> staff confirms
-> UI sends POST request
```

Confirmation must show enough context to prevent accidental skip:

- Queue ticket number.
- Queue ticket status.
- Reservation code if present.
- Party size and party size group.
- Customer name if present.
- Masked phone if present.
- `calledAt` if present.
- `holdUntilAt` if present.

Request fields from confirmation:

| Field | UI behavior |
| --- | --- |
| `skippedAt` | Optional. V1 UI should normally omit this and let backend clock decide. If a future UI exposes manual time, it must send ISO8601 only. |
| `reasonCode` | Optional. Trim before send. V1 does not require reason code metadata validation. |
| `note` | Optional. Trim before send. Empty note is omitted. |

Cancel behavior:

- Closing or cancelling the confirmation sends no request.
- No idempotency key is generated until the staff confirms the mutation.

## 8. API Binding

Endpoint:

```http
POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip
```

Sources:

| Value | Source |
| --- | --- |
| `storeId` | Route path `/stores/:storeId/queue-tickets` |
| `queueTicketId` | QueueTicket card data |
| `Idempotency-Key` | Generated by frontend for the confirmed attempt |
| `skippedAt` | Optional confirmation field |
| `reasonCode` | Optional confirmation field |
| `note` | Optional confirmation field |

Headers:

```http
Accept: application/json
Content-Type: application/json
Idempotency-Key: <generated-key>
```

Request body allowlist:

```json
{
  "skippedAt": "2026-06-22T11:45:00Z",
  "reasonCode": "NO_RESPONSE",
  "note": "Customer did not return after call"
}
```

All body fields are optional. The future client must omit blank optional fields rather than sending empty strings.

Forbidden request fields:

```text
tenantId
storeId in body
actorId
actorType
reservationId
tableId
tableGroupId
seatingId
cleaningId
turnoverId
rejoinReason
noShowAt
cancelledAt
status
mutation action
```

The UI must not trust or send tenant, actor, reservation, table, seating, rejoin, no-show, cancellation, cleaning, turnover, or status mutation fields.

## 9. App Gate Behavior

Permission shorthand:

```text
reservation_queue.queue.skip
```

Frontend metadata shape:

```text
appKey = reservation_queue
permission = queue.skip
```

Visibility rule:

```text
Show or enable Skip only when /api/me/apps contains a visible reservation_queue entry
and that entry permissions contains queue.skip.
```

Deny or unavailable cases:

- If tenant is not entitled to `reservation_queue`, no active Skip control.
- If store has `reservation_queue` disabled, no active Skip control.
- If permissions do not contain `queue.skip`, no active Skip control.
- If `/api/me/apps` cannot be loaded, no active Skip control.
- If backend returns App Gate denial anyway, show the returned `error.code` and `error.messageKey`.

Security rule:

```text
Frontend permission checks are display hints only.
Backend Queue Skip API plus App Gate remain the final authorization source.
```

The future UI must not infer authorization from role name alone.

## 10. Idempotency Behavior

The future UI must send `Idempotency-Key`.

Rules:

- Generate one idempotency key per user-confirmed Skip attempt.
- Generate the key after confirmation, not when the list renders.
- Reuse the same key only for the same in-flight request retry.
- Use a new key for a new user-confirmed attempt.
- Do not reuse a failed key after `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Do not auto-loop on `IDEMPOTENCY_IN_PROGRESS`.
- Do not send a request without `Idempotency-Key`.

Error handling:

| API code | UI behavior |
| --- | --- |
| `MISSING_IDEMPOTENCY_KEY` | Show code and messageKey. Treat as client bug. |
| `IDEMPOTENCY_CONFLICT` | Show code and messageKey. Require a new confirmed attempt. |
| `IDEMPOTENCY_IN_PROGRESS` | Show code and messageKey. Staff may refresh or retry deliberately after delay. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | Show code and messageKey. New confirmed attempt must use a new key. |

## 11. UI States

Loading:

- Disable the Skip action for the target card while the request is in flight.
- Keep the rest of the list readable.
- Show a local loading indicator near the action or confirmation submit button.
- Prevent double submit for the same confirmation.

Fresh success:

- Treat `success = true` and `alreadySkipped = false` as completed skip.
- Show a short success state using the project i18n/message pattern when available.
- Refresh the current Queue List query.
- Do not navigate away from the Queue List page.

Already skipped:

- Treat `success = true` and `alreadySkipped = true` as terminal success.
- Do not show duplicate success messaging as if a fresh mutation happened.
- Refresh the current Queue List query.
- Do not retry endlessly.
- Do not attempt to reconstruct missing evidence in the frontend.

Error:

- Show `error.code`.
- Show `error.messageKey`.
- Keep the affected ticket card visible until the list refreshes.
- Allow a new confirmed attempt only when the error is actionable and the ticket still appears eligible after refresh.

Network or invalid response:

- Show a frontend fallback code such as `UNKNOWN_ERROR` or the project-standard network fallback.
- Show a message key, not only free text.

## 12. Refresh Behavior

After fresh success or `alreadySkipped`:

```text
reload current Queue List query
```

The refresh must preserve:

- Current status filter.
- Current `limit`.
- Current `offset`, unless implementation intentionally resets to avoid an empty page.

Expected behavior by filter:

| Current filter | Expected after successful skip |
| --- | --- |
| `called` | skipped ticket usually disappears from the filtered list after refresh. |
| `all` | skipped ticket may remain visible if the list API returns skipped status in the current query result. |
| `waiting` | skip action should not have been available. |
| `seated` | skip action should not have been available. |

No optimistic status change is required in V1. If used later, it must be reconciled with the API response and refresh.

## 13. Error Display

The UI must preserve backend error identity:

```text
error.code
error.messageKey
```

Do not replace `messageKey` with hardcoded English-only or Chinese-only business copy. Fixed labels such as `错误代码` and `消息键` are allowed, but the returned values must remain visible.

Minimum codes to handle:

| Code | Expected UI behavior |
| --- | --- |
| `QUEUE_TICKET_STATUS_NOT_CALLED` | Show stale/invalid-state error and refresh option. |
| `QUEUE_SKIP_EVIDENCE_INCOMPLETE` | Show blocking evidence error; no silent success. |
| `MISSING_IDEMPOTENCY_KEY` | Show client/idempotency error. |
| `IDEMPOTENCY_CONFLICT` | Show conflict; require new confirmed attempt. |
| `IDEMPOTENCY_IN_PROGRESS` | Show retry-later state; no endless retry. |
| `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` | Show failed-key state; new attempt uses new key. |
| `TENANT_APP_NOT_ENABLED` | Show App Gate denial; active Skip control should be unavailable after metadata refresh. |
| `STORE_APP_NOT_ENABLED` | Show App Gate denial; active Skip control should be unavailable after metadata refresh. |
| `PERMISSION_DENIED` | Show permission denial; active Skip control should be unavailable for the actor. |
| `FORBIDDEN` | Show authorization failure. |
| `STORE_SCOPE_MISMATCH` | Show store scope failure. |
| `PERSISTENCE_ERROR` | Show server persistence failure. |
| `EVENT_WRITE_FAILED` | Show server write failure. |
| `STATE_TRANSITION_WRITE_FAILED` | Show server write failure. |
| `AUDIT_WRITE_FAILED` | Show server write failure. |
| `UNKNOWN_ERROR` | Show frontend fallback for unknown/network/invalid response. |

## 14. Accessibility and Mobile UX

The future UI must remain mobile-first:

- Single-column Queue List cards remain the primary layout.
- Skip action target is thumb-friendly.
- Confirmation modal or sheet traps focus while open.
- Confirmation can be cancelled by an obvious cancel control.
- Destructive action styling is clear but not visually dominant.
- Buttons expose accessible names.
- Loading state is announced with `aria-live` or project-equivalent pattern.
- Error state is announced and keeps `error.code` / `error.messageKey` readable.
- Long IDs, codes, and message keys wrap without clipping.
- Called ticket `holdUntilAt` remains prominent.
- Do not add complex tables, drag-and-drop, table map, or display-screen layout.

## 15. Future Implementation Test Contract

Visibility and permission tests:

- Skip is visible or enabled only when `reservation_queue` is visible and permissions contains `queue.skip`.
- Skip is hidden or disabled when `queue.skip` is absent.
- Skip is hidden or disabled when `/api/me/apps` does not return visible `reservation_queue`.
- Backend `403` / App Gate denial displays `error.code` and `error.messageKey`.

Status tests:

- Called ticket shows active Skip action.
- Waiting ticket does not show active Skip action.
- Seated ticket does not show active Skip action.
- Skipped ticket does not show active Skip action.
- Cancelled, expired, rejoined, no-show, completed, closed, and unknown statuses do not show active Skip action.

Confirmation tests:

- Clicking Skip opens confirmation.
- Cancelling confirmation sends no request.
- Confirming sends one request.
- Confirmation displays queue ticket number, status, reservation code, party size, masked phone, `calledAt`, and `holdUntilAt` when present.

API client tests:

- Uses `POST`.
- Calls `/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip`.
- Sends `Idempotency-Key`.
- Request body contains only `skippedAt`, `reasonCode`, and `note`.
- Omits blank optional fields.
- Does not send forbidden fields.

Idempotency tests:

- Generates one idempotency key per confirmed attempt.
- Reuses the same key only for the same in-flight retry.
- Uses a new key for a new confirmed attempt.
- Handles `MISSING_IDEMPOTENCY_KEY`.
- Handles `IDEMPOTENCY_CONFLICT`.
- Handles `IDEMPOTENCY_IN_PROGRESS`.
- Handles failed-key-requires-new-key behavior.

Success tests:

- Fresh success refreshes the current Queue List query.
- Fresh success does not navigate away.
- Fresh success does not create Call, Seat, Rejoin, Display, Table map, or Workbench UI behavior.
- `alreadySkipped = true` is treated as terminal success.
- `alreadySkipped = true` refreshes the list.
- `alreadySkipped = true` does not show duplicate fresh-mutation messaging.

Error tests:

- `409 QUEUE_TICKET_STATUS_NOT_CALLED` displays `error.code` and `error.messageKey`.
- `QUEUE_SKIP_EVIDENCE_INCOMPLETE` displays `error.code` and `error.messageKey`.
- App Gate denial displays `error.code` and `error.messageKey`.
- Network or invalid response displays a stable fallback code and message key.
- No hardcoded business copy replaces the backend `messageKey`.

Boundary tests:

- No Queue Rejoin API or UI.
- No Queue Display API or UI.
- No Queue Workbench mutation.
- No Queue Call from list.
- No Queue Seat from list.
- No Seating or SeatingResource creation.
- No Table status change.
- No Table map.
- No Auto assignment.
- No No-show.
- No Cancellation.
- No Cleaning or Turnover change.
- No migration.
- No backend API change.

## 16. Boundary Confirmation

Queue Skip UI implemented: No  
Queue Skip frontend API client implemented: No  
Queue Rejoin API implemented: No  
Queue Display API implemented: No  
Queue Workbench mutation implemented: No  
Queue Call from list implemented: No  
Queue Seat from list implemented: No  
Seating implemented: No  
SeatingResource created: No  
Table status changed: No  
Table map implemented: No  
Auto assignment implemented: No  
No-show API implemented: No  
Cancellation API implemented: No  
Cleaning API changed: No  
Turnover API implemented: No  
Migration changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No  

## 17. Open Questions

- Should the future implementation hide ineligible Skip actions entirely, or show disabled controls with a reason for `waiting`, `seated`, and `skipped` tickets?
- Should `reasonCode` be free text in V1 UI, or should the UI wait for a later reason-code metadata source before exposing a select control?
- Should successful Skip on a `called` filter preserve the same offset even if the current page becomes empty, or reset to the previous available page?

## 18. Open Conflicts

- None.

## 19. Next Step Recommendation

Next approved slice:

```text
Queue Skip UI Implementation V1
```

Recommended future implementation files:

- `src/api/queueSkipApi.ts`
- `src/types/queueSkip.ts`
- minimal additions to `src/pages/QueueTicketListPage.vue`

The implementation slice must keep Queue Rejoin, Queue Display, Queue Workbench, Queue Call from list, Queue Seat from list, Seating, Table map, Auto assignment, No-show, Cancellation, Cleaning, Turnover, backend API changes, App Gate metadata changes, migrations, SQL, production config, production database access, and seed data out of scope unless separately approved.
