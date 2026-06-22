# Reservation Arrived Direct Seating UI Validation Report V1

## 1. Read Documents

This validation is updated after the local runtime transaction fix. It supersedes the earlier blocked result where fresh Direct Seating hit `UnexpectedRollbackException`.

Read and aligned:

- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_CONTRACT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_LOCAL_RUNTIME_TRANSACTION_FIX_REPORT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`

## 2. Validation Environment

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8080`
- PostgreSQL: `127.0.0.1:63822`, database `reservation_platform`
- Runtime directory: `target/local-runtime/20260621-transaction-fix`
- Tenant: `10000000-0000-0000-0000-000000000701`
- Store: `20000000-0000-0000-0000-000000000701`
- Actor: `30000000-0000-0000-0000-000000000701`

Runtime actor permissions:

- `reservation.create`
- `reservation.check_in`
- `reservation.seat`
- `walkin.direct_seating.create`
- `cleaning.complete`

Validation data:

- Reservation: `4ddfbd92-a33a-41ca-af76-211e10590fe1`
- Reservation code: `R-20300621-0710`
- Status before Direct Seating: `arrived`
- Success table: `22000000-0000-0000-0000-000000000701` (`T-701`)
- Boundary/error table: `22000000-0000-0000-0000-000000000702` (`T-702`)

## 3. Route Validation

Passed.

- Route opened: `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct`
- Page heading: `Seat Arrived Reservation`
- Visible fields: `reservationId`, `tableId`, `tableGroupId`, `overrideReasonCode`, `overrideNote`, `note`
- Resource rule remains exactly one of `tableId` or `tableGroupId`

## 4. Staff Home Validation

Passed.

- Staff Home route: `/stores/20000000-0000-0000-0000-000000000701/staff`
- Entry visible: `Seat Arrived Reservation`
- Entry href: `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct`

## 5. Permission Display Validation

Passed.

- `/api/me/apps?storeId=20000000-0000-0000-0000-000000000701` returned `reservation_queue.permissions` containing `reservation.seat`.
- Staff Home still gates the entry through `canSeatArrivedReservation`.
- No App Gate bypass was added.

## 6. Form Validation

Passed.

Observed browser states:

- Empty form: submit disabled.
- `reservationId` only: submit disabled.
- `reservationId + tableId`: submit enabled.
- `reservationId + tableId + tableGroupId`: submit disabled.
- `reservationId + tableId + blank tableGroupId`: submit enabled.

## 7. API Client Validation

Passed by source and build.

`src/api/reservationArrivedDirectSeatingApi.ts` still calls:

- `POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct`
- `Accept: application/json`
- `Content-Type: application/json`
- `Idempotency-Key`

Serialized body remains limited to:

- `tableId`
- `tableGroupId`
- `overrideReasonCode`
- `overrideNote`
- `note`

## 8. Success Submit Result

Passed after the transaction fix.

Submitted through the browser:

- reservationId: `4ddfbd92-a33a-41ca-af76-211e10590fe1`
- tableId: `22000000-0000-0000-0000-000000000701`

Observed UI result:

- Heading: `Reservation seated`
- `reservationStatus`: `seated`
- `reservationCode`: `R-20300621-0710`
- `seatingId`: `3179c3fb-0f3d-4ba5-9302-052110359138`
- `resource`: `table 22000000-0000-0000-0000-000000000701`
- `alreadySeated`: `false`
- `seatingStatus`: `occupied`
- `events`: `reservation.seated`, `seating.created`, `table.occupied`
- `idempotency.status`: `completed`
- `idempotency.replayed`: `false`

## 9. TableGroup Result

Not repeated through browser in this transaction-fix follow-up.

Automated Direct Seating tests continue to cover TableGroup success, table_group resource serialization, member table occupancy, invalid group, unavailable member, and capacity errors. The fix did not change TableGroup logic.

## 10. Already Seated Result

Passed.

Second browser submit for the same reservation/table returned:

- `alreadySeated`: `true`
- Same `seatingId`: `3179c3fb-0f3d-4ba5-9302-052110359138`
- `events`: `[]`
- `idempotency.status`: `completed`
- `idempotency.replayed`: `false`

