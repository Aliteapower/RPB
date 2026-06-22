# Reservation Arrived Direct Seating Local Runtime Transaction Fix Result

## 1. Root Cause

The visible runtime symptom was `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only` during fresh Reservation Arrived Direct Seating.

The diagnostic root cause was:

- Root exception class: `org.springframework.orm.ObjectOptimisticLockingFailureException`
- Hibernate cause: `org.hibernate.StaleObjectStateException`
- Message: `Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.rpb.reservation.reservation.persistence.entity.ReservationEntity#<reservationId>]`
- Failure point: `ReservationPersistenceAdapter.save`, through `SimpleJpaRepository.save`, while saving the reservation status change from `arrived` to `seated`.

The rollback did not originate from seating insert, seating_resource insert, event write, transition write, audit write, idempotency completion, or response mapping.

The existing reservation row had already advanced its JPA `version` through Create and CheckIn. `ReservationPersistenceAdapter.save` mapped the domain object into a detached entity whose version was stale for the existing row. Hibernate treated the merge as an optimistic-lock conflict, Spring translated it to `ObjectOptimisticLockingFailureException`, and the participating transaction was marked rollback-only. The service then attempted to return a stable application error from inside that same transaction, but commit detected rollback-only and raised `UnexpectedRollbackException`.

## 2. Files Changed

- `src/main/java/com/rpb/reservation/reservation/persistence/adapter/ReservationPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationArrivedDirectSeatingApplicationService.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationArrivedDirectSeatingLocalRuntimeTransactionTest.java`
- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/api/ReservationControllerTest.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_LOCAL_RUNTIME_TRANSACTION_FIX_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`

## 3. Transaction Fix Scope

`ReservationPersistenceAdapter.save` now loads the existing scoped reservation row before updating it. Existing-row saves preserve:

- `createdAt`
- `deletedAt`
- JPA `version`

New reservation saves still use `entityManager.persist` with a null version.

`ReservationArrivedDirectSeatingApplicationService` now preserves caught repository exceptions as causes on internal `ApplicationFailure` instances. This does not change the API contract or response shape; it keeps the original persistence cause available during diagnostics.

The controller/API path, App Gate permission key, UI behavior, Flyway migrations, schema, production config, Queue, No-show, Cancellation, Cleaning, and Turnover behavior were not changed.

## 4. Regression Test

Added `ReservationArrivedDirectSeatingLocalRuntimeTransactionTest`.

Coverage:

- SpringBootTest + MockMvc + local temporary PostgreSQL.
- Create reservation through API.
- CheckIn reservation to `arrived` through API.
- Fresh direct seating through `POST /api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct`.
- Assert HTTP 200, `reservationStatus=seated`, one seating, one seating_resource, table occupied, evidence written, idempotency completed.
- Submit a second Direct Seating request with a new idempotency key and assert `alreadySeated=true`.
- Assert missing reservation returns structured `RESERVATION_NOT_FOUND` without creating seating side effects.
- Assert boundary tables remain unchanged for queue, cleaning, turnover, no-show, and cancellation.

## 5. Fresh Direct Seating Runtime

Browser/runtime validation after the fix passed.

- Route: `/stores/20000000-0000-0000-0000-000000000701/reservations/seating/direct`
- Reservation: `4ddfbd92-a33a-41ca-af76-211e10590fe1`
- Reservation code: `R-20300621-0710`
- Table: `22000000-0000-0000-0000-000000000701`
- Result heading: `Reservation seated`
- `reservationStatus`: `seated`
- `seatingId`: `3179c3fb-0f3d-4ba5-9302-052110359138`
- `resourceType`: `table`
- `alreadySeated`: `false`
- `idempotency.status`: `completed`

The previous local runtime rollback did not recur.

## 6. TableGroup Runtime

TableGroup browser revalidation was not repeated in this transaction-fix round.

Automated coverage remains in:

- `ReservationArrivedDirectSeatingApiIntegrationTest`
- `ReservationArrivedDirectSeatingControllerTest`
- `ReservationArrivedDirectSeatingApplicationServiceTest`
- `mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test`
- `mvn test`

Those tests cover `table_group` resource responses, member-table occupancy, invalid group/member errors, and capacity errors. The production fix is limited to reservation persistence version preservation and does not simplify or bypass TableGroup logic.

## 7. Already Seated

Browser/runtime validation passed.

Second submit for the same reservation/table returned:

- `alreadySeated`: `true`
- Same `seatingId`: `3179c3fb-0f3d-4ba5-9302-052110359138`
- `events`: `[]`
- `idempotency.status`: `completed`

Database evidence after the second submit:

- `seatings` for the reservation: `1`
- `seating_resources` for the reservation seating: `1`
- `reservation.seated` target event for the reservation: `1`
- `seating.created` target event for the seating: `1`
- `reservation.seat` audit for the reservation: `1`

## 8. Error Display

Browser/runtime validation still passed for a missing reservation.

- Missing reservation: `50000000-0000-0000-0000-000000009999`
- UI displayed `RESERVATION_NOT_FOUND`
- UI displayed `reservation.not_found`
- `idempotency.status`: `failed`
- `reservation.seat.failed` audit was written
- No seating or seating_resource was created for the error path

## 9. Database Assertions

Runtime database assertions after the browser validation:

- Reservation: `4ddfbd92-a33a-41ca-af76-211e10590fe1` is `seated`, version `2`
- Seating: one active seating for the reservation
- SeatingResource: one active resource for the reservation seating
- Table: `22000000-0000-0000-0000-000000000701` is `occupied`
- Boundary table: `22000000-0000-0000-0000-000000000702` remains `available`
- BusinessEvent: `reservation.seated` target reservation exists
- BusinessEvent: `seating.created` target seating exists
- BusinessEvent: `table.occupied` target table exists
- StateTransitionLog: `reservation.seat` has `arrived -> seated`
- StateTransitionLog: `seating.occupy` has `planned -> occupied`
- StateTransitionLog: `dining_table.occupy` exists for the table
- AuditLog: `reservation.seat` exists for the seated reservation
- AuditLog: `reservation.seat.failed` exists for the missing reservation
- IdempotencyRecord: fresh and already-seated UI requests are `completed`
- IdempotencyRecord: missing reservation request is `failed`
- AppGateAuditLog: no allowed request denial observed; runtime actor has `reservation.seat`
- QueueTicket: `0`
- Cleaning: `1` existing runtime fixture row, no new cleaning side effect from Direct Seating
- Turnover: `0`
- ReservationPreassignment: `0`
- Extra seating table: `reservation_seatings` does not exist; seating tables remain `seatings` and `seating_resources`

## 10. Commands

- New regression test: `mvn -q "-Dtest=ReservationArrivedDirectSeatingLocalRuntimeTransactionTest" test`
- ReservationArrivedDirectSeating*Test: `mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test`
- Boundary guard confirmation: `mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,ReservationCreateApiIntegrationTest,WalkInDirectSeatingControllerTest" test`
- Full backend test suite: `mvn test`
- Frontend build: `npm run build`

