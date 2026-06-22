# Reservation Arrived Direct Seating UI Checklist V1

Use this checklist before implementing or reviewing a future Reservation Arrived Direct Seating UI.

This checklist is contract-only. It does not authorize Vue, router, API client, backend, migration, SQL, seed, database, or production configuration changes.

## 1. Scope

- [ ] The UI is only for Reservation Arrived Direct Seating.
- [ ] The UI performs `arrived -> seated` only through the existing Direct Seating API.
- [ ] The UI allows manual `reservationId` input.
- [ ] The UI allows manual `tableId` input.
- [ ] The UI allows manual `tableGroupId` input.
- [ ] The UI does not implement Reservation list.
- [ ] The UI does not implement Reservation calendar.
- [ ] The UI does not implement Reservation search.
- [ ] The UI does not implement table map.
- [ ] The UI does not implement drag-and-drop table layout.
- [ ] The UI does not implement Queue.
- [ ] The UI does not implement Auto assignment.
- [ ] The UI does not implement recommended table.
- [ ] The UI does not implement No-show.
- [ ] The UI does not implement Cancellation.
- [ ] The UI does not implement Cleaning changes.
- [ ] The UI does not implement Turnover.

## 2. Route

- [ ] Future route is `/stores/:storeId/reservations/seating/direct`.
- [ ] Future page is `ReservationArrivedDirectSeatingPage.vue`.
- [ ] Route stays under Store Staff workflow.
- [ ] Route does not use `/stores/:storeId/queue`.
- [ ] Route does not use `/stores/:storeId/reservations/calendar`.
- [ ] Route does not use `/stores/:storeId/table-map`.
- [ ] Route does not use `/stores/:storeId/seating/auto`.

## 3. Staff Home Entry

- [ ] Staff Home entry label is `Seat Arrived Reservation`.
- [ ] Entry points to `/stores/:storeId/reservations/seating/direct`.
- [ ] Entry is shown only when `reservation_queue` app is visible and enabled.
- [ ] Entry is shown only when `permissions` contains `reservation.seat`.
- [ ] Entry is hidden when `permissions` does not contain `reservation.seat`.
- [ ] Entry is hidden when `/api/me/apps` does not return `reservation_queue`.
- [ ] Staff Home still does not add Queue entry.
- [ ] Staff Home still does not add Reservation list entry.
- [ ] Staff Home still does not add Reservation calendar entry.
- [ ] Staff Home still does not add table-map entry.
- [ ] Staff Home still does not add auto-assignment entry.
- [ ] Staff Home still does not add No-show entry.
- [ ] Staff Home still does not add Cancellation entry.
- [ ] Staff Home still does not add Cleaning entry.
- [ ] Staff Home still does not add Turnover entry.

## 4. Permission

- [ ] UI uses `reservation.seat` as the Direct Seating visibility permission.
- [ ] UI uses `reservation_queue` as the app key.
- [ ] UI does not create a new app key.
- [ ] UI does not invent `reservation.seating`, `reservation.direct_seat`, `reservation.arrived_seat`, or `seating.create`.
- [ ] UI permission checks are treated as display-only.
- [ ] Backend Direct Seating API plus App Gate remains final authorization.
- [ ] App Gate denial from backend is displayed as an API error.

## 5. API Call

- [ ] UI calls `POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct`.
- [ ] `storeId` comes from route path.
- [ ] `reservationId` comes from the required form field and is used in the API path.
- [ ] `reservationId` is not sent in the request body.
- [ ] `Idempotency-Key` is generated automatically.
- [ ] `Idempotency-Key` is sent in the request header.
- [ ] Request accepts JSON response.
- [ ] Request body contains only allowed fields.

## 6. Form

Required:

- [ ] `reservationId`

Resource selection:

- [ ] `tableId`
- [ ] `tableGroupId`
- [ ] Exactly one of `tableId` and `tableGroupId` is required.
- [ ] Both `tableId` and `tableGroupId` blank is blocked before submit.
- [ ] Both `tableId` and `tableGroupId` filled is blocked before submit.

