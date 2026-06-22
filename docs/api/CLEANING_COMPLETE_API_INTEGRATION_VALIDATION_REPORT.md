# Cleaning Complete API Integration Validation Report V1

## 1. Read Documents

- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_API_ERROR_CONTRACT.md`
- `docs/api/CLEANING_API_IDEMPOTENCY_CONTRACT.md`
- `docs/backend/CLEANING_COMPLETE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/CLEANING_COMPLETE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/governance/BUSINESS_GLOSSARY.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/governance/DATA_STANDARD.md`
- `docs/governance/DATA_CHECKLIST.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL_OVERVIEW.md`
- `docs/skills/reservation-system/SKILL.md`

Previous round confirmation:

- `mvn test` passed.
- 136 tests.
- 0 failures.
- 0 errors.
- `CleaningController` created.
- Start Cleaning endpoint implemented.
- Complete Cleaning endpoint implemented.
- Reservation API implemented: No.
- Queue API implemented: No.
- Turnover API implemented: No.
- Vue UI implemented: No.
- Migration changed: No.
- Production database touched: No.

## 2. Test Environment

- Workspace: `D:\RPB`
- Java: Java 21
- Test runner: Maven Surefire / JUnit 5
- Spring profile: `test`
- PostgreSQL: local temporary PostgreSQL instance
- PostgreSQL version observed during test run: 17.10
- Auth: test-only `CurrentActorProvider` override reusing the existing WalkIn API actor boundary
- Production DB touched: No

## 3. Database Validation Method

- The Cleaning integration test starts a local temporary PostgreSQL instance with `initdb` and `pg_ctl`.
- The test helper applies `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql` to an empty local database.
- Spring JPA runs with `ddl-auto=validate`.
- Flyway remains disabled in the `test` profile, matching the existing WalkIn integration validation pattern.
- Test fixture data is inserted only into the temporary local PostgreSQL database.
- No production database, production credentials, runtime seed data, or production configuration is used.

## 4. Endpoints Tested

Start Cleaning:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
```

Complete Cleaning:

```text
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

## 5. Start Cleaning Success Cases

Covered:

- Start Cleaning from `seatingId` with dining table resource.
- Start Cleaning from `seatingId` with TableGroup resource.
- Completed idempotency replay returns `200 OK` with `idempotency.replayed = true`.

Validated successful dining table behavior:

- API returns `201 Created`.
- `resource.type = TABLE`.
- `cleaningStatus = cleaning`.
- `tableStatus = cleaning`.
- Event codes include `cleaning.started` and `table.cleaning`.

Validated successful TableGroup behavior:

- API returns `201 Created`.
- `resource.type = TABLE_GROUP`.
- TableGroup member tables become `cleaning`.
- Cleaning record targets the TableGroup, not an individual table.

## 6. Start Cleaning Error Cases

Covered:

- Missing `Idempotency-Key`.
- Seating not found.
- Seating resource not found.
- Table not occupied.
- TableGroup invalid.
- Cleaning already active.
- Forbidden role.
- Missing permission.
- Store scope mismatch.
- Idempotency in progress.
- Failed idempotency requires new key.
- Idempotency hash conflict.

Notes:

- `IDEMPOTENCY_IN_PROGRESS` is validated as `409 Conflict`.
- Missing idempotency key, forbidden role, missing permission, and store scope mismatch are rejected before application mutation.

## 7. Complete Cleaning Success Cases

Covered:

- Complete Cleaning by `cleaningId` with dining table resource.
- Complete Cleaning by `cleaningId` with TableGroup resource.
- Completed idempotency replay returns `200 OK` with `idempotency.replayed = true`.

Validated successful dining table behavior:

- API returns `200 OK`.
- `resource.type = TABLE`.
- `cleaningStatus = released`.
- `tableStatus = available`.
- Event codes include `cleaning.completed` and `table.available`.

Validated successful TableGroup behavior:

- API returns `200 OK`.
- `resource.type = TABLE_GROUP`.
- TableGroup member tables become `available`.
- Cleaning record remains tied to the TableGroup resource.

## 8. Complete Cleaning Error Cases

Covered:

- Missing `Idempotency-Key`.
- Cleaning not found.
- Cleaning already released / completed.
- Table not cleaning.
- TableGroup invalid.
- Forbidden role.
- Missing permission.
- Store scope mismatch.
- Idempotency in progress.
- Failed idempotency requires new key.
- Idempotency hash conflict.

Notes:

- `IDEMPOTENCY_IN_PROGRESS` is validated as `409 Conflict`.
- Completed replay does not write duplicate events, transitions, audit rows, or Cleaning/table mutations.

## 9. Database Assertions

Start Cleaning success assertions:

- `cleanings` record is created.
- `cleanings.status = cleaning`.
- DiningTable target changes to `cleaning`.
- TableGroup member tables change to `cleaning`.
- `seatings.status = cleaning_triggered`.
- `seating_resources.status = released`.
- `business_events` contains `cleaning.started`.
- `business_events` contains `table.cleaning`.
- `state_transition_logs` are written.
- `audit_logs` are written.
- `idempotency_records.status = completed`.

Complete Cleaning success assertions:

- `cleanings.status = released`.
- `cleanings.completed_at` is populated.
- `cleanings.released_at` is populated.
- DiningTable target changes to `available`.
- TableGroup member tables change to `available`.
- `business_events` contains `cleaning.completed`.
- `business_events` contains `table.available`.
- `state_transition_logs` are written.
- `audit_logs` are written.
- `idempotency_records.status = completed`.

Boundary table assertions:

- `reservations` remains empty.
- `queue_tickets` remains empty.
- `turnovers` remains empty.

## 10. Boundary Assertions

Reservation created: No  
QueueTicket created: No  
Turnover created: No  
Reservation API touched: No  
Queue API touched: No  
Turnover API touched: No  
Vue UI touched: No  
Migration changed: No  
SQL schema file created: No  
Production database touched: No  
Seed data inserted: No  
Runtime mock data inserted: No  
Full auth system implemented: No  
OpenAPI dependency added: No  
OpenAPI generated: No  

## 11. Security / Scope Validation

Validated allowed roles:

- `tenant_admin`
- `store_manager`
- `store_staff`

Validated rejected access:

- Forbidden role `customer` returns `FORBIDDEN`.
- Missing permission returns `FORBIDDEN`.
- Store scope mismatch returns `STORE_SCOPE_MISMATCH`.

Auth boundary:

- The test reuses the existing `CurrentActorProvider` / `CurrentActor` placeholder pattern.
- `tenantId`, `actorId`, `actorType`, role, permissions, and Store scope come from the test actor context.
- `storeId` comes from the path.
- No full JWT login system was implemented.
- No Login API or user registration API was implemented.

Tenant mismatch note:

- The current local/test actor placeholder primarily supports Store scope validation through `CurrentActor.canAccessStore`.
- A separate production-grade Tenant mismatch path remains a future production auth concern.

## 12. Idempotency Validation

Start Cleaning:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Completed record with same request hash replays previous response.
- Started record with same request hash returns `IDEMPOTENCY_IN_PROGRESS`.
- Failed record with same request hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key with different request hash returns `IDEMPOTENCY_CONFLICT`.
- New successful request writes `completed`.
- Application failure writes `failed`.

Complete Cleaning:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Completed record with same request hash replays previous response.
- Started record with same request hash returns `IDEMPOTENCY_IN_PROGRESS`.
- Failed record with same request hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key with different request hash returns `IDEMPOTENCY_CONFLICT`.
- New successful request writes `completed`.
- Application failure writes `failed`.

## 13. Tests Executed

Targeted integration command:

```text
mvn -q '-Dtest=CleaningCompleteApiIntegrationTest' test
```

Targeted result:

- Passed.
- `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`

Full verification command:

```text
mvn test
```

Full result:

- `BUILD SUCCESS`
- `Tests run: 164, Failures: 0, Errors: 0, Skipped: 0`

## 14. Test Result

Cleaning Complete API integration validation passed.

Confirmed:

- API -> Controller -> Application Service -> Repository Port -> Persistence Adapter -> JPA Repository -> PostgreSQL schema path works.
- Start Cleaning integration path works.
- Complete Cleaning integration path works.
- Database assertions pass.
- Idempotency integration behavior passes.
- Role, permission, and Store scope validation pass.
- No production code fix was required by this validation round.

## 15. Files Changed

Created:

- `src/test/java/com/rpb/reservation/cleaning/integration/LocalPostgresTestDatabase.java`
- `src/test/java/com/rpb/reservation/cleaning/integration/TestCurrentActorProvider.java`
- `src/test/java/com/rpb/reservation/cleaning/integration/CleaningCompleteIntegrationFixture.java`
- `src/test/java/com/rpb/reservation/cleaning/integration/CleaningCompleteApiIntegrationTest.java`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`

No production source file was changed.

## 16. Open Questions

- Should the local PostgreSQL test helper be moved to a shared test support package in a later test-infrastructure cleanup round?
- Should production auth later enforce Tenant mismatch before idempotency persistence is attempted?
- Should Flyway be upgraded or PostgreSQL test runtime pinned so Flyway can own local integration migration execution for PostgreSQL 17.x?

## 17. Open Conflicts

None.

## 18. Next Step Recommendation

Recommended next round:

```text
Store Staff Minimal UI V1 - Cleaning Complete
```

The next round should stay scoped to the Cleaning UI needed to release an occupied/cleaning table after WalkIn Direct Seating, without expanding into Reservation, Queue, Turnover BI, OpenAPI generation, migration changes, production auth, or complex table maps.
