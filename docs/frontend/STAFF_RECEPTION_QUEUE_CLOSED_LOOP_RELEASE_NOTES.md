# Release Notes

## Version / Date

Staff Reception Queue Closed Loop / 2026-06-24

## New

- Added the staff "现场取号" entry under Reception, making the staff home reception area a three-action layout.
- Added a walk-in queue page that creates a WalkIn-backed QueueTicket in the same queue list as reservation-backed QueueTickets.
- Added `POST /api/v1/stores/{storeId}/walk-ins/queue` for walk-in take-a-number.
- Added `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/cancel` for one-click queue cancellation.
- Added Queue List actions for one-click call, skip, cancel, rejoin, and route-to-seat from the ticket list.
- Added API contracts:
  - `docs/api/WALKIN_QUEUE_API_CONTRACT.md`
  - `docs/api/QUEUE_CANCEL_API_CONTRACT.md`

## Changed

- Staff home queue call and queue seat entries now route through the Queue List instead of old copy-ID style pages.
- Queue skip, rejoin, and seating-from-called-queue support WalkIn-backed QueueTickets where `reservationId` is null.
- Seating from a called walk-in queue ticket no longer requires a Reservation and does not emit reservation seated events for walk-ins.
- The legacy `ReservationCreatePage.vue` route target was removed; the old create path now redirects into the current reservation today workflow.

## Fixed

- Removed the old queue-entry interaction that depended on copying IDs before call or seating actions.
- Avoided the stale dedicated reservation create page from interfering with the current staff workbench path.

## Migration

- No database migration is required.
- Existing `walk_ins`, `queue_tickets`, audit, state transition, business event, idempotency, and customer persistence surfaces are reused.

## Permission

- Added `walkin.queue.create` under the `reservation_queue` App Gate entry permission set.
- Added `queue.cancel` under the `reservation_queue` App Gate entry permission set.
- Local runtime allowlist was extended only for:
  - `POST /api/v1/stores/{storeId}/walk-ins/queue`
  - `POST /api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/cancel`

## Risk

- QueueTicket responses now intentionally allow walk-in tickets without a Reservation summary. UI and application services must treat `reservationId`, `reservationCode`, and `reservationStatus` as nullable for these paths.
- The table state-machine regression that allowed `occupied -> available` globally has been corrected; table cleanup lifecycle stays `occupied -> cleaning -> available`.
- The worktree contains unrelated reservation, table resource, temporary table group, and status-action changes that are outside this release note scope.

## Rollback Notes

- To roll back the walk-in take-a-number feature, remove the `walk-in-queue` route, `WalkInQueuePage.vue`, `walkInQueueApi.ts`, `walkInQueue.ts`, and the walk-in queue backend controller/service/DTO classes.
- To roll back queue cancellation, remove the Queue List cancel action, `queueCancelApi.ts`, `queueCancel.ts`, and the queue cancel backend controller/service/DTO classes.
- Remove `walkin.queue.create` and `queue.cancel` from `AppGateRequiredPermission` only if the corresponding endpoints and UI actions are also rolled back.
- Restore the deleted legacy reservation create page only if the old route behavior is intentionally reintroduced.
