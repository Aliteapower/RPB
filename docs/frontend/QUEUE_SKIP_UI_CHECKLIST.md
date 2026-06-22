# Queue Skip UI Checklist V1

## 1. Contract Scope

- [x] This round only creates Queue Skip UI contract documentation.
- [x] `docs/frontend/QUEUE_SKIP_UI_CONTRACT.md` is created.
- [x] `docs/frontend/QUEUE_SKIP_UI_CHECKLIST.md` is created to match the existing frontend UI checklist convention.
- [x] No Vue page is created or modified.
- [x] No frontend API client is created.
- [x] No frontend type file is created.
- [x] No Vue Router entry is created or modified.
- [x] No Staff Home source change is made.
- [x] No backend API change is made.
- [x] No App Gate metadata change is made.
- [x] No migration, SQL, database structure, production config, seed data, or production data change is made.

## 2. Baseline Reads

- [x] Queue Skip API contract considered.
- [x] Queue Skip API implementation report considered.
- [x] Queue List UI contract considered.
- [x] Queue List UI checklist considered.
- [x] Queue Call API implementation report considered.
- [x] Seating From Called Queue API implementation report considered.
- [x] Architecture governance considered.
- [x] Business glossary considered.
- [x] Business rules considered.
- [x] Data standard considered.
- [x] Existing Queue Ticket List page pattern considered.
- [x] Existing Queue List API client and type pattern considered.
- [x] Existing Queue Call API client and type pattern considered.
- [x] Existing Seating From Called Queue API client and type pattern considered.
- [x] Existing Staff Home `/api/me/apps` permission pattern considered.
- [x] Existing Pinia store context pattern considered.
- [x] Existing Vue Router pattern considered.

## 3. UI Placement

- [x] Future Skip action is scoped to `QueueTicketListPage.vue`.
- [x] Future Skip action belongs on eligible QueueTicket cards.
- [x] Future Skip action is secondary to queue number, status, and hold time.
- [x] No standalone Queue Skip page is designed.
- [x] No Staff Home `过号处理` entry is designed.
- [x] No Queue Workbench is designed.
- [x] No Queue Display is designed.
- [x] No Table map is designed.

## 4. Status Eligibility

- [x] `called` is the only allowed status for active Skip.
- [x] `waiting` is not allowed.
- [x] `seated` is not allowed.
- [x] `skipped` is not allowed.
- [x] `rejoined` is not allowed.
- [x] `expired` is not allowed.
- [x] `cancelled` is not allowed.
- [x] `no_show` is not allowed.
- [x] `completed` is not allowed.
- [x] `closed` is not allowed.
- [x] Unknown future statuses default to no active Skip action.
- [x] Stale state maps to API error `QUEUE_TICKET_STATUS_NOT_CALLED`.

## 5. Confirmation

- [x] Skip requires confirmation before mutation.
- [x] Confirmation displays queue ticket number.
- [x] Confirmation displays queue ticket status.
- [x] Confirmation displays reservation code when present.
- [x] Confirmation displays party size and party size group.
- [x] Confirmation displays customer name when present.
- [x] Confirmation displays masked phone when present.
- [x] Confirmation displays `calledAt` when present.
- [x] Confirmation displays `holdUntilAt` when present.
- [x] Cancel sends no request.
- [x] No idempotency key is generated until confirm.

## 6. Request Body

- [x] `skippedAt` is optional.
- [x] V1 UI should normally omit `skippedAt` and let backend clock decide.
- [x] `reasonCode` is optional.
- [x] `note` is optional.
- [x] Optional fields are trimmed.
- [x] Blank optional fields are omitted.
- [x] Request body allowlist is only `skippedAt`, `reasonCode`, and `note`.

## 7. Forbidden Fields

- [x] Does not send `tenantId`.
- [x] Does not send `storeId` in body.
- [x] Does not send `actorId`.
- [x] Does not send `actorType`.
- [x] Does not send `reservationId`.
- [x] Does not send `tableId`.
- [x] Does not send `tableGroupId`.
- [x] Does not send `seatingId`.
- [x] Does not send `cleaningId`.
- [x] Does not send `turnoverId`.
- [x] Does not send `rejoinReason`.
- [x] Does not send `noShowAt`.
- [x] Does not send `cancelledAt`.
- [x] Does not send `status`.
- [x] Does not send mutation action in body.

## 8. API Binding

- [x] Future UI calls `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip`.
- [x] `storeId` comes from route path.
- [x] `queueTicketId` comes from QueueTicket card data.
- [x] `Idempotency-Key` header is required.
- [x] `Accept: application/json` is used.
- [x] `Content-Type: application/json` is used when a body is sent.
- [x] Existing API paths are not changed.

## 9. App Gate

- [x] Product shorthand permission is `reservation_queue.queue.skip`.
- [x] Frontend metadata app key is `reservation_queue`.
- [x] Frontend permission value is `queue.skip`.
- [x] Active Skip requires visible `reservation_queue` entry.
- [x] Active Skip requires permissions containing `queue.skip`.
- [x] Tenant app denial means no active Skip control.
- [x] Store app denial means no active Skip control.
- [x] Permission denial means no active Skip control.
- [x] `/api/me/apps` failure means no active Skip control.
- [x] Frontend checks are display hints only.
- [x] Backend App Gate remains final authorization.

## 10. Idempotency

