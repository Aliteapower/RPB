# Reservation Staff End-to-End Smoke Review Result

## 1. Read Documents

Read and aligned:

- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/frontend/RESERVATION_CREATE_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CREATE_UI_VALIDATION_REPORT.md`
- `docs/frontend/STORE_STAFF_RESERVATION_CREATE_HANDOFF.md`
- `docs/backend/RESERVATION_CHECKIN_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CHECKIN_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CHECKIN_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_CHECKIN_UI_VALIDATION_REPORT.md`
- `docs/backend/RESERVATION_CHECKIN_LOCAL_RUNTIME_SECURITY_FIX_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_ARRIVED_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_IMPLEMENTATION_REPORT.md`
- `docs/frontend/RESERVATION_ARRIVED_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/backend/RESERVATION_ARRIVED_DIRECT_SEATING_LOCAL_RUNTIME_TRANSACTION_FIX_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `src/pages/StoreStaffHomePage.vue`
- `src/router/index.ts`
- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `docs/backend/APP_GATE_OPERATIONAL_HANDOFF.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT.md`
- `docs/backend/APP_GATE_PERMISSION_METADATA_ALIGNMENT_REPORT.md`
- `docs/backend/APP_GATE_INTEGRATION_CHECKLIST.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`
- `docs/database/SCHEMA_DESIGN.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `src/main/resources/db/migration/V002__app_gate_foundation.sql`

## 2. Created / Updated Files

Created:

- `docs/frontend/RESERVATION_STAFF_END_TO_END_HANDOFF.md`
- `docs/frontend/RESERVATION_STAFF_END_TO_END_SMOKE_REVIEW_REPORT.md`

No source code, migration, SQL, production config, or seed data was changed.

## 3. Validation Environment

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8080`
- Database: local PostgreSQL `127.0.0.1:63822`, database `reservation_platform`
- Runtime directory: `target/local-runtime/20260621-end-to-end-smoke`
- Tenant: `10000000-0000-0000-0000-000000000701`
- Store: `20000000-0000-0000-0000-000000000701`
- Actor: `30000000-0000-0000-0000-000000000701`
- Auth: local/test actor

Local actor permissions used for smoke:

```text
reservation.create
reservation.check_in
reservation.seat
cleaning.start
cleaning.complete
walkin.direct_seating.create
```

## 4. Staff Home Review

Passed.

Route:

```text
/stores/20000000-0000-0000-0000-000000000701/staff
```

Visible entries:

- `WalkIn Direct Seating`
- `Cleaning Complete`
- `Create Reservation`
- `Check In Reservation`
- `Seat Arrived Reservation`

No Queue, Reservation list/calendar/search, table map, auto assignment, No-show, Cancellation, or Turnover entry was visible.

## 5. Permission Review

Passed.

`GET /api/me/apps?storeId=20000000-0000-0000-0000-000000000701` returned the visible `reservation_queue` app with:

```text
cleaning.complete
reservation.check_in
reservation.seat
walkin.direct_seating.create
cleaning.start
reservation.create
```

Backend App Gate remains the final authorization layer for each command endpoint.

## 6. Demo Path

Executed through the browser:

```text
Staff Home
-> Create Reservation
-> Check In Reservation
-> Seat Arrived Reservation
-> Cleaning Complete / Start Cleaning
-> Cleaning Complete / Complete Cleaning
```

Smoke identifiers:

- Reservation: `df06cee2-8577-418f-b399-f0177f756563`
- Reservation code: `R-20260622-2296`
- Table: `22000000-0000-0000-0000-000000000702` (`T-702`)
- Seating: `7db79511-fbf9-451c-970c-53dd63c75ad1`
- Cleaning: `9f3c6a30-88a9-4756-8081-f6729bb3f85a`

## 7. Create Reservation Result

Passed.

Browser result:

- `reservationCode = R-20260622-2296`
- `reservationId = df06cee2-8577-418f-b399-f0177f756563`
- `status = confirmed`
- `events = reservation.created, reservation.confirmed`
- `idempotency = completed`

## 8. CheckIn Result

Passed.

Browser result:

- `status = arrived`
- `reservationCode = R-20260622-2296`
- `alreadyArrived = false`
- `events = reservation.arrived`
- `idempotency.status = completed`

## 9. Direct Seating Result

Passed.

Browser result:

- `reservationStatus = seated`
- `reservationCode = R-20260622-2296`
- `seatingId = 7db79511-fbf9-451c-970c-53dd63c75ad1`
- `resource = table 22000000-0000-0000-0000-000000000702`
- `alreadySeated = false`
- `seatingStatus = occupied`
- `events = reservation.seated, seating.created, table.occupied`
- `idempotency.status = completed`

## 10. Cleaning Result

Passed.

Start Cleaning browser result:

- `cleaningId = 9f3c6a30-88a9-4756-8081-f6729bb3f85a`
- `seatingId = 7db79511-fbf9-451c-970c-53dd63c75ad1`
- `resource = TABLE 22000000-0000-0000-0000-000000000702`
- `cleaningStatus = cleaning`
- `tableStatus = cleaning`
- `events = cleaning.started, table.cleaning`
- `idempotency = completed`

