# Reservation CheckIn Local Runtime Security Fix Report V1

## 1. Goal

Fix the local runtime Spring Security block for Reservation CheckIn:

```text
ReservationCheckInPage.vue
-> POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
-> Spring Security local runtime
-> empty HTTP 403 before App Gate
```

The fix had to let the request reach App Gate and the CheckIn business layer without changing CheckIn business logic, reservation state machine, UI, API contract, migrations, Queue, Seating, No-show, or Cancellation.

## 2. Root Cause

`LocalRuntimeSecurityConfiguration` had explicit local/test `permitAll` entries for existing browser-driven APIs, including Reservation Create:

```text
POST /api/v1/stores/*/reservations
```

It did not include Reservation CheckIn:

```text
POST /api/v1/stores/*/reservations/*/check-in
```

Because the local security chain ends with `anyRequest().denyAll()`, the CheckIn POST was rejected before MVC handler resolution, before `@RequireAppGate`, and before `ReservationCheckInApplicationService`.

## 3. Red Test

Added:

```text
src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationCheckInSecurityTest.java
```

Before the fix, running:

```text
mvn -q "-Dtest=LocalRuntimeReservationCheckInSecurityTest" test
```

failed with:

```text
Status expected:<200> but was:<403>
Handler: Type = null
Content type = null
Body =
```

This reproduced the UI validation symptom exactly: an empty 403 from Spring Security before the request reached App Gate or the CheckIn controller.

## 4. Code Change

Updated:

```text
src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java
```

Added one local/test allowlist entry:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/check-in").permitAll()
```

No other production Java source was changed.

## 5. Green Test

After the fix:

```text
mvn -q "-Dtest=LocalRuntimeReservationCheckInSecurityTest" test
```

passed.

The regression proves that, in the local profile with `rpb.local-auth.enabled=true`, a local actor with `reservation.check_in` can send the CheckIn POST without JWT login and the request reaches the controller/App Gate/application-service mock path.

## 6. Runtime Validation

Runtime environment:

- Backend: `http://127.0.0.1:18082`, Spring Boot local profile
- Frontend: `http://127.0.0.1:5174`
- PostgreSQL: temporary local PostgreSQL 17 on `127.0.0.1:57427`
- Runtime directory: `target/reservation-checkin-local-security-runtime/20260621-140523`
- Tenant: `10000000-0000-0000-0000-000000000911`
- Store: `20000000-0000-0000-0000-000000000911`
- Actor: `30000000-0000-0000-0000-000000000911`
- Reservation: `50000000-0000-0000-0000-000000000911`

Local actor permissions included:

```text
walkin.direct_seating.create
cleaning.start
cleaning.complete
reservation.create
reservation.check_in
```

## 7. Fresh CheckIn Result

Browser submitted:

```text
POST /api/v1/stores/20000000-0000-0000-0000-000000000911/reservations/50000000-0000-0000-0000-000000000911/check-in
```

Response:

```text
HTTP 200
status=arrived
alreadyArrived=false
events=reservation.arrived
idempotency.status=completed
```

## 8. Already Arrived Result

The browser submitted the same arrived reservation with a new idempotency key.

Response:

```text
HTTP 200
status=arrived
alreadyArrived=true
events=[]
idempotency.status=completed
```

No duplicate arrival event, transition log, or success audit was written.

## 9. Error Result

The browser submitted a missing reservation id:

```text
50000000-0000-0000-0000-000000000999
```

Response:

```text
HTTP 404
error.code=RESERVATION_NOT_FOUND
error.messageKey=reservation.not_found
idempotency.status=failed
```

The UI displayed the structured backend error envelope.

## 10. Database Evidence

After the runtime validation:

```text
reservation_status=arrived
business_events_reservation_arrived=1
state_transition_confirmed_to_arrived=1
audit_reservation_check_in=1
audit_reservation_check_in_failed=1
idempotency_total=3
idempotency_completed=2
idempotency_failed=1
app_gate_audit_logs=0
queue_tickets=0
seatings=0
table_locks=0
reservation_preassignments=0
check_ins_regclass=null
```

`app_gate_audit_logs=0` is expected for authorized requests and confirms there was no App Gate denial. The business evidence proves the request now reaches the CheckIn application layer.

## 11. Verification Commands

Executed:

```text
mvn -q "-Dtest=LocalRuntimeReservationCheckInSecurityTest" test
mvn -q "-Dtest=ReservationCheckIn*Test" test
mvn test
npm run build
```

Results:

- `LocalRuntimeReservationCheckInSecurityTest`: Passed after fix
- `ReservationCheckIn*Test`: Passed
- `mvn test`: Passed, `301` tests, `0` failures, `0` errors, `0` skipped
- `npm run build`: Passed

## 12. Files Changed

Changed:

- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- `docs/frontend/RESERVATION_CHECKIN_UI_VALIDATION_REPORT.md`

Added:

- `src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationCheckInSecurityTest.java`
- `docs/backend/RESERVATION_CHECKIN_LOCAL_RUNTIME_SECURITY_FIX_REPORT.md`

## 13. Not Changed

No changes were made to:

- CheckIn business logic
- Reservation state machine
- App Gate business rules
- Reservation CheckIn API path or contract
- Reservation CheckIn UI behavior
- Migration or schema
- Queue
- Seating
- Table assignment
- No-show
- Cancellation

## 14. Conclusion

Reservation CheckIn local runtime is fixed.

The local Spring Security allowlist now permits the CheckIn POST to enter MVC/App Gate/business handling. The new regression test prevents a return of the empty pre-App-Gate 403, and browser runtime validation confirms fresh CheckIn, already-arrived handling, structured error display, and DB boundary assertions.