Optional:

- [ ] `overrideReasonCode`
- [ ] `overrideNote`
- [ ] `note`

System-generated:

- [ ] `Idempotency-Key`

Forbidden:

- [ ] UI does not send `tenantId`.
- [ ] UI does not send `storeId` in body.
- [ ] UI does not send `reservationId` in body.
- [ ] UI does not send `queueTicketId`.
- [ ] UI does not send `walkInId`.
- [ ] UI does not send `checkInAt`.
- [ ] UI does not send `noShowAt`.
- [ ] UI does not send `cancelledAt`.
- [ ] UI does not send `cleaningId`.
- [ ] UI does not send `turnoverId`.
- [ ] UI does not send `status`.

## 7. Success Display

- [ ] Displays `reservationId`.
- [ ] Displays `reservationCode`.
- [ ] Displays `reservationStatus`.
- [ ] Highlights `reservationStatus=seated`.
- [ ] Displays `seatingId`.
- [ ] Displays `seatingStatus`.
- [ ] Displays `resourceType`.
- [ ] Displays `resourceId`.
- [ ] Displays `alreadySeated`.
- [ ] Displays `events`.
- [ ] Displays `idempotency`.
- [ ] Fresh success displays `alreadySeated=false`.
- [ ] Fresh success displays events containing `reservation.seated`.
- [ ] Fresh success displays events containing `seating.created`.
- [ ] Fresh success displays events containing `table.occupied`.
- [ ] Fresh success displays `idempotency.status=completed`.
- [ ] Completed replay displays `idempotency.replayed=true`.

## 8. Already Seated Display

- [ ] Already-seated response displays `alreadySeated=true`.
- [ ] Already-seated response displays `reservationStatus=seated`.
- [ ] Already-seated response displays `reservationCode`.
- [ ] Already-seated response displays `seatingId`.
- [ ] Already-seated response displays `resourceType`.
- [ ] Already-seated response displays `resourceId`.
- [ ] Already-seated response displays `idempotency.status=completed`.
- [ ] Already-seated response allows empty `events`.
- [ ] Already-seated response is treated as success-like, not as failure.
- [ ] `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING` is displayed as an error response if returned.

## 9. Error Display

- [ ] Displays `error.code`.
- [ ] Displays `error.messageKey`.
- [ ] Does not replace `messageKey` with hardcoded business copy.
- [ ] Displays the actual backend `error.code` when present.
- [ ] Handles `MISSING_IDEMPOTENCY_KEY`.
- [ ] Handles `RESERVATION_NOT_FOUND`.
- [ ] Handles `RESERVATION_STATUS_NOT_ARRIVED`.
- [ ] Handles compatibility alias `RESERVATION_INVALID_STATUS` if a local helper emits it.
- [ ] Handles success-like alias `RESERVATION_ALREADY_SEATED` as `alreadySeated=true` when response is successful.
- [ ] Handles consistency error `RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING`.
- [ ] Handles `TABLE_NOT_FOUND`.
- [ ] Handles `TABLE_NOT_AVAILABLE`.
- [ ] Handles `TABLE_CAPACITY_INSUFFICIENT`.
- [ ] Handles `TABLE_LOCK_CONFLICT`.
- [ ] Handles compatibility alias `TABLE_LOCKED` if a local helper emits it.
- [ ] Handles `TABLE_GROUP_NOT_FOUND`.
- [ ] Handles `TABLE_GROUP_INVALID`.
- [ ] Handles `TABLE_GROUP_MEMBER_UNAVAILABLE`.
- [ ] Handles `TABLE_GROUP_CAPACITY_INSUFFICIENT`.
- [ ] Handles `RESOURCE_SELECTION_REQUIRED`.
- [ ] Handles `RESOURCE_SELECTION_CONFLICT`.
- [ ] Handles compatibility alias `RESOURCE_SELECTION_INVALID` if a local helper emits it.
- [ ] Handles `IDEMPOTENCY_CONFLICT`.
- [ ] Handles `IDEMPOTENCY_IN_PROGRESS`.
- [ ] Handles `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- [ ] Handles App Gate denial codes such as `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, and `PERMISSION_DENIED`.
- [ ] Handles `APP_GATE_DENIED` if a fixture or backend response emits it.
- [ ] Handles `FORBIDDEN`.
- [ ] Handles `STORE_SCOPE_MISMATCH`.
- [ ] Handles `UNKNOWN_ERROR` or unknown fallback.

