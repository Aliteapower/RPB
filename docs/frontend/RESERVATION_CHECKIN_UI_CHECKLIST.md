# Reservation CheckIn UI Checklist V1

Use this checklist before implementing or reviewing a future Reservation CheckIn UI.

This checklist is contract-only. It does not authorize Vue, router, API client, backend, migration, SQL, seed, or production database changes.

## 1. Scope

- [ ] The UI is only for Reservation CheckIn.
- [ ] The UI performs `confirmed -> arrived` only through the existing CheckIn API.
- [ ] The UI does not implement Reservation list.
- [ ] The UI does not implement Reservation calendar.
- [ ] The UI does not implement Reservation search.
- [ ] The UI does not implement Queue.
- [ ] The UI does not implement Seating.
- [ ] The UI does not implement Table assignment.
- [ ] The UI does not implement No-show.
- [ ] The UI does not implement Cancellation.
- [ ] The UI does not introduce `CheckInEntity` or `check_ins` concepts.

## 2. Route

- [ ] Future route is `/stores/:storeId/reservations/check-in`.
- [ ] Future page is `ReservationCheckInPage.vue`.
- [ ] Route stays under Store Staff workflow.
- [ ] Route does not use `/stores/:storeId/check-ins`.
- [ ] Route does not use `/stores/:storeId/queue`.
- [ ] Route does not use `/stores/:storeId/reservations/calendar`.

## 3. Staff Home Entry

- [ ] Staff Home entry label is `Check In Reservation` or equivalent approved UI text.
- [ ] Entry points to `/stores/:storeId/reservations/check-in`.
- [ ] Entry is shown only when `reservation_queue` app is visible and enabled.
- [ ] Entry is shown only when `permissions` contains `reservation.check_in`.
- [ ] Entry is hidden when `permissions` does not contain `reservation.check_in`.
- [ ] Entry is hidden when `/api/me/apps` does not return `reservation_queue`.
- [ ] Staff Home still does not add Queue entry.
- [ ] Staff Home still does not add Seating entry.
- [ ] Staff Home still does not add No-show entry.
- [ ] Staff Home still does not add Cancellation entry.
- [ ] Staff Home still does not add Reservation list/calendar entry.

## 4. Permission

- [ ] UI uses `reservation.check_in` as the CheckIn visibility permission.
- [ ] UI uses `reservation_queue` as the app key.
- [ ] UI does not create a new app key.
- [ ] UI does not invent `reservation.arrive`, `reservation.arrival`, `reservation.checkin`, or `checkin.create`.
- [ ] UI permission checks are treated as display-only.
- [ ] Backend CheckIn API plus App Gate remains final authorization.
- [ ] App Gate denial from backend is displayed as an API error.

## 5. API Call

- [ ] UI calls `POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in`.
- [ ] `storeId` comes from route path.
- [ ] `reservationId` comes from the required form field and is used in the API path.
- [ ] `reservationId` is not sent in the request body.
- [ ] `Idempotency-Key` is generated automatically.
- [ ] `Idempotency-Key` is sent in the request header.
- [ ] Request accepts JSON response.
- [ ] Request body contains only allowed optional fields.

## 6. Form

Required:

- [ ] `reservationId`

Optional:

- [ ] `arrivedAt`
- [ ] `reasonCode`
- [ ] `note`

System-generated:

- [ ] `Idempotency-Key`

Forbidden:

- [ ] UI does not send `tenantId`.
- [ ] UI does not send `storeId` in body.
- [ ] UI does not send `reservationId` in body.
- [ ] UI does not send `actorId`.
- [ ] UI does not send `actorType`.
- [ ] UI does not send `queueTicketId`.
- [ ] UI does not send `seatingId`.
- [ ] UI does not send `tableId`.
- [ ] UI does not send `tableGroupId`.
- [ ] UI does not send `noShowAt`.
- [ ] UI does not send `cancelledAt`.
- [ ] UI does not send `status`.

