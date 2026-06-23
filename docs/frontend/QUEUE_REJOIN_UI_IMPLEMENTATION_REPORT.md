# Queue Rejoin UI Implementation Report V1

## Summary

Queue Rejoin UI V1 has been implemented on the existing Queue Ticket List page only.

Business action:

```text
重新入队
```

Implemented flow:

```text
eligible skipped QueueTicket card
-> staff confirms Rejoin
-> frontend sends POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin
-> request includes Idempotency-Key
-> request body is {}
-> success or alreadyRejoined refreshes the current list
```

## Files Changed

- `src/pages/QueueTicketListPage.vue`
- `src/api/queueRejoinApi.ts`
- `src/types/queueRejoin.ts`
- `src/test/java/com/rpb/reservation/appgate/ui/QueueRejoinUiImplementationValidationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/QueueSkipUiImplementationValidationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/QueueCallUiImplementationValidationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/SeatingFromCalledQueueUiImplementationValidationTest.java`
- `docs/frontend/QUEUE_REJOIN_UI_IMPLEMENTATION_REPORT.md`

## UI Behavior

- Adds a `重新入队` action to `QueueTicketListPage.vue`.
- Shows the action only for `queueTicketStatus === 'skipped'`.
- Hides the action for waiting, called, seated, cancelled, no-show, completed, terminal, and unknown statuses by default.
- Requires `reservation_queue` app metadata plus `queue.rejoin` permission from `/api/me/apps`.
- Uses the same native confirmation style as Queue Skip.
- Cancelling confirmation sends no API request.
- While the request is in flight, only the active ticket's Rejoin button is disabled.
- Duplicate clicks for the same ticket are blocked while loading.
- No standalone Queue Rejoin page, route, Staff Home entry, skipped-only filter, or extra component was added.

## API Client And Types

Added `src/api/queueRejoinApi.ts`:

- Exposes `rejoinQueueTicket(storeId, queueTicketId, idempotencyKey, fetcher)`.
- Calls `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`.
- Sends `Accept: application/json`.
- Sends `Content-Type: application/json`.
- Sends `Idempotency-Key`.
- Parses success and backend error envelopes.
- Converts network, invalid JSON, and unknown response shapes to `UNKNOWN_ERROR` with `queue.rejoin.unknown_error`.

Added `src/types/queueRejoin.ts`:

- `RejoinQueueTicketResponse`
- `QueueRejoinApiErrorResponse`
- `QueueRejoinApiErrorBody`
- `QueueRejoinIdempotency`
- `QueueRejoinIdempotencyStatus`

The response type includes queue ticket summary, reservation summary, `queuePosition`, `rejoinedAt`, `alreadyRejoined`, `events`, and idempotency state.

## App Gate Behavior

Frontend visibility uses existing app metadata:

```text
appKey: reservation_queue
permission: queue.rejoin
```

