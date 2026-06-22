# Cleaning Complete Runtime Closed Loop Fix Report V1

## 1. Read Documents

- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_CONTRACT.md`
- `docs/backend/CLEANING_VERTICAL_SLICE_CHECKLIST.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

Confirmed previous state:

- `npm run build`: Passed
- Start Cleaning browser-to-backend: Passed
- Complete Cleaning browser-to-backend: Failed
- Failure: `UnexpectedRollbackException`
- Previous `mvn test`: Passed, 164 tests, 0 failures, 0 errors
- Migration changed: No
- Production database touched: No

## 2. Original Failure

The local runtime flow failed after Start Cleaning succeeded:

```text
Start Cleaning
-> returned cleaningId
-> Complete Cleaning
-> UnexpectedRollbackException
```

The existing integration suite had independent Start and Complete fixture cases, but it did not execute Complete Cleaning against the same Cleaning record returned by Start Cleaning.

## 3. Reproduction Test

Added a backend integration regression test:

```text
CleaningCompleteApiIntegrationTest.startThenCompleteCleaningWithReturnedCleaningIdThroughFullApiToPostgresPath
```

The test uses:

- test profile
- temporary local PostgreSQL
- existing V001 schema
- test-only actor provider
- real CleaningController
- real CleaningApplicationService
- real persistence adapters and JPA repositories

Red result before the fix:

```text
mvn -q '-Dtest=CleaningCompleteApiIntegrationTest#startThenCompleteCleaningWithReturnedCleaningIdThroughFullApiToPostgresPath' test
```

Result:

- Failed
- Error: `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`

## 4. Root Cause

`DiningTablePersistenceAdapter.save` mapped a domain `DiningTable` back to a new JPA entity with `version = 0`.

In the real closed loop:

1. Start Cleaning updates the table from `occupied` to `cleaning`.
2. JPA increments `dining_tables.version`.
3. Complete Cleaning loads the same table later and tries to update it from `cleaning` to `available`.
4. The mapper-created detached entity loses the current version and saves with stale `version = 0`.
5. Hibernate marks the transaction rollback-only.
6. The application service catches the runtime persistence failure and returns an application result, but transaction commit then raises `UnexpectedRollbackException`.

The issue was persistence version loss, not a state-machine, migration, API, UI, audit, event, or idempotency rule issue.

## 5. Fix Applied

Updated:

- `src/main/java/com/rpb/reservation/table/persistence/adapter/DiningTablePersistenceAdapter.java`

The adapter now:

- maps the requested domain state to an entity payload
- reloads the existing scoped `DiningTableEntity`
- preserves existing persistence metadata:
  - `created_at`
  - `deleted_at`
  - `version`
- saves the entity with the current JPA optimistic-lock version
- uses `version = null` only for new table rows

No migration, schema, API, UI, state-machine, audit, event, or idempotency behavior was changed.

## 6. Transaction Behavior After Fix

Success path:

- Start Cleaning commits normally.
- Complete Cleaning commits normally against the same returned `cleaningId`.
- `cleanings.status` becomes `released`.
- `dining_tables.status` becomes `available`.
- BusinessEvent, StateTransitionLog, AuditLog, and IdempotencyRecord writes commit in the same command transaction.

Failure behavior:

- No exception swallowing was added.
- No transaction bypass was introduced.
- No `@Transactional` boundary was removed.
- Idempotency failure persistence remains the existing V1 strategy; future infrastructure-failure durability can still consider `REQUIRES_NEW` if Product Owner asks for it.

## 7. Database Assertions

Regression test assertions:

- `cleanings.status = released`
- `cleanings.completed_at is not null`
- `cleanings.released_at is not null`
- `dining_tables.status = available`
- `business_events` contains:
  - `cleaning.started`
  - `table.cleaning`
  - `cleaning.completed`
  - `table.available`
- `state_transition_logs = 4`
- `audit_logs = 2`
- both Start and Complete `idempotency_records.status = completed`
- `reservations = 0`
- `queue_tickets = 0`
- `turnovers = 0`

## 8. UI Validation Result

Revalidated through local runtime:

```text
CleaningCompletePage route
-> Vite proxy
-> local Spring Boot backend
-> temporary PostgreSQL
```

Observed result:

- Route status: `200`
- Start Cleaning:
  - `cleaningStatus = cleaning`
  - `tableStatus = cleaning`
  - `idempotency.status = completed`
- Complete Cleaning:
  - `cleaningStatus = released`
  - `tableStatus = available`
  - `idempotency.status = completed`
- Database:
  - `cleanings.status = released`
  - `dining_tables.status = available`
  - events: `cleaning.started`, `table.cleaning`, `cleaning.completed`, `table.available`
  - `state_transition_logs = 4`
  - `audit_logs = 2`
  - idempotency records completed
  - Reservation / QueueTicket / Turnover counts remain `0,0,0`

Validation used a temporary local PostgreSQL fixture only. No production database or production seed data was touched.

## 9. Commands Executed

Red reproduction:

```text
mvn -q '-Dtest=CleaningCompleteApiIntegrationTest#startThenCompleteCleaningWithReturnedCleaningIdThroughFullApiToPostgresPath' test
```

Result:

- Failed before fix with `UnexpectedRollbackException`

Green targeted regression:

```text
mvn -q '-Dtest=CleaningCompleteApiIntegrationTest#startThenCompleteCleaningWithReturnedCleaningIdThroughFullApiToPostgresPath' test
```

Result:

- Passed

Cleaning integration suite:

```text
mvn -q '-Dtest=CleaningCompleteApiIntegrationTest' test
```

Result:

- Passed
- Tests run: 29, failures: 0, errors: 0

Full backend suite:

```text
mvn test
```

Result:

- BUILD SUCCESS
- Tests run: 165, failures: 0, errors: 0, skipped: 0

Frontend build:

```text
npm run build
```

Result:

- Passed
- `vue-tsc --noEmit`: passed
- `vite build`: passed

Local runtime validation:

- `mvn spring-boot:run '-Dspring-boot.run.profiles=local'`
- `vite.cmd --host 127.0.0.1 --port 5173`
- Start and Complete requests sent through Vite `/api` proxy
- Result: Passed

## 10. Files Changed

Production code:

- `src/main/java/com/rpb/reservation/table/persistence/adapter/DiningTablePersistenceAdapter.java`

Test code:

- `src/test/java/com/rpb/reservation/cleaning/integration/CleaningCompleteApiIntegrationTest.java`

Documentation:

- `docs/api/CLEANING_COMPLETE_RUNTIME_FIX_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`

## 11. Boundary Check

Reservation implemented: No  
Queue implemented: No  
Turnover BI implemented: No  
Reservation UI created: No  
Queue UI created: No  
Turnover UI created: No  
Complex table map created: No  
Migration changed: No  
Database schema changed: No  
Production database touched: No  
Production seed data inserted: No  
Full Auth system implemented: No  

## 12. Open Questions

- Should other versioned persistence adapters later adopt the same managed-version preservation pattern in a dedicated persistence hardening round?
- Should infrastructure failure idempotency/audit persistence later use an explicit `REQUIRES_NEW` policy, or remain V1 best-effort on rollback paths?

## 13. Next Step Recommendation

Recommended next round:

```text
Store Staff Cleaning Complete UI Final Smoke / Handoff
```

The next round can stay frontend-focused and verify staff ergonomics now that the backend closed loop is fixed. Do not expand into Reservation, Queue, Turnover BI, complex table map, new migration, or production auth.
