# Reservation Arrived To Queue UI Checklist V1

Use this checklist before implementing or reviewing a future Reservation Arrived To Queue UI.

This checklist is contract-only. It does not authorize Vue, router, API client, backend, App Gate metadata, migration, SQL, seed, database, production configuration, or production data changes.

## 1. Scope

- [ ] The UI is only for Reservation Arrived To Queue.
- [ ] The UI sends an already-arrived Reservation to Queue only through the existing Queue API.
- [ ] The UI keeps Reservation status as `arrived`.
- [ ] The UI displays QueueTicket status as `waiting`.
- [ ] The UI allows manual `reservationId` input.
- [ ] The UI allows optional `partySizeGroup`.
- [ ] The UI allows optional `reasonCode`.
- [ ] The UI allows optional `note`.
- [ ] The UI does not implement Queue list.
- [ ] The UI does not implement Queue call.
- [ ] The UI does not implement Queue skip.
- [ ] The UI does not implement Queue rejoin.
- [ ] The UI does not implement Queue display screen.
- [ ] The UI does not implement Seating from queue.
- [ ] The UI does not implement table map.
- [ ] The UI does not implement drag-and-drop table layout.
- [ ] The UI does not implement auto assignment.
- [ ] The UI does not implement No-show.
- [ ] The UI does not implement Cancellation.
- [ ] The UI does not implement Reservation edit or delete.

## 2. Route

- [ ] Future route is `/stores/:storeId/reservations/queue`.
- [ ] Future page is `ReservationArrivedToQueuePage.vue`.
- [ ] Route stays under Store Staff workflow.
- [ ] Route supports optional query prefill `?reservationId=...`.
- [ ] Route does not use `/stores/:storeId/queue`.
- [ ] Route does not use `/stores/:storeId/queue/call`.
- [ ] Route does not use `/stores/:storeId/queue/display`.
- [ ] Route does not use `/stores/:storeId/seating/from-queue`.

## 3. Staff Home Entry

- [ ] Staff Home entry label is `预约排队`.
- [ ] Entry points to `/stores/:storeId/reservations/queue`.
- [ ] Entry is shown only when `reservation_queue` app is visible and enabled.
- [ ] Entry is shown only when `permissions` contains `reservation.queue`.
- [ ] Entry is hidden when `permissions` does not contain `reservation.queue`.
- [ ] Entry is hidden when `/api/me/apps` does not return `reservation_queue`.
- [ ] Staff Home still does not add Queue list entry.
- [ ] Staff Home still does not add Queue call entry.
- [ ] Staff Home still does not add Queue skip entry.
- [ ] Staff Home still does not add Queue rejoin entry.
- [ ] Staff Home still does not add display-screen entry.
- [ ] Staff Home still does not add Seating from queue entry.
- [ ] Staff Home still does not add table-map entry.
- [ ] Staff Home still does not add No-show entry.
- [ ] Staff Home still does not add Cancellation entry.

## 4. Today View Entry

- [ ] Today View `arrived` card shows `进入排队`.
- [ ] Today View `arrived` card still may show `预约入座`.
- [ ] Today View `confirmed` card does not show `进入排队`.
- [ ] Today View `seated` card does not show `进入排队`.
- [ ] Today View terminal status cards do not show `进入排队`.
- [ ] `进入排队` navigates to `/stores/:storeId/reservations/queue?reservationId=...`.
- [ ] Navigation preserves the current `storeId`.
- [ ] Today View does not submit the Queue API directly.
- [ ] Today View does not create QueueTicket.
- [ ] Today View remains read-only except navigation and copy behavior.

## 5. Permission

- [ ] UI uses `reservation.queue` as the Reservation Queue visibility permission.
- [ ] UI uses `reservation_queue` as the app key.
- [ ] UI does not create a new app key.
- [ ] UI does not invent `queue.create`, `reservation.waitlist`, or `reservation.arrived.queue`.
- [ ] UI permission checks are treated as display-only.
- [ ] Backend Reservation Arrived To Queue API plus App Gate remains final authorization.
- [ ] App Gate denial from backend is displayed as an API error.

## 6. API Call

- [ ] UI calls `POST /api/v1/stores/{storeId}/reservations/{reservationId}/queue`.
- [ ] `storeId` comes from route path.
- [ ] `reservationId` comes from the required form field and is used in the API path.
- [ ] `storeId` is not sent in the request body.
- [ ] `reservationId` is not sent in the request body.
- [ ] `Idempotency-Key` is generated automatically.
- [ ] `Idempotency-Key` is sent in the request header.
- [ ] Request accepts JSON response.
- [ ] Request body contains only allowed optional fields.

## 7. Form

Required:

- [ ] `reservationId`

Optional:

- [ ] `partySizeGroup`
- [ ] `reasonCode`
- [ ] `note`

System-generated:

- [ ] `Idempotency-Key`

partySizeGroup:

- [ ] Supports `自动推导`.
- [ ] Supports `1-2`.
- [ ] Supports `3-4`.
- [ ] Supports `5-6`.
- [ ] Supports `7+`.
- [ ] `自动推导` omits `partySizeGroup` from request body.

Forbidden:

- [ ] UI does not send `tenantId`.
- [ ] UI does not send `storeId` in body.
- [ ] UI does not send `reservationId` in body.
- [ ] UI does not send `actorId`.
- [ ] UI does not send `actorType`.
- [ ] UI does not send `tableId`.
- [ ] UI does not send `tableGroupId`.
- [ ] UI does not send `seatingId`.
- [ ] UI does not send `walkInId`.
- [ ] UI does not send `cleaningId`.
- [ ] UI does not send `turnoverId`.
- [ ] UI does not send `noShowAt`.
- [ ] UI does not send `cancelledAt`.
- [ ] UI does not send `queueTicketId`.
- [ ] UI does not send `queueTicketNumber`.
- [ ] UI does not send `status`.

## 8. Success Display

- [ ] Displays `reservationId`.
- [ ] Displays `reservationCode`.
- [ ] Displays `reservationStatus`.
- [ ] Highlights `reservationStatus=arrived`.
- [ ] Displays `queueTicketId`.
- [ ] Displays `queueTicketNumber`.
- [ ] Highlights `queueTicketNumber`.
- [ ] Displays `queueTicketStatus`.
- [ ] Highlights `queueTicketStatus=waiting`.
- [ ] Displays `queueGroupId`.
- [ ] Displays `queueGroupCode`.
- [ ] Displays `partySize`.
- [ ] Displays `partySizeGroup`.
- [ ] Displays `businessDate`.
- [ ] Displays `queuePosition`.
- [ ] Displays `alreadyQueued`.
- [ ] Displays `events`.
- [ ] Displays `idempotency`.
- [ ] Fresh success displays `alreadyQueued=false`.
- [ ] Fresh success displays events containing `reservation.queued`.
- [ ] Fresh success displays events containing `queue_ticket.created`.
- [ ] Fresh success displays `idempotency.status=completed`.
- [ ] Completed replay displays `idempotency.replayed=true`.

## 9. AlreadyQueued Display

- [ ] Already-queued response displays `alreadyQueued=true`.
- [ ] Already-queued response displays `reservationStatus=arrived`.
- [ ] Already-queued response displays `queueTicketStatus=waiting`.
- [ ] Already-queued response displays existing `queueTicketNumber`.
- [ ] Already-queued response displays `reservationCode`.
- [ ] Already-queued response displays `partySizeGroup`.
- [ ] Already-queued response displays `idempotency.status=completed`.
- [ ] Already-queued response allows empty `events`.
- [ ] Already-queued response is treated as success-like, not as failure.
- [ ] UI does not generate or display a new local queue number for already-queued response.

## 10. Error Display