## 11. Test Result

Passed:

- `mvn -q "-Dtest=ReservationArrivedDirectSeatingLocalRuntimeTransactionTest" test`
- `mvn -q "-Dtest=ReservationArrivedDirectSeating*Test" test`
- `mvn -q "-Dtest=CleaningControllerTest,ReservationControllerTest,ReservationCreateApiIntegrationTest,WalkInDirectSeatingControllerTest" test`
- `mvn test`: 340 tests, 0 failures, 0 errors, 0 skipped
- `npm run build`

The first full `mvn test` run exposed stale vertical-slice UI allowlists in older guard tests. Those allowlists were updated to acknowledge the already-implemented `ReservationArrivedDirectSeatingPage.vue` while still blocking Queue, Turnover, No-show, Cancellation, table map, and other out-of-scope artifacts. Full `mvn test` then passed.

## 12. Boundary Check

- Reservation state machine changed incorrectly: No
- API path changed: No
- App Gate bypassed: No
- App Gate app key changed: No
- App Gate permission key changed: No
- Migration changed: No
- Database structure changed: No
- Production database touched: No
- Production config changed: No
- Reservation list created: No
- Reservation calendar created: No
- Table map created: No
- Auto assignment implemented: No
- Queue implemented: No
- No-show implemented: No
- Cancellation implemented: No
- Cleaning changed: No
- Turnover implemented: No
- New seating table created: No

## 13. Open Questions

- TableGroup was covered by automated tests but not repeated through the browser in this transaction-fix round.
- The local security `/error` behavior from the previous failed validation was not changed because the rollback no longer occurs on the successful path.

## 14. Next Step Recommendation

Proceed with the next Reservation Arrived Direct Seating slice. If desired, run one browser TableGroup smoke pass before starting the next feature, but it is not blocking this transaction fix.