The button is hidden when app metadata is missing, the app is not visible, or `queue.rejoin` is absent. Backend App Gate remains authoritative. If backend returns `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, or `PERMISSION_DENIED`, the UI shows returned `error.code` and `error.messageKey` and does not mutate local UI as success.

## Idempotency Behavior

- One idempotency key is generated per confirmed Rejoin attempt.
- No key is generated before confirmation.
- The key prefix is `queue:rejoin`.
- Duplicate click while the request is in flight does not send a second request.
- A new confirmed attempt generates a new key.
- No broad shared idempotency framework was introduced.

## Request Body Proof

The API client sends:

```ts
body: JSON.stringify({})
```

Forbidden fields are not sent:

```text
note
skippedAt
rejoinedAt
reasonCode
targetStatus
queuePosition
ticketNumber
tenantId
storeId in body
actorId
reservationId
tableId
seatingId
status
skip
noShow
cancellation
cleaning
turnover
```

## Success And Error Behavior

Success:

- HTTP 200 plus `success: true` is treated as success.
- `alreadyRejoined=false` shows normal success feedback.
- `alreadyRejoined=true` is treated as terminal success with non-duplicate success wording.
- The current Queue List query is refreshed.
- If refreshed current page is empty and `offset > 0`, the page offset moves back by one page and refreshes again.

Errors:

- Stable backend `error.code` is displayed.
- Stable backend `error.messageKey` is displayed.
- `QUEUE_TICKET_STATUS_NOT_SKIPPED` displays the stable error and refreshes the list.
- App Gate denial displays the stable error and does not refresh/mutate as success.
- Unknown/network/invalid responses show `UNKNOWN_ERROR` and `queue.rejoin.unknown_error`.

## Test Results

Red check:

```text
mvn -q "-Dtest=QueueRejoinUiImplementationValidationTest" test
```

Initial result before implementation: failed because `src/api/queueRejoinApi.ts` and related UI contract artifacts were absent.

Final validation:

```text
mvn -q "-Dtest=QueueRejoinUiImplementationValidationTest" test: PASS
mvn -q "-Dtest=QueueRejoin*Test" test: PASS
mvn -q "-Dtest=QueueRejoinUi*Test" test: PASS
mvn -q "-Dtest=QueueSkip*Test" test: PASS
mvn -q test: PASS
cmd /c npm run build: PASS
cmd /c npx vue-tsc --noEmit: PASS
cmd /c npx vite build: PASS
```

`package.json` has no dedicated `npm test` script.

## Runtime Validation

HTTP shell checks:

```text
http://127.0.0.1:5176/stores/20000000-0000-0000-0000-000000000983/queue-tickets: HTTP 200
Vue app shell: present
http://127.0.0.1:8080/api/me/apps: HTTP 403
```

The active frontend process was reachable on `5176`, and the active Java backend responded on `8080`, but the backend did not expose an authenticated local UI session for `/api/me/apps`. Because of that, browser-level confirmation/click validation was partial in this slice.

Runtime-equivalent backend/API behavior for Rejoin was covered by the existing Queue Rejoin integration and local runtime security tests that passed under Maven.

## DB Side-Effect Boundary

DB side-effect validation was covered by the passing Queue Rejoin backend integration tests. Those tests exercise successful rejoin, replay/alreadyRejoined behavior, App Gate denial, idempotency, audit/event/state-transition evidence, and forbidden side-effect boundaries.

No production database was touched.

## File Boundary Check

Confirmed in source boundary tests and Git inspection:

- No backend API behavior change.
- No migration change.
- No App Gate metadata change.
- No Queue Rejoin route/page/component.
- No Queue List skipped-only filter.
- No Queue Display API/UI.
- No Queue Workbench mutation.
- No Queue Call from list.
- No Queue Seat from list.
- No Seating UI.
- No Table status or Table map.
- No Reservation Calendar.
- No No-show or Cancellation UI/API.
- No Cleaning or Turnover change.
- No GitHub remote.
- No Maven Wrapper.

Old UI boundary tests were updated narrowly to allow the now-approved `src/api/queueRejoinApi.ts` while continuing to forbid unrelated frontend API/UI artifacts.

## Open Questions

- Full browser click-through requires a local frontend session backed by local auth metadata that allows `/api/me/apps` and contains `queue.rejoin`.

## Open Conflicts

- None in source/test scope.
- Browser runtime validation is partial because the active local backend returned `403` for `/api/me/apps`.

## Next Step Recommendation

Commit the Queue Rejoin UI implementation and report after review. If a stronger manual browser proof is required before commit, restart backend with the local-auth properties used by `QueueRejoinLocalRuntimeSecurityTest` and seed/fixture data visible to the Queue List page.

## Boundary Statement

Queue Rejoin UI implemented: Yes
Queue Rejoin frontend API client implemented: Yes
Queue Rejoin frontend types implemented: Yes
Queue Rejoin API changed: No
Queue List skipped filter implemented: No
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
