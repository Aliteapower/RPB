# Store Staff Closed Loop Runtime Smoke Report V1

## 1. Read Documents

- `docs/frontend/STORE_STAFF_CLOSED_LOOP_UI_POLISH_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_RUNTIME_FIX_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed before runtime smoke:

- WalkIn Direct Seating API integration validation passed.
- Cleaning Complete API integration validation passed.
- Cleaning Complete runtime closed loop fix passed.
- WalkIn UI exists.
- Cleaning UI exists.
- Closed Loop UI Polish completed.
- Fresh `npm run build` passed in this round.
- Fresh `mvn test` passed in this round: 165 tests, 0 failures, 0 errors.
- Migration changed: No.
- Production database touched: No.

## 2. Runtime Environment

Frontend:

- Vue 3 / Vite / TypeScript / Pinia / Vue Router
- Runtime URL:

```text
http://127.0.0.1:5173
```

- Vite proxy target:

```text
VITE_API_PROXY_TARGET=http://127.0.0.1:18082
```

Backend:

- Spring Boot local runtime
- Profile: `local`
- Port: `18082`
- Flyway disabled for runtime after V001 was manually applied
- JPA schema validation enabled

Database:

- Temporary local PostgreSQL 17.10
- Host: `127.0.0.1`
- Port: `55716`
- Database: `postgres`
- Schema: existing `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- Runtime fixture: one temporary local Tenant, Store, Area, StorePolicy, and two DiningTable rows
- Production database touched: No

Auth:

- Existing local/test actor placeholder
- Tenant: `10000000-0000-0000-0000-000000000301`
- Store: `20000000-0000-0000-0000-000000000301`
- Actor: `30000000-0000-0000-0000-000000000301`
- Role: `store_staff`
- Permissions:
  - `walkin.direct_seating.create`
  - `cleaning.start`
  - `cleaning.complete`

## 3. Frontend Startup

Initial attempted background command:

```text
npm run dev -- --host 127.0.0.1 --port 5173
```

In the PowerShell background wrapper, the Vite arguments were forwarded incorrectly as positional arguments, causing SPA route `404`.

Corrected runtime command:

```text
node_modules/.bin/vite.cmd --host 127.0.0.1 --port 5173
```

Result:

- Vite started.
- `/stores/:storeId/walk-ins/direct-seating` returned HTTP 200.
- `/api` proxy pointed to the local backend on port `18082`.

No source code or Vite config was changed.

## 4. Backend Startup

Backend command:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Runtime arguments included:

- `--server.port=18082`
- `--spring.flyway.enabled=false`
- `--rpb.local-auth.enabled=true`
- local runtime Tenant, Actor, Store scope, role, and permissions

Result:

- Spring Boot started on port `18082`.
- JPA validation passed against the temporary PostgreSQL schema.
- No production auth system was implemented.

## 5. Database Environment

Database setup:

- Created temporary PostgreSQL data directory under `target/closed-loop-runtime-smoke`.
- Applied V001 migration manually.
- Inserted temporary local fixture data only.

Smoke fixture:

- Tenant: `Runtime Smoke Tenant`
- Store: `Runtime Smoke Store`
- Tables:
  - `S1`, capacity 1-2, initially `available`
  - `A1`, capacity 1-4, initially `available`

No production database, production credentials, runtime seed data, or migration change was used.

## 6. WalkIn Direct Seating Smoke Result

Browser route:

```text
/stores/20000000-0000-0000-0000-000000000301/walk-ins/direct-seating
```

Input:

- `partySize = 2`
- `customerName = Smoke Guest`
- `phoneE164` empty
- no manual `tableId`
- no manual `tableGroupId`

Observed success display:

- `walkInId = fd3169c7-2a75-40d5-af1f-174009400ad7`
- `seatingId = 5a256c0c-1ab2-4874-bc01-38a0c731cb06`
- `resource = TABLE 40000000-0000-0000-0000-000000000301`
- `status = occupied`
- `idempotency = completed`

Handoff:

- `开始清台` link was displayed.
- Link target:

```text
/stores/20000000-0000-0000-0000-000000000301/cleaning?seatingId=5a256c0c-1ab2-4874-bc01-38a0c731cb06
```

## 7. Start Cleaning Smoke Result

Browser route after handoff:

```text
/stores/20000000-0000-0000-0000-000000000301/cleaning?seatingId=5a256c0c-1ab2-4874-bc01-38a0c731cb06
```

Observed:

- `seatingId` was auto-filled from route query.
- Start Cleaning succeeded.
- Success display included:
  - `cleaningId = c0080f10-a0d4-48a8-91f6-9c262e2181e8`
  - `seatingId = 5a256c0c-1ab2-4874-bc01-38a0c731cb06`
  - `resource = TABLE 40000000-0000-0000-0000-000000000301`
  - `cleaningStatus = cleaning`
  - `tableStatus = cleaning`
  - `events = cleaning.started, table.cleaning`
  - `idempotency = completed`
- Page displayed:

```text
清台已开始，可继续完成清台
```

- Complete Cleaning form was auto-filled with:

```text
c0080f10-a0d4-48a8-91f6-9c262e2181e8
```

## 8. Complete Cleaning Smoke Result

Action:

- Clicked `Complete Cleaning` using the auto-filled `cleaningId`.

Observed success display:

- Heading: `桌位已释放`
- `cleaningId = c0080f10-a0d4-48a8-91f6-9c262e2181e8`
- `resource = TABLE 40000000-0000-0000-0000-000000000301`
- `cleaningStatus = released`
- `tableStatus = available`
- `events = cleaning.completed, table.available`
- `idempotency = completed`

Closed loop result:

```text
WalkIn Direct Seating -> Start Cleaning -> Complete Cleaning
```

passed through:

```text
Vue UI -> Vite proxy -> Spring Boot backend -> Application Service -> Persistence Adapter -> PostgreSQL -> UI success display
```

## 9. Database Assertions

Counts after the successful browser flow:

| Table | Count |
| --- | ---: |
| `walk_ins` | 1 |
| `seatings` | 1 |
| `seating_resources` | 1 |
| `cleanings` | 1 |
| `business_events` | 8 |
| `state_transition_logs` | 8 |
| `audit_logs` | 3 |
| `idempotency_records` | 3 |
| `reservations` | 0 |
| `queue_tickets` | 0 |
| `turnovers` | 0 |

Cleaning:

- `cleanings.status = released`
- `completed_at` populated: Yes
- `released_at` populated: Yes

Table:

- `dining_tables.status = available` for the smoke resource table.

BusinessEvent:

- `walk_in.created`
- `seating.created`
- `table.locked`
- `table.occupied`
- `cleaning.started`
- `table.cleaning`
- `cleaning.completed`
- `table.available`

StateTransitionLog:

- `walk_in created -> seated`
- `seating planned -> occupied`
- `dining_table available -> locked`
- `dining_table locked -> occupied`
- `cleaning pending -> cleaning`
- `dining_table occupied -> cleaning`
- `cleaning cleaning -> released`
- `dining_table cleaning -> available`

SeatingResource:

- `status = released`
- `resource_type = dining_table`
- `table_id = 40000000-0000-0000-0000-000000000301`
- `table_group_id = null`

Seating boundary:

- `status = cleaning_triggered`
- `reservation_id = null`
- `queue_ticket_id = null`
- `walk_in_id` populated

IdempotencyRecord:

- `seat_walk_in_directly = completed`
- `start_cleaning = completed`
- `complete_cleaning = completed`

Boundary:

- Reservation created: No
- QueueTicket created: No
- Turnover created: No

## 10. Error Display Smoke

Error case validated:

- invalid `phoneE164`

Browser input:

```text
phoneE164 = not-a-phone
```

Observed UI error display:

- `error.code = INVALID_PHONE_E164`
- `error.messageKey = walkin.direct_seating.invalid_phone_e164`

This validation was client-side and did not create additional database records.

## 11. Commands Executed

Frontend build:

```text
npm run build
```

Result:

- Passed.
- `vue-tsc --noEmit`: passed.
- `vite build`: passed.

Backend tests:

```text
mvn test
```

Result:

- Passed.
- Tests run: 165.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Database setup:

```text
initdb
pg_ctl start
psql -f src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql
```

Result:

- Temporary local PostgreSQL started.
- V001 applied successfully.
- Runtime fixture inserted successfully.

Backend runtime:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Result:

- Started on port `18082`.

Frontend runtime:

```text
node_modules/.bin/vite.cmd --host 127.0.0.1 --port 5173
```

Result:

- Started on port `5173`.

Browser validation:

- Completed with in-app browser automation against the real Vite route and backend proxy.

## 12. Build / Test Result

- `npm run build`: Passed.
- `mvn test`: Passed, 165 tests, 0 failures, 0 errors.
- Browser runtime smoke: Passed.
- Database assertions: Passed.
- Error display smoke: Passed.

## 13. Files Changed

Created:

- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`

No production source code was changed.
No frontend source code was changed.
No backend source code was changed.
No migration or SQL file was changed.

## 14. Boundary Check

Reservation UI created: No  
Queue UI created: No  
Turnover UI created: No  
POS UI created: No  
Payment UI created: No  
Marketing UI created: No  
Membership UI created: No  
Complex table map created: No  
Drag-and-drop table layout created: No  
Table API created: No  
Seating query API created: No  
Cleaning query API created: No  
Customer search API created: No  
Reservation API created: No  
Queue API created: No  
Turnover API created: No  
Backend API changed: No  
Backend business flow changed: No  
Migration changed: No  
Database structure changed: No  
Production database touched: No  
Seed data inserted: No  
Frontend test framework added: No  
Full JWT/Login/User system implemented: No  
Full store switcher implemented: No  

## 15. Open Questions

- Should the next UI round add a compact staff home page that links only to the approved WalkIn and Cleaning pages?
- Should a future approved frontend testing round introduce Vitest for route/query/form smoke tests?
- Should a future Table/Seating/Cleaning query API replace manual ID fallback once selector/list capability is approved?

## 16. Next Step Recommendation

Recommended next round:

```text
Store Staff Closed Loop Operational Handoff
```

Suggested scope:

- Document the current local operator path for demos.
- Keep the UI limited to WalkIn Direct Seating and Cleaning Complete.
- Do not add Reservation, Queue, Turnover BI, complex table map, new backend API, migration, production auth, or database schema changes.