- [x] One key is generated per user-confirmed attempt.
- [x] Key is generated after confirmation.
- [x] Same key is reused only for the same in-flight retry.
- [x] New confirmed attempt uses a new key.
- [x] Failed idempotency key is not reused after failed-key-requires-new-key.
- [x] UI does not auto-loop on in-progress response.
- [x] UI never sends Skip without `Idempotency-Key`.
- [x] `MISSING_IDEMPOTENCY_KEY` is handled.
- [x] `IDEMPOTENCY_CONFLICT` is handled.
- [x] `IDEMPOTENCY_IN_PROGRESS` is handled.
- [x] `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` is handled.

## 11. UI States

- [x] Loading disables the target card Skip action.
- [x] Loading prevents double submit.
- [x] Loading keeps the rest of the list readable.
- [x] Fresh success is terminal success.
- [x] Fresh success refreshes the current Queue List query.
- [x] Fresh success does not navigate away.
- [x] `alreadySkipped = true` is terminal success.
- [x] `alreadySkipped = true` refreshes the current Queue List query.
- [x] `alreadySkipped = true` does not duplicate fresh mutation messaging.
- [x] Error display keeps the affected ticket card visible until refresh.
- [x] Network or invalid response uses a stable fallback code and message key.

## 12. Error Display

- [x] Error display must show `error.code`.
- [x] Error display must show `error.messageKey`.
- [x] UI must not replace backend `messageKey` with hardcoded business copy.
- [x] `QUEUE_TICKET_STATUS_NOT_CALLED` is covered.
- [x] `QUEUE_SKIP_EVIDENCE_INCOMPLETE` is covered.
- [x] `MISSING_IDEMPOTENCY_KEY` is covered.
- [x] `IDEMPOTENCY_CONFLICT` is covered.
- [x] `IDEMPOTENCY_IN_PROGRESS` is covered.
- [x] `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` is covered.
- [x] `TENANT_APP_NOT_ENABLED` is covered.
- [x] `STORE_APP_NOT_ENABLED` is covered.
- [x] `PERMISSION_DENIED` is covered.
- [x] `FORBIDDEN` is covered.
- [x] `STORE_SCOPE_MISMATCH` is covered.
- [x] `PERSISTENCE_ERROR` is covered.
- [x] `EVENT_WRITE_FAILED` is covered.
- [x] `STATE_TRANSITION_WRITE_FAILED` is covered.
- [x] `AUDIT_WRITE_FAILED` is covered.
- [x] `UNKNOWN_ERROR` is covered.

## 13. Refresh Behavior

- [x] Fresh success reloads the current Queue List query.
- [x] `alreadySkipped` reloads the current Queue List query.
- [x] Refresh preserves status filter.
- [x] Refresh preserves `limit`.
- [x] Refresh preserves `offset` unless a later implementation intentionally adjusts empty-page behavior.
- [x] Called filter behavior is documented.
- [x] All filter behavior is documented.
- [x] No optimistic update is required in V1.

## 14. Accessibility and Mobile UX

- [x] Queue List remains single-column and mobile-first.
- [x] Skip target must be thumb-friendly.
- [x] Confirmation modal or sheet traps focus.
- [x] Confirmation has an obvious cancel control.
- [x] Destructive action styling is clear but not dominant.
- [x] Buttons expose accessible names.
- [x] Loading state is announced.
- [x] Error state is announced.
- [x] Long codes and message keys wrap.
- [x] `holdUntilAt` remains prominent for called tickets.
- [x] No complex table layout.
- [x] No drag-and-drop.
- [x] No display-screen layout.

## 15. Future Test Contract

- [x] Skip visible or enabled only with `reservation_queue` plus `queue.skip`.
- [x] Skip hidden or disabled when App Gate metadata denies.
- [x] Skip hidden or disabled for invalid statuses.
- [x] Confirmation required.
- [x] Idempotency key generated correctly.
- [x] Success refreshes the list.
- [x] `alreadySkipped` is terminal success.
- [x] `409 QUEUE_TICKET_STATUS_NOT_CALLED` is shown.
- [x] `403` / App Gate denial is shown.
- [x] Forbidden fields are absent from body.
- [x] No unrelated Queue Rejoin, Display, Workbench, Seating, Table, No-show, Cancellation, Cleaning, or Turnover UI.

## 16. Boundary Checklist

- [x] Queue Skip UI implemented: No.
- [x] Queue Skip frontend API client implemented: No.
- [x] Queue Rejoin API implemented: No.
- [x] Queue Display API implemented: No.
- [x] Queue Workbench mutation implemented: No.
- [x] Queue Call from list implemented: No.
- [x] Queue Seat from list implemented: No.
- [x] Seating implemented: No.
- [x] SeatingResource created: No.
- [x] Table status changed: No.
- [x] Table map implemented: No.
- [x] Auto assignment implemented: No.
- [x] No-show API implemented: No.
- [x] Cancellation API implemented: No.
- [x] Cleaning API changed: No.
- [x] Turnover API implemented: No.
- [x] Migration changed: No.
- [x] Production database touched: No.
- [x] Seed data inserted: No.
- [x] Existing API paths changed: No.

## 17. Final Gate

- [x] Queue Skip UI contract is created.
- [x] Queue Skip UI checklist is created.
- [x] UI placement is defined.
- [x] Allowed and disallowed statuses are defined.
- [x] Confirmation behavior is defined.
- [x] `skippedAt`, `reasonCode`, and `note` handling is defined.
- [x] Loading, success, alreadySkipped, error, and App Gate denied states are defined.
- [x] Idempotency behavior is defined.
- [x] Refresh behavior is defined.
- [x] Accessibility and mobile UX expectations are defined.
- [x] Future implementation test contract is defined.
- [x] No implementation is included.

Next recommended gate:

```text
Queue Skip UI Implementation V1
```
