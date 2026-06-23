# Queue Rejoin UI Checklist V1

## 1. Contract Scope

- [x] This round only creates Queue Rejoin UI contract documentation.
- [x] `docs/frontend/QUEUE_REJOIN_UI_CONTRACT.md` is created.
- [x] `docs/frontend/QUEUE_REJOIN_UI_CHECKLIST.md` is created to match the existing frontend UI checklist convention.
- [x] No Vue page is created or modified.
- [x] No frontend API client is created.
- [x] No frontend type file is created.
- [x] No Vue Router entry is created or modified.
- [x] No Staff Home source change is made.
- [x] No backend API change is made.
- [x] No App Gate metadata change is made.
- [x] No migration, SQL, database structure, production config, seed data, or production data change is made.

## 2. Baseline Reads

- [x] Queue Rejoin API contract considered.
- [x] Queue Rejoin API implementation report considered.
- [x] Queue Skip API contract considered.
- [x] Queue Skip API implementation report considered.
- [x] Queue Skip UI contract considered.
- [x] Queue Skip UI checklist considered.
- [x] Queue List UI contract considered.
- [x] Architecture governance considered.
- [x] Business glossary considered.
- [x] Business rules considered.
- [x] Data standard considered.
- [x] Existing Queue Ticket List page pattern considered.
- [x] Existing Queue Skip API client and type pattern considered.
- [x] Existing Staff Home `/api/me/apps` permission pattern considered.
- [x] Existing Pinia store context pattern considered.
- [x] Existing Vue Router pattern considered.

## 3. UI Placement

- [x] Future Rejoin action is scoped to `QueueTicketListPage.vue`.
- [x] Future Rejoin action belongs on eligible QueueTicket cards.
- [x] Future Rejoin action is secondary to queue number, status, and reservation summary.
- [x] Recommended action label is `重新入队`.
- [x] No standalone Queue Rejoin page is designed.
- [x] No Staff Home `重新入队` entry is designed.
- [x] No Queue Workbench is designed.
- [x] No Queue Display is designed.
- [x] No Table map is designed.

## 4. Status Eligibility

- [x] `skipped` is the only allowed status for active Rejoin.
- [x] `waiting` is not allowed.
- [x] `called` is not allowed.
- [x] `seated` is not allowed.
- [x] `rejoined` is not allowed.
- [x] `expired` is not allowed.
- [x] `cancelled` is not allowed.
- [x] `no_show` is not allowed.
- [x] `completed` is not allowed.
- [x] `closed` is not allowed.
- [x] Unknown future statuses default to no active Rejoin action.
- [x] Stale state maps to API error `QUEUE_TICKET_STATUS_NOT_SKIPPED` or another stable backend error.

## 5. App Gate

- [x] Product shorthand permission is `reservation_queue.queue.rejoin`.
- [x] Frontend metadata app key is `reservation_queue`.
- [x] Frontend permission value is `queue.rejoin`.
- [x] Active Rejoin requires visible `reservation_queue` entry.
- [x] Active Rejoin requires permissions containing `queue.rejoin`.
- [x] Tenant app denial means no active Rejoin control.
- [x] Store app denial means no active Rejoin control.
- [x] Permission denial means no active Rejoin control.
- [x] `/api/me/apps` failure means no active Rejoin control.
- [x] Frontend checks are display hints only.
- [x] Backend App Gate remains final authorization.

## 6. Confirmation

- [x] Rejoin requires confirmation before mutation.
- [x] Confirmation copy meaning is documented.
- [x] Suggested Chinese label/copy is documented.
- [x] Confirmation should include ticket context when the UI pattern allows it.
- [x] Cancel sends no request.
- [x] No idempotency key is generated until confirm.

## 7. Request Body

- [x] V1 request body is `{}` by default.
- [x] Backend-supported `note` is documented as optional only if a clean existing note pattern is approved.
- [x] V1 does not expose `reasonCode`.
- [x] V1 does not expose `targetStatus`.
- [x] V1 does not expose `queuePosition`.
- [x] V1 does not expose `ticketNumber`.
- [x] V1 does not expose `rejoinedAt`.
- [x] Backend clock supplies `rejoinedAt`.

