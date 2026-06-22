# Reservation Create Local Runtime Validation Report V1

## 1. Read Documents

- `docs/api/RESERVATION_CREATE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/RESERVATION_CREATE_API_CONTRACT.md`
- `docs/backend/RESERVATION_CREATE_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/RESERVATION_CREATE_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/skills/reservation-system/SKILL.md`

Confirmed previous result:

- Reservation Create API integration validation passed.
- `mvn test` passed: 226 tests, 0 failures, 0 errors.
- Reservation is created with status `confirmed`.
- No QueueTicket, Seating, TableLock, or ReservationPreassignment is created.
- Migration changed: No.
- Production database touched: No.

## 2. Runtime Environment

- Workspace: `D:\RPB`
- Java: Java 21
- Spring profile: `test`
- Runtime validation method: SpringBootTest + MockMvc + local temporary PostgreSQL
- PostgreSQL: local temporary PostgreSQL instance started by the test helper
- PostgreSQL version observed through Hibernate startup: 17.10
- Schema: `src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql` applied to an empty temporary local database
- JPA schema validation: enabled through the test profile
- Auth: test-only `CurrentActorProvider`
- Production DB touched: No
- Local HTTP/curl smoke: Not executed in this round; the approved SpringBootTest integration path was used because Reservation UI does not exist yet.

## 3. Endpoint Validated

```text
POST /api/v1/stores/{storeId}/reservations
```

Permission:

```text
reservation.create
```

## 4. Success Cases

Covered by `ReservationCreateApiIntegrationTest`:

- Create reservation with an existing Customer.
- Create reservation with a `phoneE164` Customer.
- Create no-phone temporary reservation.
- Omit `reservedEndAt` and derive it from StorePolicy expected dining duration.
- Return generated `reservationCode`.
- Return `holdUntilAt`.
- Return `status = confirmed`.
- Complete idempotency for fresh success.
- Replay completed idempotency with `200 OK` and `idempotency.replayed = true`.

Additional V1 customer-type assertion added in this round:

- Reservation-created no-phone temporary Customer is persisted with `customer_type = temporary`.
- `reservation_guest` is not used in the Reservation Create production path.

## 5. Error Cases

Covered:

- Missing `Idempotency-Key`.
- Invalid `partySize`.
- Invalid time range.
- `reservedStartAt` in the past.
- Invalid `phoneE164`.
- Customer not found.
- Duplicate active Reservation.
- Capacity insufficient using the V1 fallback of 50 guests per overlapping Store time window.
- Forbidden role.
- Missing permission.
- Store scope mismatch.
- Idempotency in progress.
- Failed idempotency requires a new key.
- Idempotency hash conflict.

Not executed as local HTTP/curl smoke:

- Network-level runtime smoke. This round used the allowed SpringBootTest integration path instead.

## 6. Database Assertions

Success assertions:

- `reservations` row is created.
- `reservations.status = confirmed`.
- `reservation_code` is not blank.
- `party_size` is persisted.
- `reserved_start_at` is persisted.
- `reserved_end_at` is persisted.
- `hold_until_at` is persisted.
- `business_date` is persisted.
- No-phone temporary customer is persisted with `customer_type = temporary`.
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

## 7. Boundary Assertions

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
Full JWT/Login system implemented: No  

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

- Tenant id, actor id, actor type, role, permissions, and Store scope come from the test-only actor provider.
- Path `storeId` is still the Store operation boundary.
- Full production JWT, Login API, user registration API, and unified permission evaluator remain deferred.

## 9. Idempotency Validation

Covered:

- Missing key returns `MISSING_IDEMPOTENCY_KEY`.
- Fresh success creates a completed Store-scoped idempotency record.
- Completed same key + same request hash replays previous success with `idempotency.replayed = true`.
- Completed replay does not create a duplicate Reservation.
- Completed replay does not append duplicate events, state transition logs, or audit logs.
- Existing `started` same hash returns `IDEMPOTENCY_IN_PROGRESS`.
- Existing `failed` same hash returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Same key + different hash returns `IDEMPOTENCY_CONFLICT`.

## 10. Commands Executed

Targeted local runtime validation command:

```text
mvn -q '-Dtest=ReservationCreateApiIntegrationTest' test
```

Result:

- Passed.
- Tests run: 19.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Full regression command:

```text
mvn test
```

Result:

- Passed.
- Tests run: 226.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

Local backend command:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Result:

- Not executed in this round.
- The approved SpringBootTest integration path was used instead of curl/HTTP smoke.

HTTP smoke:

- Not executed in this round.
- No Reservation UI exists yet.
- The endpoint was validated through MockMvc against the Spring context and real PostgreSQL persistence.

## 11. Test Result

Reservation Create local runtime validation passed through:

```text
ReservationController
-> ReservationCreateApplicationService
-> Repository Port
-> Persistence Adapter
-> JPA Repository
-> PostgreSQL V001 schema
```

Confirmed:

- API to Application to Persistence to PostgreSQL works.
- Database assertions pass.
- Idempotency behavior passes.
- Role, permission, and Store scope validation pass.
- No CheckIn, Queue, Seating, No-show, Cancellation, TableLock, ReservationPreassignment, Reservation UI, migration change, production DB touch, seed data, or OpenAPI generation was introduced.

## 12. Files Changed

Created:

- `docs/api/RESERVATION_CREATE_LOCAL_RUNTIME_VALIDATION_REPORT.md`

Updated test files:

- `src/test/java/com/rpb/reservation/reservation/integration/ReservationCreateApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/reservation/application/ReservationCreateApplicationServiceTest.java`

Test-only changes:

- Added an integration assertion that no-phone Reservation-created Customer uses `customer_type = temporary`.
- Aligned an existing unit-test fixture customer type from `reservation_guest` to `temporary`.

Production source code changed in this round: No.

## 13. Open Questions

- Should a future Reservation UI contract start with a small store-staff form before adding list/calendar views?
- Should the local PostgreSQL test helper be moved to shared test support after Reservation, WalkIn, and Cleaning now use the same pattern?
- Should future Customer model work persist Reservation customer name/nickname as first-class fields instead of only projecting them in API responses?

## 14. Open Conflicts

None.

## 15. Next Step Recommendation

Proceed to:

```text
Store Staff Reservation Create UI Contract
```

Recommended scope:

- Design only the minimal Store Staff Create Reservation UI contract.
- Keep CheckIn, Queue, Seating, No-show, Cancellation, Table assignment, TableLock, ReservationPreassignment, OpenAPI generation, migration changes, production auth, and full Reservation calendar/list out of scope.
