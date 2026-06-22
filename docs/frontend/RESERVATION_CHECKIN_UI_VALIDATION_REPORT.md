# Reservation CheckIn UI Validation Report V2

## 1. Status

Result: Passed after local runtime security fix.

This report supersedes the previous blocked result where the CheckIn POST returned an empty HTTP 403 before App Gate.

## 2. Scope

Validated chain:

```text
ReservationCheckInPage.vue
-> reservationCheckInApi.ts
-> POST /api/v1/stores/{storeId}/reservations/{reservationId}/check-in
-> local Spring Security
-> App Gate
-> ReservationCheckInApplicationService
-> PostgreSQL evidence
-> UI result rendering
```

No Reservation list/calendar/search, Queue, Seating, Table assignment, No-show, Cancellation, migration, or state-machine behavior was added.

## 3. Runtime Environment

- Workspace: `D:\RPB`
- Frontend: `http://127.0.0.1:5174`
- Frontend proxy: `VITE_API_PROXY_TARGET=http://127.0.0.1:18082`
- Backend: Spring Boot local profile at `http://127.0.0.1:18082`
- Database: temporary local PostgreSQL 17 at `127.0.0.1:57427`
- Runtime directory: `target/reservation-checkin-local-security-runtime/20260621-140523`
- Migrations applied manually: `V001__reservation_platform_bootstrap.sql`, `V002__app_gate_foundation.sql`
- Backend Flyway runtime: disabled for this validation run
- Production database touched: No

Seed:

- Tenant: `10000000-0000-0000-0000-000000000911`
- Store: `20000000-0000-0000-0000-000000000911`
- Actor: `30000000-0000-0000-0000-000000000911`
- Reservation: `50000000-0000-0000-0000-000000000911`
- Reservation code: `R-CHECKIN-0911`
- Initial status: `confirmed`

## 4. Staff Home Permission Display

With `reservation.check_in` present in `/api/me/apps`, Staff Home displayed `Check In Reservation`.

With browser override header:

```text
X-Test-Permissions: reservation.create
```

Staff Home still displayed the `reservation_queue` app entry but hid `Check In Reservation`.

## 5. Route And Form

Validated route:

```text
/stores/:storeId/reservations/check-in
```

Concrete URL:

```text
http://127.0.0.1:5174/stores/20000000-0000-0000-0000-000000000911/reservations/check-in
```

Validated fields:

- `reservationId`
- `arrivedAt`
- `reasonCode`
- `note`

The UI request body stayed limited to `arrivedAt`, `reasonCode`, and `note`.

## 6. Fresh CheckIn

Browser submitted:

```text
POST /api/v1/stores/20000000-0000-0000-0000-000000000911/reservations/50000000-0000-0000-0000-000000000911/check-in
```

Response:

```json
{
  "success": true,
  "reservationId": "50000000-0000-0000-0000-000000000911",
  "reservationCode": "R-CHECKIN-0911",
  "status": "arrived",
  "arrivedAt": "2030-06-20T03:10:00Z",
  "alreadyArrived": false,
  "events": ["reservation.arrived"],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

UI rendered:

- `status=arrived`
- `reservationCode=R-CHECKIN-0911`
- `alreadyArrived=false`
- `events=reservation.arrived`
- `idempotency.status=completed`

Screenshot artifact:

```text
target/reservation-checkin-local-security-runtime/20260621-140523/checkin-fresh-success.png
```

## 7. Already Arrived

Browser submitted the same reservation again with a new UI-generated idempotency key.

Response:

```json
{
  "success": true,
  "reservationId": "50000000-0000-0000-0000-000000000911",
  "reservationCode": "R-CHECKIN-0911",
  "status": "arrived",
  "arrivedAt": "2030-06-20T03:10:00Z",
  "alreadyArrived": true,
  "events": [],
  "idempotency": {
    "status": "completed",
    "replayed": false
  }
}
```

UI rendered:

- `alreadyArrived=true`
- `events=[]`
- `idempotency.status=completed`

Screenshot artifact:

```text
target/reservation-checkin-local-security-runtime/20260621-140523/checkin-already-arrived.png
```

## 8. Error Display

Browser submitted a missing reservation id:

```text
50000000-0000-0000-0000-000000000999
```

Response:

```json
{
  "success": false,
  "error": {
    "code": "RESERVATION_NOT_FOUND",
    "messageKey": "reservation.not_found",
    "details": {}
  },
  "idempotency": {
    "status": "failed"
  }
}
```

UI rendered:

```text
Error
RESERVATION_NOT_FOUND
reservation.not_found
```

Screenshot artifact:

```text
target/reservation-checkin-local-security-runtime/20260621-140523/checkin-not-found-error.png
```

## 9. Database Assertions

After the fresh, already-arrived, and not-found UI submissions:

```text
reservation_status=arrived
reservation_updated_at=2030-06-20 11:10:00+08
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

Interpretation:

- Fresh CheckIn wrote exactly one arrival event, transition log, and success audit.
- Already-arrived path did not duplicate business evidence.
- Missing reservation produced a failed idempotency record and failed audit only.
- App Gate deny audit stayed at 0, proving authorized requests entered the business layer.
- Queue, Seating, TableLock, ReservationPreassignment, and `check_ins` artifacts remained absent.

## 10. Local Security Regression Link

The UI validation depends on the local runtime allowlist fixed in:

```text
src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java
```

Regression test:

```text
src/test/java/com/rpb/reservation/walkin/auth/LocalRuntimeReservationCheckInSecurityTest.java
```

The regression test failed before the fix with empty HTTP 403 and passed after adding the CheckIn POST allowlist entry.

## 11. Commands

Executed:

```text
mvn -q "-Dtest=LocalRuntimeReservationCheckInSecurityTest" test
mvn -q "-Dtest=ReservationCheckIn*Test" test
mvn test
npm run build
```

Runtime validation commands included:

```text
initdb -A trust -U postgres -D target/reservation-checkin-local-security-runtime/20260621-140523/pgdata
pg_ctl -D target/reservation-checkin-local-security-runtime/20260621-140523/pgdata -o "-p 57427 -h 127.0.0.1" -w start
psql -f src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql
psql -f src/main/resources/db/migration/V002__app_gate_foundation.sql
mvn spring-boot:run -Dspring-boot.run.profiles=local
npm run dev -- --host 127.0.0.1 --port 5174
```

## 12. Build And Test Results

- `mvn -q "-Dtest=LocalRuntimeReservationCheckInSecurityTest" test`: Passed
- `mvn -q "-Dtest=ReservationCheckIn*Test" test`: Passed
- `mvn test`: Passed, `301` tests, `0` failures, `0` errors, `0` skipped
- `npm run build`: Passed, `vue-tsc --noEmit` and `vite build`

## 13. Runtime Cleanup

Stopped after validation:

- Vite dev server on `5174`
- Backend local runtime on `18082`
- Temporary PostgreSQL on `57427`

Port checks after cleanup:

```text
frontend5174Listening=false
backend18082Listening=false
pg57427Listening=false
```

## 14. Boundary

Reservation CheckIn UI runtime success: Yes.  
Local Spring Security pre-App-Gate block fixed: Yes.  
App Gate bypassed: No.  
CheckIn business logic changed: No.  
Reservation state machine changed: No.  
Reservation list/calendar/search added: No.  
Queue or Seating behavior added: No.  
No-show or Cancellation behavior added: No.  
Migration changed: No.  
Production data touched: No.