## 8. Forbidden Fields

- [x] Does not send `tenantId`.
- [x] Does not send `storeId` in body.
- [x] Does not send `actorId`.
- [x] Does not send `actorType`.
- [x] Does not send `queueTicketId` in body.
- [x] Does not send `reservationId`.
- [x] Does not send `tableId`.
- [x] Does not send `tableGroupId`.
- [x] Does not send `seatingId`.
- [x] Does not send `status`.
- [x] Does not send `targetStatus`.
- [x] Does not send `queuePosition`.
- [x] Does not send `ticketNumber`.
- [x] Does not send `rejoinedAt`.
- [x] Does not send `skippedAt`.
- [x] Does not send `calledAt`.
- [x] Does not send `reasonCode`.
- [x] Does not send `skip`.
- [x] Does not send `rejoin`.
- [x] Does not send `noShow`.
- [x] Does not send `cancellation`.
- [x] Does not send `cleaning`.
- [x] Does not send `turnover`.
- [x] Does not send mutation action in body.

## 9. API Binding

- [x] Future UI calls `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin`.
- [x] `storeId` comes from route path.
- [x] `queueTicketId` comes from QueueTicket card data.
- [x] `Idempotency-Key` header is required.
- [x] `Accept: application/json` is used.
- [x] `Content-Type: application/json` is used.
- [x] Existing API paths are not changed.

## 10. Idempotency

- [x] One key is generated per user-confirmed attempt.
- [x] Key is generated after confirmation.
- [x] Same key is reused only for the same in-flight retry.
- [x] New confirmed attempt uses a new key.
- [x] Failed idempotency key is not reused after failed-key-requires-new-key.
- [x] UI does not auto-loop on in-progress response.
- [x] UI never sends Rejoin without `Idempotency-Key`.
- [x] Duplicate click while loading sends no duplicate request.
- [x] `MISSING_IDEMPOTENCY_KEY` is handled.
- [x] `IDEMPOTENCY_CONFLICT` is handled.
- [x] `IDEMPOTENCY_IN_PROGRESS` is handled.
- [x] `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` is handled.

## 11. UI States

- [x] Loading disables the target card Rejoin action.
- [x] Loading prevents double submit.
- [x] Loading keeps the rest of the list readable.
- [x] Fresh success is terminal success.
- [x] Fresh success refreshes the current Queue List query.
- [x] Fresh success does not navigate away.
- [x] `alreadyRejoined = true` is terminal success.
- [x] `alreadyRejoined = true` refreshes the current Queue List query.
- [x] `alreadyRejoined = true` does not duplicate fresh mutation messaging.
- [x] Error display keeps the affected ticket card visible until refresh.
- [x] Network or invalid response uses a stable fallback code and message key.

## 12. Error Display

- [x] Error display must show `error.code`.
- [x] Error display must show `error.messageKey`.
- [x] UI must not replace backend `messageKey` with hardcoded business copy.
- [x] `MISSING_IDEMPOTENCY_KEY` is covered.
- [x] `QUEUE_TICKET_STATUS_NOT_SKIPPED` is covered.
- [x] `QUEUE_REJOIN_EVIDENCE_INCOMPLETE` is covered.
- [x] `IDEMPOTENCY_CONFLICT` is covered.
- [x] `IDEMPOTENCY_IN_PROGRESS` is covered.
- [x] `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` is covered.
- [x] `TENANT_APP_NOT_ENABLED` is covered.
- [x] `STORE_APP_NOT_ENABLED` is covered.
- [x] `PERMISSION_DENIED` is covered.
- [x] `FORBIDDEN` is covered.
- [x] `STORE_SCOPE_MISMATCH` is covered.
- [x] `QUEUE_TICKET_NOT_FOUND` is covered.
- [x] `QUEUE_TICKET_STORE_SCOPE_MISMATCH` is covered as a future/stable backend variant.
- [x] `QUEUE_REJOIN_WRITE_FAILED` is covered as a future/stable backend variant.
- [x] `QUEUE_REJOIN_PERSISTENCE_FAILED` is covered as a future/stable backend variant.
- [x] `EVENT_WRITE_FAILED` is covered.
- [x] `STATE_TRANSITION_WRITE_FAILED` is covered.
- [x] `AUDIT_WRITE_FAILED` is covered.
- [x] `PERSISTENCE_ERROR` is covered.
- [x] `UNKNOWN_ERROR` is covered.

