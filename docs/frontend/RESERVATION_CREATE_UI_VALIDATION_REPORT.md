# Reservation Create UI Validation Report

## 1. Read Documents

- `docs/frontend/RESERVATION_CREATE_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CREATE_UI_CONTRACT.md`
- `docs/frontend/RESERVATION_CREATE_UI_CHECKLIST.md`
- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/RESERVATION_API_ERROR_CONTRACT.md`
- `docs/api/RESERVATION_API_IDEMPOTENCY_CONTRACT.md`
- `docs/frontend/STORE_STAFF_OPERATIONAL_HANDOFF.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

## 2. Validation Environment

- Frontend: Vue 3 / Vite dev server at `http://127.0.0.1:5173`.
- Backend: Spring Boot local profile at `http://127.0.0.1:18082`.
- Database: Local temporary PostgreSQL 17.10 on `127.0.0.1:54345`.
- Auth: Local/test actor placeholder only, with `reservation.create` permission.
- Store: `20000000-0000-0000-0000-000000000701`.
- Tenant: `10000000-0000-0000-0000-000000000701`.

## 3. Route Validation

- Route validated: `/stores/:storeId/reservations/create`.
- Concrete URL: `http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000701/reservations/create`.
- Page rendered `Create Reservation`.
- Staff home link remained scoped to the same store.
- No Reservation list/calendar route was used.

## 4. Form Validation

- `partySize <= 0`: blocked by native `min=1` input validation and Vue validation contract.
- Missing or invalid `reservedStartAt`: mapped to `INVALID_TIME_RANGE`.
- `reservedEndAt <= reservedStartAt`: displayed `INVALID_TIME_RANGE` / `reservation.invalid_time_range`.
- Invalid `phoneE164`: displayed `INVALID_PHONE_E164` / `reservation.invalid_phone_e164`.
- Optional customer fields remain optional.
- No table, table group, queue, seating, check-in, no-show, or cancellation fields are present.

## 5. API Client Validation

- Endpoint: `POST /api/v1/stores/{storeId}/reservations`.
- `storeId` came from the route path.
- `Idempotency-Key` was generated as `reservation:create:<uuid>`.
- Successful UI run used `reservation:create:c6e3eee1-f839-410c-9951-06610a86b91d`.
- Request body is produced by `toApiBody()` and includes only:
  - `partySize`
  - `reservedStartAt`
  - `reservedEndAt`
  - `customerId`
  - `customerName`
  - `customerNickname`
  - `phoneE164`
  - `note`
- Body excludes `tenantId`, `tableId`, `tableGroupId`, `queueTicketId`, `seatingId`, `checkInAt`, `noShowAt`, and reservation cancellation fields.

## 6. Success Submit

- Submitted from the UI with:
  - `partySize = 4`
  - `reservedStartAt = 2027-03-21T19:00` local Singapore time
  - no explicit `reservedEndAt`
  - `customerName = UI Validation Guest`
  - `phoneE164 = +6598765436`
- UI displayed:
  - `reservationCode = R-20270321-7040`
  - `reservationId = 2fe2c61a-6e43-42e0-a258-d6fe8038017c`
  - `status = confirmed`
  - `reservedStartAt = 2027-03-21T11:00:00Z`
  - `reservedEndAt = 2027-03-21T12:30:00Z`
  - `holdUntilAt = 2027-03-21T11:15:00Z`
  - `businessDate = 2027-03-21`
  - `events = reservation.created, reservation.confirmed`
  - `idempotency = completed`

## 7. Error Display

- Missing `Idempotency-Key` backend probe returned `MISSING_IDEMPOTENCY_KEY` / `reservation.missing_idempotency_key`.
- Invalid phone UI validation displayed `INVALID_PHONE_E164` / `reservation.invalid_phone_e164`.
- Invalid time range UI validation displayed `INVALID_TIME_RANGE` / `reservation.invalid_time_range`.
- Error panel displays `error.code` and `error.messageKey`; no translated business copy is hardcoded as replacement.

## 8. Database Assertions

- Reservation: one `confirmed` reservation created with party size `4`.
- UTC time check:
  - `reserved_start_at = 2027-03-21 11:00:00 UTC`
  - `reserved_end_at = 2027-03-21 12:30:00 UTC`
  - `hold_until_at = 2027-03-21 11:15:00 UTC`
- BusinessEvent: `reservation.created` and `reservation.confirmed` created for the reservation.
- StateTransitionLog: one transition `none -> confirmed` with `reservation.confirm`.
- AuditLog: one `reservation.create` audit record.
- IdempotencyRecord: status `completed`, target `reservation`, response snapshot persisted as JSON object.
- QueueTicket: `0`.
- Seating: `0`.
- TableLock: `0`.
- ReservationPreassignment: `0`.

## 9. Runtime Fixes Applied During Validation

- Local auth runtime support: added local/test security permit for `POST /api/v1/stores/*/reservations`.
- PostgreSQL JSONB persistence: added JSONB write casts for String-backed JSONB fields in idempotency, event, state transition, and audit entities.
- Boundary tests were aligned so `ReservationCreatePage.vue` is allowed while CheckIn / Queue / Seating / No-show / Cancellation / Calendar / List remain forbidden.
- These fixes did not add new API endpoints, did not change the migration, and did not change database structure.

## 10. Commands Executed

- `mvn test`: passed, `226 tests, 0 failures, 0 errors`.
- `npm run build`: passed.
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`: running on local port `18082`.
- `npm run dev`: running on local port `5173`.
- PostgreSQL local runtime: running on local port `54345`.

## 11. Boundary Check

- CheckIn UI created: No.
- Queue UI created: No.
- Seating UI created: No.
- No-show UI created: No.
- Cancellation UI created: No.
- Reservation Calendar/List created: No.
- Backend API changed: No.
- Migration changed: No.
- Database structure changed: No.
- Production database touched: No.
- Seed data inserted: No.

## 12. Open Questions

- Whether to add a dedicated PostgreSQL-backed JSONB persistence test for String-backed JSONB fields.
- Whether future runtime setup scripts should include Reservation local-auth permissions by default.

## 13. Next Step Recommendation

- Proceed to Store Staff Reservation Create UI handoff / smoke review.
- Keep Reservation next slice narrow; do not merge CheckIn, Queue fallback, Seating, No-show, or Calendar/List into the create-reservation slice.