Database confirmed no duplicate seating or seating_resource for the reservation.

## 11. Error Display Result

Passed.

Submitted missing reservation:

- reservationId: `50000000-0000-0000-0000-000000009999`
- tableId: `22000000-0000-0000-0000-000000000702`

Observed UI result:

- Heading: `Error`
- `RESERVATION_NOT_FOUND`
- `reservation.not_found`
- `idempotency.status`: `failed`

The boundary table remained `available`.

## 12. Database Assertions

Runtime database after browser validation:

- Reservation `4ddfbd92-a33a-41ca-af76-211e10590fe1`: `seated`, version `2`
- Table `22000000-0000-0000-0000-000000000701`: `occupied`
- Table `22000000-0000-0000-0000-000000000702`: `available`
- Seatings for reservation: `1`
- Seating resources for reservation seating: `1`
- Fresh idempotency key `reservation:seat:9b3421b6-6a5a-402c-95c4-049b4732a944`: `completed`
- Already-seated idempotency key `reservation:seat:aa6db080-64b7-4589-b024-e9b27ea9487a`: `completed`
- Missing reservation idempotency key `reservation:seat:3f4292da-6961-44ab-bd73-e5381bd17e6b`: `failed`
- `reservation.seated` event exists for the reservation
- `seating.created` event exists for the seating
- `table.occupied` event exists for the table
- `reservation.seat` transition exists with `arrived -> seated`
- `seating.occupy` transition exists with `planned -> occupied`
- `dining_table.occupy` transition exists for the table
- `reservation.seat` audit exists for the seated reservation
- `reservation.seat.failed` audit exists for the missing reservation
- `queue_tickets`: `0`
- `cleanings`: `1` existing fixture row, no Direct Seating side effect
- `turnovers`: `0`
- `reservation_preassignments`: `0`
- `reservation_seatings`: table does not exist
- Seating-related tables remain `seatings` and `seating_resources`

## 13. Commands Executed

Runtime and DB inspection:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/me/apps?storeId=20000000-0000-0000-0000-000000000701
Invoke-WebRequest http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct
psql -h 127.0.0.1 -p 63822 -U postgres -d reservation_platform -Atc "<validation SQL>"
```

Browser validation:

```text
Opened Reservation Arrived Direct Seating route
Filled reservationId + tableId
Submitted fresh seating
Submitted same reservation/table again for alreadySeated
Submitted missing reservation for structured error display
```

Verification:

```powershell
mvn -q "-Dtest=ReservationArrivedDirectSeatingLocalRuntimeTransactionTest" test
mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test
mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,ReservationCreateApiIntegrationTest,WalkInDirectSeatingControllerTest" test
mvn test
npm run build
```

## 14. Build/Test Result

Passed:

- `mvn -q "-Dtest=ReservationArrivedDirectSeatingLocalRuntimeTransactionTest" test`
- `mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test`
- `mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,ReservationCreateApiIntegrationTest,WalkInDirectSeatingControllerTest" test`
- `mvn test`: 340 tests, 0 failures, 0 errors, 0 skipped
- `npm run build`

## 15. Files Changed

Changed in the transaction-fix and validation follow-up:

- `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingLocalRuntimeTransactionTest.java`
- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_LOCAL_RUNTIME_TRANSACTION_FIX_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`

## 16. Boundary Check

Confirmed:

- No Reservation list/search/calendar was implemented.
- No table map or auto assignment was implemented.
- No Queue, No-show, Cancellation, Cleaning, or Turnover behavior was changed.
- No migrations were changed.
- No production config or production data was touched.
- No API path was changed.
- No App Gate annotation, app key, or permission key was changed.
- No new seating table was introduced.

## 17. Open Questions

- TableGroup browser validation was not repeated in this follow-up; it remains covered by automated Direct Seating tests.

## 18. Next Step Recommendation

Continue with the next Reservation Arrived Direct Seating increment. Optional next validation can add a short browser TableGroup smoke pass before starting new product behavior.