## 10. Mobile-first

- [ ] Single-column layout.
- [ ] `reservationId` input is visually prominent.
- [ ] `tableId` and `tableGroupId` are clearly presented as exactly-one resource selection.
- [ ] Optional fields are visually secondary.
- [ ] Submit button is large enough for mobile use.
- [ ] Submit button has loading state.
- [ ] Submit button has disabled state.
- [ ] Duplicate taps are prevented while loading.
- [ ] Success result highlights `reservationStatus=seated`.
- [ ] Already-seated success-like result is clear on mobile.
- [ ] Error result is clear on mobile.
- [ ] Text wraps inside cards, buttons, and result panels.
- [ ] No complex back-office layout.
- [ ] No calendar UI.
- [ ] No list UI.
- [ ] No table map.
- [ ] No drag-and-drop UI.

## 11. Future Tests

- [ ] Route opens at `/stores/:storeId/reservations/seating/direct`.
- [ ] Staff Home shows the entry when `permissions` contains `reservation.seat`.
- [ ] Staff Home hides the entry when `permissions` does not contain `reservation.seat`.
- [ ] UI permission logic is display-only.
- [ ] Backend App Gate denial is displayed from backend response.
- [ ] `reservationId` is required.
- [ ] Exactly one of `tableId` and `tableGroupId` is required.
- [ ] Both resource fields blank is blocked.
- [ ] Both resource fields filled is blocked.
- [ ] `Idempotency-Key` is generated.
- [ ] `Idempotency-Key` is sent in the request header.
- [ ] Request body excludes forbidden fields.
- [ ] Arrived reservation direct seating success displays `reservationStatus=seated`.
- [ ] Success displays `reservationCode`.
- [ ] Success displays `seatingId`.
- [ ] Success displays `resourceType`.
- [ ] Success displays `resourceId`.
- [ ] Success displays `alreadySeated=false`.
- [ ] Success displays `idempotency.status=completed`.
- [ ] Already-seated displays `alreadySeated=true`.
- [ ] Already-seated does not display failure UI.
- [ ] Error displays `error.code`.
- [ ] Error displays `error.messageKey`.
- [ ] No Reservation list is created.
- [ ] No Reservation calendar is created.
- [ ] No table map is created.
- [ ] No Queue UI is created.
- [ ] No Auto assignment is created.
- [ ] No No-show UI is created.
- [ ] No Cancellation UI is created.
- [ ] No Cleaning change is created.
- [ ] No Turnover UI is created.

## 12. Backend and Data Boundary

- [ ] Backend API is not changed by the UI implementation unless a separate backend round approves it.
- [ ] Existing Direct Seating endpoint path is unchanged.
- [ ] Existing Reservation Create path is unchanged.
- [ ] Existing Reservation CheckIn path is unchanged.
- [ ] Business state machine is unchanged.
- [ ] Migration is unchanged.
- [ ] SQL files are unchanged.
- [ ] Database structure is unchanged.
- [ ] Production database is not touched.
- [ ] Production seed data is not inserted.
- [ ] QueueTicket is not created by UI.
- [ ] WalkIn is not created by UI.
- [ ] CheckIn is not created by UI.
- [ ] No-show is not created by UI.
- [ ] Cancellation is not created by UI.
- [ ] Cleaning is not created by UI.
- [ ] Turnover is not created by UI.
