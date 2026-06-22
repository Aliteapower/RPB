# Reservation Create API Integration Validation Report V1

## 1. Read Documents

- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_API_ERROR_CONTRACT.md`
- `docs/api/RESERVATION_API_IDEMPOTENCY_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_CREATE_APPLICATION_CONTRACT.md`
- `docs/backend/RESERVATION_VERTICAL_SLICE_CHECKLIST.md`
- `docs/database/MIGRATION_VALIDATION_REPORT.md`
- `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`

Previous round confirmation:

- `mvn test` passed.
- 207 tests.
- 0 failures.
- 0 errors.
- `ReservationController` created.
- Create Reservation endpoint implemented.
- CheckIn API implemented: No.
- Queue API implemented: No.
- Seating API implemented: No.
- No-show API implemented: No.
- Cancellation API implemented: No.
- Table assignment API implemented: No.
- Reservation UI implemented: No.
- Migration changed: No.
- Production database touched: No.

## 2. Test Environment

- Workspace: `D:\RPB`
- Java: Java 21
- Test runner: Maven Surefire / JUnit 5
- Spring profile: `test`
- PostgreSQL: local temporary PostgreSQL instance
- PostgreSQL version observed during integration test startup: 17.10
- Auth: test-only `CurrentActorProvider`, following the existing WalkIn/Cleaning integration validation pattern
- Production DB touched: No

## 3. Database Validation Method

- The integration test starts a local temporary PostgreSQL instance with `initdb` and `pg_ctl`.
- The test helper applies `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql` to an empty local database.
- Spring JPA runs with `ddl-auto=validate`.
- Flyway remains disabled in the `test` profile, matching the existing WalkIn/Cleaning integration pattern.
- Fixture data is inserted only into the temporary local PostgreSQL database.
- No production database, production credentials, runtime seed data, production configuration, or migration change was used.

## 4. Endpoint Tested

```text
POST /api/v1/stores/{storeId}/reservations
```

Permission boundary:

```text
reservation.create
```

## 5. Success Cases Tested

- Create reservation with existing Customer.
- Create reservation with `phoneE164`.
- Create no-phone temporary reservation.
- Omit `reservedEndAt` and verify the application derives it from StorePolicy expected dining duration.
- Completed idempotency replay returns `200 OK` with `idempotency.replayed = true`.

Validated successful response fields:

- `reservationId`
- `reservationCode`
- `status = confirmed`
- `partySize`
- `reservedStartAt`
- `reservedEndAt`
- `holdUntilAt`
- `businessDate`
- `customer.id`
- `events = reservation.created, reservation.confirmed`
- `idempotency.status = completed`

## 6. Error Cases Tested

Covered:

- Missing `Idempotency-Key`.
- Invalid `partySize`.
- Invalid time range.
- `reservedStartAt` in the past.
- Invalid `phoneE164`.
- Customer not found.
- Duplicate active reservation.
- Capacity insufficient using the V1 fallback of 50 guests per overlapping Store time window.
- Forbidden role.
- Missing permission.
- Store scope mismatch.
- Idempotency in progress.
- Failed idempotency requires a new key.
- Idempotency hash conflict.

Not separately covered in this API integration test:

- Reservation code conflict, because `reservationCode` is not accepted by the public Create Reservation API request body in V1. This remains covered by application tests.
- Persistence error by artificial repository failure, because replacing production persistence with a failing test bean would stop validating the real API -> persistence -> PostgreSQL path. The real PostgreSQL alignment issue found in this round was fixed and validated.
- Tenant scope mismatch as a separate error, because the current local/test actor provider primarily supports Store scope validation. Cross-tenant hardening remains part of the future production auth boundary.

## 7. Database Assertions

Success assertions:

- `reservations` row is created.
- `reservations.status = confirmed`.
- `reservation_code` is not blank.
- `party_size` is persisted.
- `reserved_start_at` is persisted as UTC.
- `reserved_end_at` is persisted as UTC.
- `hold_until_at` is persisted.
- `business_date` is derived from Store timezone.
- `business_events` contains `reservation.created`.
- `business_events` contains `reservation.confirmed`.
- `state_transition_logs` contains Reservation transition `none -> confirmed`.
- `audit_logs` contains `reservation.create`.
- `idempotency_records.status = completed`.

Boundary assertions:

- `queue_tickets` remains empty.
- `seatings` remains empty.
- `table_locks` remains empty.
- `reservation_preassignments` remains empty.
- `cleanings` remains empty.
- `turnovers` remains empty.
- No Reservation row enters `no_show` or `cancelled` status in this slice.

## 8. Security / Scope Validation

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
- `tenantId`, `actorId`, `actorType`, roles, permissions, and Store scope come from the test actor context.
- `storeId` comes from the path.
- No full JWT login system, Auth API, Login API, or user registration API was implemented.

## 9. Idempotency Validation

Covered:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success creates a completed Store-scoped idempotency record.
- Completed same key + same request returns `200 OK` and `replayed = true`.
- Completed replay does not create a duplicate Reservation.
- Completed replay does not append duplicate Reservation events, transition logs, or audit logs.
- Existing `started` same hash returns `IDEMPOTENCY_IN_PROGRESS` with `409 Conflict`.
- Existing `failed` same hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY` with `409 Conflict`.
- Same key + different hash returns `IDEMPOTENCY_CONFLICT` with `409 Conflict`.

