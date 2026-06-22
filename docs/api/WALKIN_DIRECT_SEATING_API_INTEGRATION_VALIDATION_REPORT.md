# WalkIn Direct Seating API Integration Validation Report

## 1. Read Documents

- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`

Previous round result confirmed:

- `mvn test` passed
- 70 tests
- 0 failures
- 0 errors
- Reservation API implemented: No
- Queue API implemented: No
- Cleaning API implemented: No
- Turnover API implemented: No
- Vue UI implemented: No
- Migration changed: No

## 2. Test Environment

- OS/runtime: local Windows workspace
- Java: Java 21
- Test runner: Maven Surefire / JUnit 5
- Spring profile: `test`
- PostgreSQL validation: local temporary PostgreSQL instance started for integration tests
- PostgreSQL version: 17.10
- Database lifecycle: created under `target/test-postgres`, migrated, used by tests, then stopped and removed
- Production database touched: No

## 3. Database Validation Method

- The integration test starts a local PostgreSQL instance with `initdb` and `pg_ctl`.
- The test helper applies `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql` to the empty local database before Spring context startup.
- Spring JPA runs with `ddl-auto=validate`.
- Flyway is disabled only in the `test` profile because the bundled Flyway version reports PostgreSQL 17.10 as unsupported in this environment.
- No production database, production credentials, seed data, or runtime mock data are used.

## 4. Endpoint Tested

- Method: `POST`
- Path: `/api/v1/stores/{storeId}/walk-ins/direct-seating`
- Permission boundary: `walkin.direct_seating.create`

## 5. Success Cases Tested

- Success with no-phone walk-in customer
- Success with specified dining table
- Success with existing table group
- Success with auto-selected dining table
- Completed idempotency replay returns `200 OK` with `replayed=true`

## 6. Error Cases Tested

- Missing `Idempotency-Key`
- Invalid `partySize`
- Invalid `phoneE164`
- `tableId` and `tableGroupId` both present
- Inactive table
- Active table lock conflict
- Capacity insufficient
- Invalid table group
- Override reason missing for non-recommended resource
- Idempotency in progress
- Failed idempotency requires a new key
- Idempotency hash conflict
- Forbidden role
- Store scope mismatch

## 7. Database Assertions

- `walk_ins` record is created for successful direct seating.
- `seatings` record is created with source WalkIn.
- `seating_resources` record is created with exactly one target: table or table group.
- `dining_tables.status` becomes `occupied` for direct table seating.
- `business_events` records are created.
- `state_transition_logs` records are created.
- `audit_logs` record is created.
- `idempotency_records` is created and updated to `completed` for success.
- Application-level failures create failed idempotency records and audit records without creating WalkIn or Seating rows.

## 8. Boundary Assertions

- `reservations` remains empty.
- `queue_tickets` remains empty.
- `cleanings` remains empty.
- `turnovers` remains empty.
- No Reservation API was added.
- No Queue API was added.
- No Cleaning API was added.
- No Turnover API was added.
- No Vue UI was added.
- No migration was changed.

## 9. Security / Scope Validation

- Test-only `CurrentActorProvider` supplies tenant id, actor id, actor type, roles, permissions, and store scope.
- Allowed role with permission can call the endpoint.
- Forbidden role returns `FORBIDDEN`.
- Store scope mismatch returns `STORE_SCOPE_MISMATCH`.
- Full JWT login system was not implemented.
- Auth API was not implemented.

## 10. Idempotency Validation

- Missing key returns `MISSING_IDEMPOTENCY_KEY` before application service mutation.
- Completed record with same request hash replays prior response.
- Started record with same request hash returns `IDEMPOTENCY_IN_PROGRESS`.
- Failed record with same request hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key with different request hash returns `IDEMPOTENCY_CONFLICT`.
- Successful new request writes `started` then `completed`.
- Application failure writes `failed`.

## 11. Integration Fixes Applied

The integration test exposed real PostgreSQL/JPA alignment issues. Minimal fixes were applied so the vertical slice can run through the real persistence path:

- New versioned entities with assigned UUIDs were persisted with `EntityManager.persist` and `version=null` on the new-entity path for Customer, WalkIn, Seating, and TableLock. This avoids Spring Data treating new objects with `version=0` as detached existing entities.
- WalkIn Direct Seating audit, business event, and state transition source values were aligned with V001 check constraints by writing the actor source (`staff`, `customer`, `integration`, `system`) instead of `application_service`.
- Test fixture expected auto-selection was aligned with the current candidate ordering rule: `capacityMax asc, tableCode asc`.

## 12. Tests Executed

- `mvn -q '-Dtest=WalkInDirectSeatingApiIntegrationTest#seatsNoPhoneWalkInThroughFullApiToPostgresPath' test`
- `mvn -q '-Dtest=WalkInDirectSeatingApiIntegrationTest' test`
- `mvn test`

## 13. Test Result

- `mvn test`: Passed
- Tests run: 89
- Failures: 0
- Errors: 0
- Skipped: 0

## 14. Files Changed

- `src/main/java/com/rpb/reservation/customer/persistence/adapter/CustomerPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/walkin/persistence/adapter/WalkInPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/seating/persistence/adapter/SeatingPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/table/persistence/adapter/TableLockPersistenceAdapter.java`
- `src/main/java/com/rpb/reservation/walkin/application/service/WalkInDirectSeatingApplicationService.java`
- `src/test/java/com/rpb/reservation/walkin/integration/LocalPostgresTestDatabase.java`
- `src/test/java/com/rpb/reservation/walkin/integration/TestCurrentActorProvider.java`
- `src/test/java/com/rpb/reservation/walkin/integration/WalkInDirectSeatingIntegrationFixture.java`
- `src/test/java/com/rpb/reservation/walkin/integration/WalkInDirectSeatingApiIntegrationTest.java`
- `src/test/resources/application-test.yml`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`

## 15. Boundary Check

- Vue UI implemented: No
- Reservation API implemented: No
- Queue API implemented: No
- Cleaning API implemented: No
- Turnover API implemented: No
- Migration changed: No
- Production database touched: No
- Seed data inserted: No
- Runtime mock data inserted: No
- Full Auth system implemented: No
- Auth API implemented: No
- Login API implemented: No
- User registration API implemented: No

## 16. Open Questions

- Should Flyway be upgraded or PostgreSQL test runtime pinned so Flyway can own local integration migration execution for PostgreSQL 17.x?
- Should JSONB mapping be formalized with Hibernate JSON typing instead of relying on PostgreSQL JDBC `stringtype=unspecified` in the test JDBC URL?
- Should audit/event `source` retain both actor source and internal producer as separate fields in a future schema revision?

## 17. Open Conflicts

- None.

## 18. Next Step Recommendation

- Proceed to the next backend slice only after accepting the integration fixes from this validation round.
- Recommended next slice: Reservation or Queue should begin with contract/design first, then application implementation, then API/integration validation using the same PostgreSQL-backed pattern.