- [ ] Displays `error.code`.
- [ ] Displays `error.messageKey`.
- [ ] Does not replace `messageKey` with hardcoded business copy.
- [ ] Displays the actual backend `error.code` when present.
- [ ] Handles `MISSING_IDEMPOTENCY_KEY`.
- [ ] Handles `INVALID_COMMAND`.
- [ ] Handles `RESERVATION_NOT_FOUND`.
- [ ] Handles `RESERVATION_STATUS_NOT_ARRIVED`.
- [ ] Handles compatibility alias `RESERVATION_INVALID_STATUS` if a local helper emits it.
- [ ] Handles success-like `RESERVATION_ALREADY_QUEUED` as `alreadyQueued=true` when response is successful.
- [ ] Handles `RESERVATION_CANNOT_QUEUE_SEATED`.
- [ ] Handles `RESERVATION_CANNOT_QUEUE_CANCELLED`.
- [ ] Handles `RESERVATION_CANNOT_QUEUE_NO_SHOW`.
- [ ] Handles `RESERVATION_CANNOT_QUEUE_COMPLETED`.
- [ ] Handles `QUEUE_GROUP_NOT_FOUND`.
- [ ] Handles `QUEUE_GROUP_CANNOT_BE_DERIVED`.
- [ ] Handles `QUEUE_GROUP_PARTY_SIZE_MISMATCH`.
- [ ] Handles `QUEUE_TICKET_NUMBER_CONFLICT`.
- [ ] Handles `ACTIVE_QUEUE_TICKET_CONFLICT`.
- [ ] Handles `IDEMPOTENCY_CONFLICT`.
- [ ] Handles `IDEMPOTENCY_IN_PROGRESS`.
- [ ] Handles `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- [ ] Handles App Gate denial codes such as `TENANT_APP_NOT_ENABLED`, `STORE_APP_NOT_ENABLED`, and `PERMISSION_DENIED`.
- [ ] Handles `APP_GATE_DENIED` if a fixture or backend response emits it.
- [ ] Handles `FORBIDDEN`.
- [ ] Handles `STORE_SCOPE_MISMATCH`.
- [ ] Handles `UNKNOWN_ERROR` or unknown fallback.

## 11. Mobile-first

- [ ] Single-column layout.
- [ ] `reservationId` input is visually prominent.
- [ ] `partySizeGroup` is optional and secondary.
- [ ] `reasonCode` is secondary.
- [ ] `note` is secondary.
- [ ] Submit button is large enough for mobile use.
- [ ] Submit button has loading state.
- [ ] Submit button has disabled state.
- [ ] Duplicate taps are prevented while loading.
- [ ] Success result highlights `queueTicketNumber`.
- [ ] Success result highlights `queueTicketStatus=waiting`.
- [ ] Success result shows `reservationStatus=arrived`.
- [ ] Already-queued success-like result is clear on mobile.
- [ ] Error result is clear on mobile.
- [ ] Text wraps inside cards, buttons, and result panels.
- [ ] No complex back-office layout.
- [ ] No Queue list UI.
- [ ] No Queue call UI.
- [ ] No Queue skip UI.
- [ ] No Queue rejoin UI.
- [ ] No display screen.
- [ ] No table map.
- [ ] No drag-and-drop UI.

## 12. Future Tests

- [ ] Route opens at `/stores/:storeId/reservations/queue`.
- [ ] Staff Home shows `预约排队` when `permissions` contains `reservation.queue`.
- [ ] Staff Home hides `预约排队` when `permissions` does not contain `reservation.queue`.
- [ ] UI permission logic is display-only.
- [ ] Backend App Gate denial is displayed from backend response.
- [ ] Today View `arrived` card shows `进入排队`.
- [ ] Today View `confirmed` card does not show `进入排队`.
- [ ] Today View `seated` card does not show `进入排队`.
- [ ] Today View click navigates with `reservationId` query prefill.
- [ ] Today View does not submit Queue API.
- [ ] `reservationId` is required.
- [ ] `partySizeGroup` can be blank or automatic.
- [ ] `Idempotency-Key` is generated.
- [ ] `Idempotency-Key` is sent in the request header.
- [ ] Request body excludes forbidden fields.
- [ ] Arrived reservation queue success displays `reservationStatus=arrived`.
- [ ] Success displays `queueTicketStatus=waiting`.
- [ ] Success displays `queueTicketNumber`.
- [ ] Success displays `reservationCode`.
- [ ] Success displays `partySizeGroup`.
- [ ] Success displays `alreadyQueued=false`.
- [ ] Success displays `idempotency.status=completed`.
- [ ] Already-queued displays `alreadyQueued=true`.
- [ ] Already-queued does not display failure UI.
- [ ] Error displays `error.code`.
- [ ] Error displays `error.messageKey`.
- [ ] App Gate denial is displayed from backend response.
- [ ] No Queue list UI is created.
- [ ] No Queue call UI is created.
- [ ] No Queue skip UI is created.
- [ ] No Queue rejoin UI is created.
- [ ] No Seating from queue is created.
- [ ] No table map is created.
- [ ] No No-show UI is created.
- [ ] No Cancellation UI is created.

## 13. Backend and Data Boundary

- [ ] Backend API is not changed by the UI implementation unless a separate backend round approves it.
- [ ] Existing Reservation Arrived To Queue endpoint path is unchanged.
- [ ] Existing Reservation Create path is unchanged.
- [ ] Existing Reservation CheckIn path is unchanged.
- [ ] Existing Reservation Arrived Direct Seating path is unchanged.
- [ ] Business state machine is unchanged.
- [ ] Reservation status remains `arrived` after queue success.
- [ ] QueueTicket status is `waiting` after queue success.
- [ ] Migration is unchanged.
- [ ] SQL files are unchanged.
- [ ] Database structure is unchanged.
- [ ] Production database is not touched.
- [ ] Production seed data is not inserted.
- [ ] UI does not create Queue Call.
- [ ] UI does not create Queue Skip.
- [ ] UI does not create Queue Rejoin.
- [ ] UI does not create Seating from queue.
- [ ] UI does not mutate DiningTable.
- [ ] UI does not create TableLock.
- [ ] UI does not create No-show.
- [ ] UI does not create Cancellation.
- [ ] UI does not create Cleaning.
- [ ] UI does not create Turnover.