## 10. Integration Fix Applied

The first integration run exposed a real PostgreSQL/schema alignment issue:

```text
ERROR: new row for relation "customers" violates check constraint "ck_customers_type"
```

Root cause:

- `ReservationCreateApplicationService` created temporary Reservation customers with `customer_type = reservation_guest`.
- V001 allows `regular`, `anonymous`, `temporary`, `walk_in_guest`, `boss_friend`, and `special_note`.

Minimal fix:

- Aligned Reservation-created temporary Customer type to `temporary`.
- No migration, schema, seed data, Queue, Seating, TableLock, ReservationPreassignment, UI, or API expansion was introduced.

## 11. Tests Executed

Red:

```text
mvn -q '-Dtest=ReservationCreateApiIntegrationTest' test
```

Initial result:

- Failed with PostgreSQL check constraint `ck_customers_type` on `reservation_guest`.

Green:

```text
mvn -q '-Dtest=ReservationCreateApiIntegrationTest' test
```

Result:

- Passed.
- Tests run: 19.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Full verification:

```text
mvn test
```

Result:

- Passed.
- Tests run: 226.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

## 12. Files Changed

Created test files:

- `src/test/java/com/rpb/reservation/reservation/integration/LocalPostgresTestDatabase.java`
- `src/test/java/com/rpb/reservation/reservation/integration/TestCurrentActorProvider.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateIntegrationFixture.java`
- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`

Minimal production alignment fix:

- `src/main/java/com/rpb/reservation/reservation/application/service/ReservationCreateApplicationService.java`

Report:

- `docs/api/RESERVATION_CREATE_API_INTEGRATION_VALIDATION_REPORT.md`

## 13. Boundary Check

CheckIn created: No

QueueTicket created: No

Seating created: No

No-show created: No

Cancellation created: No

TableLock created: No

ReservationPreassignment created: No

Reservation UI touched: No

Migration changed: No

Production database touched: No

Seed data inserted: No

OpenAPI generated: No

Vue UI changed: No

Full JWT system implemented: No

## 14. Open Questions

- Should `temporary` become the documented canonical customer type for Reservation-created temporary/no-phone customers until the Customer model grows richer reservation-specific fields?
- Should a future production auth round add a first-class tenant mismatch error distinct from Store scope mismatch and Store not found?
- Should a future API version expose client-provided `reservationCode`, or keep code generation server-owned?

## 15. Open Conflicts

No unresolved conflicts.

Scope note:

- The explicit allowed-file list for this validation round focused on tests and the validation report.
- The first PostgreSQL-backed integration run exposed a real production path/schema mismatch in `ReservationCreateApplicationService`.
- Without the one-line alignment from `reservation_guest` to V001-supported `temporary`, the required no-phone temporary Reservation success case could not pass against the validated schema.
- No migration, SQL, seed data, unrelated API, UI, Queue, Seating, TableLock, or ReservationPreassignment change was introduced.

## 16. Next Step Recommendation

Proceed to:

```text
Reservation Create Local Runtime Validation / Minimal UI Contract
```

Recommended scope:

- Validate the Reservation Create endpoint in a local runtime setup or design a minimal Store Staff Reservation Create UI contract.
- Continue to keep CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, TableLock, ReservationPreassignment, OpenAPI generation, migration changes, production auth, and full Reservation calendar/list out of scope.
