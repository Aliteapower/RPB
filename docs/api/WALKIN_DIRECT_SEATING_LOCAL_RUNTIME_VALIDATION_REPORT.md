# WalkIn Direct Seating Local Runtime Validation Report

## 1. Read Documents

- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/frontend/FRONTEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_PLAN.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_APPLICATION_IMPLEMENTATION_REPORT.md`
- `docs/backend/WALKIN_DIRECT_SEATING_PERSISTENCE_IMPLEMENTATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed prior state:

- `npm run build`: Passed.
- UI route, form validation, idempotency key generation, and body shape were previously validated.
- Backend dedicated integration test passed.
- Previous full `mvn test` failure was caused by an old boundary test rejecting any `.vue` file.
- No production database was touched.
- No migration was changed.

## 2. Old Boundary Test Fix

The old API boundary test treated all `.vue` files as forbidden. That rule became stale after the approved frontend structure setup round.

Updated behavior:

- Allows exactly the approved minimal WalkIn UI files:
  - `src/App.vue`
  - `src/pages/WalkInDirectSeatingPage.vue`
- Still forbids UI artifacts for:
  - Reservation
  - Queue
  - Cleaning
  - Turnover
  - POS
  - Payment
  - Marketing
  - Membership
  - complex table map
  - drag-and-drop table layout
- Still forbids non-WalkIn API controllers for Reservation / Queue / Cleaning / Turnover.

## 3. Approved UI Allowlist

Approved Vue files:

```text
src/App.vue
src/pages/WalkInDirectSeatingPage.vue
```

The boundary test now checks the allowlist directly instead of rejecting Vue globally.

## 4. Forbidden UI Checks

Forbidden filename signals remain checked:

- `reservation`
- `queue`
- `cleaning`
- `turnover`
- `payment`
- `pos`
- `marketing`
- `member`
- `tablemap`
- `drag`

No forbidden UI file was found.

## 5. Local Runtime Auth Approach

Added a local/test-only runtime actor boundary:

- `LocalAuthProperties`
- `LocalRuntimeCurrentActorProvider`
- `LocalRuntimeSecurityConfiguration`

Activation requirements:

- Spring profile must be `local` or `test`.
- `rpb.local-auth.enabled=true` must be set.

Default configuration:

- `rpb.local-auth.enabled=false` in `src/main/resources/application.yml`
- `rpb.local-auth.enabled=false` in `src/test/resources/application-test.yml`

Runtime actor sources:

- Configured local/test properties for tenant, actor, role, permission, and store scope.
- Optional test headers can override those values for tests:
  - `X-Test-Tenant-Id`
  - `X-Test-Actor-Id`
  - `X-Test-Actor-Type`
  - `X-Test-Actor-Role`
  - `X-Test-Permissions`
  - `X-Test-Store-Ids`

Permission used:

```text
walkin.direct_seating.create
```

This is not production authentication.

## 6. Production Auth Status

- Full production auth implemented: No
- Full JWT login implemented: No
- User registration implemented: No
- Auth API implemented: No
- Production hardcoded user added: No

The local runtime support only supplies a safe local/test actor boundary so the already implemented vertical slice can be validated through a real browser and backend process.

## 7. Vite Proxy Status

Vite proxy remains:

```text
/api -> http://127.0.0.1:8080
```

Override:

```text
VITE_API_PROXY_TARGET
```

Browser validation used the Vite proxy from:

```text
http://127.0.0.1:5173
```

to:

```text
http://127.0.0.1:8080
```

## 8. Runtime Validation Environment

Frontend:

- `npm run dev -- --host 127.0.0.1 --port 5173`
- URL: `http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000101/walk-ins/direct-seating`
- Mobile viewport: `390 x 844`

Backend:

- `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- URL: `http://127.0.0.1:8080`
- Local auth enabled through environment variables.
- Flyway disabled for the runtime run after V001 was applied to the temporary database.
- Hibernate schema validation enabled.

Database:

- Temporary local PostgreSQL
- Host: `127.0.0.1`
- Port: `54306`
- Database: `postgres`
- Production database touched: No

Temporary validation fixture:

- Tenant: `10000000-0000-0000-0000-000000000101`
- Store: `20000000-0000-0000-0000-000000000101`
- Tables:
  - `A1`, capacity `1-4`, initially available
  - `S1`, capacity `1-2`, initially available