## 7. Success Display

- [ ] Displays `reservationId`.
- [ ] Displays `reservationCode`.
- [ ] Displays `status`.
- [ ] Highlights `status=arrived`.
- [ ] Displays `arrivedAt`.
- [ ] Displays `alreadyArrived`.
- [ ] Displays `events`.
- [ ] Displays `idempotency.status`.
- [ ] Displays `idempotency.replayed`.
- [ ] Fresh success displays `alreadyArrived=false`.
- [ ] Fresh success displays `events` containing `reservation.arrived`.
- [ ] Already-arrived response displays `alreadyArrived=true`.
- [ ] Already-arrived response is treated as success-like, not as failure.

## 8. Error Display

- [ ] Displays `error.code`.
- [ ] Displays `error.messageKey`.
- [ ] Does not replace `messageKey` with hardcoded business copy.
- [ ] Handles `MISSING_IDEMPOTENCY_KEY`.
- [ ] Handles `RESERVATION_NOT_FOUND`.
- [ ] Handles `RESERVATION_STATUS_NOT_CONFIRMED`.
- [ ] Handles compatibility alias `RESERVATION_INVALID_STATUS` if a local helper emits it.
- [ ] Handles `IDEMPOTENCY_CONFLICT`.
- [ ] Handles `IDEMPOTENCY_IN_PROGRESS`.
- [ ] Handles App Gate denial codes.
- [ ] Handles `FORBIDDEN`.
- [ ] Handles `STORE_SCOPE_MISMATCH`.
- [ ] Handles `UNKNOWN_ERROR` or unknown fallback.

## 9. Mobile-first

- [ ] Single-column layout.
- [ ] `reservationId` input is visually prominent.
- [ ] `arrivedAt` is optional and can be left blank.
- [ ] `reasonCode` is secondary.
- [ ] `note` is secondary.
- [ ] Submit button is large enough for mobile use.
- [ ] Submit button has loading/disabled state.
- [ ] Success result highlights `status=arrived`.
- [ ] Error result is clear on mobile.
- [ ] Text wraps inside cards, buttons, and result panels.
- [ ] No complex dashboard layout.
- [ ] No calendar UI.
- [ ] No list UI.
- [ ] No table map.

## 10. Future Tests

- [ ] Route opens at `/stores/:storeId/reservations/check-in`.
- [ ] Staff Home shows the entry when `permissions` contains `reservation.check_in`.
- [ ] Staff Home hides the entry when `permissions` does not contain `reservation.check_in`.
- [ ] `reservationId` is required.
- [ ] `arrivedAt` can be blank.
- [ ] `Idempotency-Key` is generated.
- [ ] Request body excludes forbidden fields.
- [ ] Confirmed reservation check-in success displays `status=arrived`.
- [ ] Success displays `reservationCode`.
- [ ] Success displays `arrivedAt`.
- [ ] Success displays `alreadyArrived=false`.
- [ ] Success displays `idempotency.status=completed`.
- [ ] Already-arrived displays `alreadyArrived=true`.
- [ ] Error displays `error.code`.
- [ ] Error displays `error.messageKey`.
- [ ] App Gate denial is displayed from backend response.
- [ ] No Queue UI is created.
- [ ] No Seating UI is created.
- [ ] No No-show UI is created.
- [ ] No Cancellation UI is created.
- [ ] No Reservation list/calendar is created.

## 11. Backend and Data Boundary

- [ ] Backend API is not changed by the UI implementation unless a separate backend round approves it.
- [ ] Existing CheckIn endpoint path is unchanged.
- [ ] Existing Reservation Create path is unchanged.
- [ ] Business state machine is unchanged.
- [ ] Migration is unchanged.
- [ ] SQL files are unchanged.
- [ ] Production database is not touched.
- [ ] Production seed data is not inserted.
- [ ] QueueTicket is not created by UI.
- [ ] Seating is not created by UI.
- [ ] Table assignment is not created by UI.