Complete Cleaning browser result:

- Heading: `桌位已释放`
- `cleaningId = 9f3c6a30-88a9-4756-8081-f6729bb3f85a`
- `resource = TABLE 22000000-0000-0000-0000-000000000702`
- `cleaningStatus = released`
- `tableStatus = available`
- `events = cleaning.completed, table.available`
- `idempotency = completed`

## 11. Database Assertions

Reservation:

- `df06cee2-8577-418f-b399-f0177f756563`
- `reservation_code = R-20260622-2296`
- Final `status = seated`
- `version = 2`

Seating:

- `7db79511-fbf9-451c-970c-53dd63c75ad1`
- `reservation_id = df06cee2-8577-418f-b399-f0177f756563`
- `queue_ticket_id = null`
- `walk_in_id = null`
- Final `status = cleaning_triggered`

SeatingResource:

- One resource for the smoke seating.
- `resource_type = dining_table`
- `table_id = 22000000-0000-0000-0000-000000000702`
- `table_group_id = null`
- Final `status = released`

Table:

- `T-702`
- Final `status = available`

Cleaning:

- `9f3c6a30-88a9-4756-8081-f6729bb3f85a`
- `seating_id = 7db79511-fbf9-451c-970c-53dd63c75ad1`
- Final `status = released`
- `completed_at` populated
- `released_at` populated

BusinessEvent:

- `reservation.created`
- `reservation.confirmed`
- `reservation.arrived`
- `reservation.seated`
- `seating.created`
- `table.occupied`
- `cleaning.started`
- `table.cleaning`
- `cleaning.completed`
- `table.available`

StateTransitionLog:

- `reservation.confirm`: `none -> confirmed`
- `reservation.check_in`: `confirmed -> arrived`
- `reservation.seat`: `arrived -> seated`
- `seating.occupy`: `planned -> occupied`
- `dining_table.occupy`: `available -> occupied`
- `cleaning.start`: `pending -> cleaning`
- `dining_table.cleaning`: `occupied -> cleaning`
- `cleaning.complete`: `cleaning -> released`
- `dining_table.available`: `cleaning -> available`

AuditLog:

- `reservation.create`
- `reservation.check_in`
- `reservation.seat`
- `cleaning.start.completed`
- `cleaning.complete.completed`

IdempotencyRecord:

- `create_reservation = completed`
- `check_in_reservation = completed`
- `seat_arrived_reservation = completed`
- `start_cleaning = completed`
- `complete_cleaning = completed`

AppGateAuditLog:

- `0` denials observed for this allowed smoke path.

Boundary:

- `queue_tickets = 0`
- `reservation_preassignments = 0`
- Smoke reservation has no `no_show` or `cancelled` side effect.
- `reservation_seatings` table does not exist.
- Seating tables remain `seatings` and `seating_resources`.

## 12. Commands

Runtime checks:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/me/apps?storeId=20000000-0000-0000-0000-000000000701
Invoke-WebRequest http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000701/staff
psql -h 127.0.0.1 -p 63822 -U postgres -d reservation_platform -Atc "<validation SQL>"
```

Backend restart:

```powershell
mvn spring-boot:run
```

with local environment properties for:

```text
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080
SPRING_FLYWAY_ENABLED=false
RPB_LOCAL_AUTH_ENABLED=true
RPB_LOCAL_AUTH_PERMISSIONS_*=reservation.create/reservation.check_in/reservation.seat/cleaning.start/cleaning.complete/walkin.direct_seating.create
```

Required verification:

```powershell
npm run build
mvn test
```

Verification result:

- `npm run build`: passed, `vue-tsc --noEmit` passed and Vite built 57 modules.
- `mvn test`: passed, 340 tests, 0 failures, 0 errors, 0 skipped.

## 13. Boundary Check

Reservation list created: No  
Reservation calendar created: No  
Reservation search created: No  
Table map created: No  
Auto assignment created: No  
Queue implemented: No  
No-show implemented: No  
Cancellation implemented: No  
Turnover implemented: No  
Migration changed: No  
SQL changed: No  
Production database touched: No  
Seed data inserted: No  
Existing API paths changed: No  
App Gate bypassed: No  
Business state machines changed: No  

## 14. Known Limitations

- Current staff pages still use manual ID copy/paste between steps.
- UI copy is mixed Chinese and English; next UX polish should unify visible copy to Chinese before final i18n extraction.
- No Reservation list/calendar/search exists.
- No table map or table picker exists.
- No Queue fallback exists for unavailable tables.
- No No-show or Cancellation workflow exists.
- No production JWT/login exists.
- No Turnover analytics exists.

## 15. Open Questions

- Should the next UI polish round convert visible staff page copy to Chinese across all completed V1 staff flows?
- Should the next functional slice be Reservation Arrived To Queue or a lightweight Reservation lookup/list to reduce manual ID copy/paste?

## 16. Next Step Recommendation

Proceed to a UI copy consolidation round for the current completed staff flows, then plan the next business slice separately. Keep Queue, Reservation list/search, table map, No-show, and Cancellation out unless explicitly opened as their own contracts.