## 9. Browser-to-Backend Validation Result

Endpoint:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

Validated browser request:

- `partySize`: `2`
- `customerName`: `Guest Runtime`
- `phoneE164`: empty
- `tableId`: empty
- `tableGroupId`: empty
- `tenantId` in body: No
- `Idempotency-Key`: generated and sent

Observed success response in browser:

- `walkInId`: `ef1086ae-c40a-4d67-9188-4e930ffcd3d3`
- `seatingId`: `a5442494-ba1c-450b-b426-e7656bddcc11`
- `resource`: `TABLE 40000000-0000-0000-0000-000000000106`
- `status`: `occupied`
- `idempotency`: `completed`

Observed API error display:

- Empty customer identity attempt returned:
  - `INVALID_CUSTOMER_IDENTITY`
  - `walkin.direct_seating.invalid_customer_identity`
- Invalid phone was blocked and displayed:
  - `INVALID_PHONE_E164`
  - `walkin.direct_seating.invalid_phone_e164`
- Both `tableId` and `tableGroupId` present was blocked and displayed:
  - `SEATING_RESOURCE_INVALID`
  - `walkin.direct_seating.resource_invalid`

## 10. Database Assertions

After the successful browser submission:

- WalkIn created: Yes
- Seating created: Yes
- SeatingResource created: Yes
- Table status changed to `occupied`: Yes
- BusinessEvent created: Yes
- StateTransitionLog created: Yes
- AuditLog created: Yes
- IdempotencyRecord completed: Yes

Counts:

```text
walk_ins              1
seatings              1
seating_resources     1
business_events       4
state_transition_logs 4
audit_logs            2
idempotency_records   2
reservations          0
queue_tickets         0
cleanings             0
turnovers             0
```

Seating source:

- `reservation_id`: null
- `queue_ticket_id`: null
- `walk_in_id`: populated

Seating resource:

- `resource_type`: `dining_table`
- `table_id`: populated
- `table_group_id`: null

Table status:

- `S1`: `occupied`

## 11. Commands Executed

- `mvn -q '-Dtest=WalkInDirectSeatingControllerTest#noOtherVerticalSliceApiOrUiArtifactsAreCreated,LocalRuntimeCurrentActorProviderTest,LocalRuntimeWalkInDirectSeatingSecurityTest' test`
  - Result: Passed
- `mvn -q '-Dtest=LocalRuntimeCurrentActorProviderTest,LocalRuntimeWalkInDirectSeatingSecurityTest' test`
  - Result: Passed
- `mvn test`
  - Result: Passed, 92 tests, 0 failures, 0 errors
- `npm run build`
  - Result: Passed
- `npm run dev -- --host 127.0.0.1 --port 5173`
  - Result: Started for browser validation
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`
  - Result: Started for browser validation

## 12. npm Build Result

`npm run build` passed:

- TypeScript check: passed
- Vite production build: passed

## 13. mvn Test Result

`mvn test` passed:

```text
Tests run: 92, Failures: 0, Errors: 0, Skipped: 0
```

## 14. Boundary Check

- Reservation UI created: No
- Queue UI created: No
- Cleaning UI created: No
- Turnover UI created: No
- Complex table map created: No
- Drag-and-drop table layout created: No
- Reservation API implemented: No
- Queue API implemented: No
- Cleaning API implemented: No
- Turnover API implemented: No
- Backend Migration changed: No
- SQL schema changed: No
- Production database touched: No
- Seed data inserted: No
- Full Auth system implemented: No
- Login API implemented: No
- User registration API implemented: No

## 15. Open Questions

- Should the future production auth round use JWT claims or a backend session principal as the source of tenant/store/role scope?
- Should Product Owner allow completely anonymous WalkIn direct seating, or should V1 continue requiring at least one customer identity signal such as `customerName`?
- Should the future Table API replace manual `tableId` / `tableGroupId` entry with a mobile table selector?

## 16. Open Conflicts

None.

## 17. Next Step Recommendation

Recommended next round:

```text
WalkIn Direct Seating Production Auth Contract Design
```

Keep the next round focused on production auth/scope/RBAC contract. Do not expand into Reservation, Queue, Cleaning, Turnover, complex table map, migrations, or database schema changes.
