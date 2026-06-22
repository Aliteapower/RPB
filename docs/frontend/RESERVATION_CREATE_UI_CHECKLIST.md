# Reservation Create UI Checklist V1

## Scope Checklist

- [x] Only Create Reservation UI is designed.
- [x] Reservation CheckIn UI is not designed.
- [x] Reservation Cancel UI is not designed.
- [x] Reservation No-show UI is not designed.
- [x] Reservation Calendar UI is not designed.
- [x] Reservation List/Search UI is not designed.
- [x] Queue UI is not designed.
- [x] Seating UI is not designed.
- [x] Table assignment UI is not designed.
- [x] Table selector is not designed.
- [x] Customer search is not designed.

## Implementation Boundary Checklist

- [x] Vue page created: No.
- [x] Vue component created: No.
- [x] API client created: No.
- [x] Route implemented: No.
- [x] Staff Home source changed: No.
- [x] Backend API changed: No.
- [x] Controller changed: No.
- [x] Migration changed: No.
- [x] SQL file created: No.
- [x] OpenAPI generated: No.

## Route Checklist

- [x] Future route is defined as `/stores/:storeId/reservations/create`.
- [x] Future page name is defined as `ReservationCreatePage.vue`.
- [x] `storeId` comes from route param.
- [x] `tenantId` is not accepted from the form.

## Form Checklist

- [x] `partySize` is required.
- [x] `reservedStartAt` is required.
- [x] `reservedEndAt` is optional.
- [x] `customerId` is optional.
- [x] `customerName` is optional.
- [x] `customerNickname` is optional.
- [x] `phoneE164` is optional.
- [x] `note` is optional.
- [x] `reservationCode` is not accepted from UI input.
- [x] Queue, seating, table, check-in, no-show, and cancellation fields are excluded.

## Validation Checklist

- [x] `partySize > 0` is required.
- [x] `reservedStartAt` must be present.
- [x] `reservedEndAt`, when present, must be after `reservedStartAt`.
- [x] `phoneE164`, when present, must be E.164.
- [x] Submit must generate `Idempotency-Key`.
- [x] Backend remains final validation authority.

## API Contract Checklist

- [x] Endpoint is `POST /api/v1/stores/{storeId}/reservations`.
- [x] Header `Idempotency-Key` is required.
- [x] Request body excludes `tenantId`.
- [x] Request body excludes `reservationCode`.
- [x] Request body excludes table, queue, seating, check-in, no-show, and cancellation fields.
- [x] Success response displays backend-derived `reservedEndAt`.
- [x] Success response displays backend-derived `holdUntilAt`.
- [x] Success response displays `reservationCode`.

## Error / I18n Checklist

- [x] Error UI displays `error.code`.
- [x] Error UI displays `error.messageKey`.
- [x] UI does not hardcode business error display text in place of `messageKey`.
- [x] Capacity insufficient is represented by `RESERVATION_CAPACITY_INSUFFICIENT`.
- [x] Complex capacity policy is not exposed in V1 UI.

## Idempotency Checklist

- [x] Fresh submit generates an idempotency key.
- [x] Completed replay is represented with `idempotency.replayed = true`.
- [x] In-progress behavior is represented.
- [x] Failed key requires a new key.
- [x] Same key different hash conflict is represented.
- [x] Idempotency is not treated as an auth, capacity, or table-lock bypass.

## Mobile-First Checklist

- [x] Single-column layout required.
- [x] `partySize` and `reservedStartAt` are primary.
- [x] Customer fields are collapsible.
- [x] Note is collapsed or visually secondary.
- [x] Submit button is prominent.
- [x] Success result highlights `reservationCode`.
- [x] No large calendar.
- [x] No complex table map.
- [x] No drag-and-drop table layout.
- [x] No dense admin grid.

## Business Boundary Checklist

- [x] Reservation remains separate from QueueTicket.
- [x] Create Reservation does not create Seating.
- [x] Create Reservation does not create TableLock.
- [x] Create Reservation does not create ReservationPreassignment.
- [x] Create Reservation does not implement CheckIn.
- [x] Create Reservation does not implement No-show.
- [x] Create Reservation does not implement Cancellation.
- [x] Reservation locks Store + date/time + party-size capacity, not a specific table by default.
- [x] Phone is optional for Customer.
- [x] No-phone temporary Reservation customer remains supported.