## 13. Refresh Behavior

- [x] Fresh success reloads the current Queue List query.
- [x] `alreadyRejoined` reloads the current Queue List query.
- [x] Refresh preserves status filter.
- [x] Refresh preserves `limit`.
- [x] Refresh preserves `offset` unless current page becomes empty.
- [x] Empty page with previous page available decrements page/offset and refreshes again.
- [x] `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` refreshes the list.
- [x] App Gate denial does not mutate UI as success.
- [x] No optimistic update is required in V1.

## 14. Accessibility and Mobile UX

- [x] Queue List remains single-column and mobile-first.
- [x] Rejoin target must be thumb-friendly.
- [x] Confirmation has an obvious cancel path.
- [x] Buttons expose accessible names.
- [x] Loading state is announced.
- [x] Error state is announced.
- [x] Long codes and message keys wrap.
- [x] Queue number and raw status remain prominent.
- [x] No complex table layout.
- [x] No drag-and-drop.
- [x] No display-screen layout.

## 15. Future Test Contract

- [x] Rejoin visible only with `reservation_queue` plus `queue.rejoin`.
- [x] Rejoin hidden when App Gate metadata denies.
- [x] Rejoin visible only for skipped status.
- [x] Rejoin hidden for waiting/called/seated/cancelled/no-show/terminal/unknown statuses.
- [x] Confirmation required.
- [x] Cancel confirmation sends no request.
- [x] Idempotency key generated correctly.
- [x] Request body is exactly `{}` by default.
- [x] Forbidden fields are absent from body.
- [x] Success refreshes the list.
- [x] `alreadyRejoined` is terminal success.
- [x] `409 QUEUE_TICKET_STATUS_NOT_SKIPPED` is shown and refreshes the list.
- [x] `403` / App Gate denial is shown and does not mutate UI as success.
- [x] Duplicate click while loading sends no duplicate request.
- [x] No Queue Rejoin UI outside `QueueTicketListPage.vue`.
- [x] No unrelated Queue Display, Workbench, Seating, Table, Calendar, No-show, Cancellation, Cleaning, or Turnover UI.

## 16. Boundary Checklist

- [x] Queue Rejoin UI implemented: No.
- [x] Queue Rejoin frontend API client implemented: No.
- [x] Queue Rejoin frontend types implemented: No.
- [x] Queue Rejoin API changed: No.
- [x] Queue Display API/UI implemented: No.
- [x] Queue Workbench mutation implemented: No.
- [x] Queue Call from list implemented: No.
- [x] Queue Seat from list implemented: No.
- [x] Seating implemented: No.
- [x] Table status changed: No.
- [x] Table map implemented: No.
- [x] Reservation Calendar implemented: No.
- [x] No-show API/UI implemented: No.
- [x] Cancellation API/UI implemented: No.
- [x] Cleaning changed: No.
- [x] Turnover API/UI implemented: No.
- [x] Migration changed: No.
- [x] Production database touched: No.
- [x] Seed data inserted: No.
- [x] Existing API paths changed: No.
- [x] GitHub remote added: No.
- [x] GitHub push performed: No.
- [x] Maven Wrapper added: No.

## 17. Final Gate

- [x] Queue Rejoin UI contract is created.
- [x] Queue Rejoin UI checklist is created.
- [x] UI placement is defined.
- [x] Allowed and disallowed statuses are defined.
- [x] App Gate behavior is defined.
- [x] Confirmation behavior is defined.
- [x] Request body `{}` is defined.
- [x] Forbidden fields are defined.
- [x] Loading, success, alreadyRejoined, error, and App Gate denied states are defined.
- [x] Idempotency behavior is defined.
- [x] Refresh behavior is defined.
- [x] Accessibility and mobile UX expectations are defined.
- [x] Future implementation test contract is defined.
- [x] No implementation is included.

Next recommended gate:

```text
Queue Rejoin UI Implementation V1
```
