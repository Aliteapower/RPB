# Store Staff Cleaning Complete UI Validation Report V1

## 1. Read Documents

- `docs/frontend/CLEANING_COMPLETE_UI_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_API_ERROR_CONTRACT.md`
- `docs/api/CLEANING_API_IDEMPOTENCY_CONTRACT.md`
- `docs/frontend/FRONTEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed prior state:

- Cleaning Complete API integration validation passed.
- Previous backend `mvn test` passed: 164 tests, 0 failures, 0 errors.
- `CleaningCompletePage.vue` exists.
- `cleaningApi.ts` exists.
- Previous `npm run build` passed.

## 2. Frontend Validation Environment

- Workspace: `D:\RPB`
- Node.js: `v22.16.0`
- npm: `10.9.2`
- Frontend stack: Vue 3 / Vite / TypeScript / Pinia / Vue Router
- Vite dev server used for local runtime attempt: `http://127.0.0.1:5173`
- Vite proxy target used for successful API reachability: `http://127.0.0.1:18081`

## 3. Backend Validation Environment

- Java: Temurin OpenJDK `21.0.11`
- Backend command attempted: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Backend local port used: `18081`
- Local auth: `rpb.local-auth.enabled=true`
- Local permissions used: `walkin.direct_seating.create`, `cleaning.start`, `cleaning.complete`
- Database: temporary local PostgreSQL 17.10 under `target/cleaning-ui-validation`
- Migration: `V001__reservation_platform_bootstrap.sql` applied manually to the temporary local database
- Runtime datasource correction used after first attempt: `jdbc:postgresql://127.0.0.1:<port>/postgres?stringtype=unspecified`
- Production database touched: No
- Production data inserted: No

## 4. Route Validation

Validated route:

```text
/stores/:storeId/cleaning
```

Concrete URL attempted:

```text
http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000201/cleaning
```

Result:

- Route returned the Vite app shell with HTTP `200`.
- Page route uses the path `storeId`.
- No Table API, Cleaning query API, Seating query API, table selector, or cleaning list was added.

## 5. Start Cleaning Form Validation

Static/page validation confirmed from `CleaningCompletePage.vue`:

- `seatingId` is the primary Start Cleaning field.
- `reasonCode` is optional.
- `note` is optional.
- Empty `seatingId` is blocked client-side and displays:
  - `SEATING_NOT_FOUND`
  - `cleaning.seating_not_found`
- Submit generates an `Idempotency-Key` with prefix `cleaning:start-cleaning:`.

Runtime validation through Vite proxy:

- Endpoint reached:
  - `POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start`
- Request body:
  - `{"reasonCode":null,"note":null}`
- Body excludes:
  - `tenantId`
  - `tableId`
  - `tableGroupId`
- Header:
  - `Idempotency-Key: ui-cleaning-start-2`
- Result:
  - HTTP `201 Created`
  - `cleaningStatus = cleaning`
  - `tableStatus = cleaning`
  - `idempotency.status = completed`
- Database observation before cleanup:
  - `cleanings.status = cleaning`
  - `dining_tables.status = cleaning`
  - `idempotency_records.status = completed`

## 6. Complete Cleaning Form Validation

Static/page validation confirmed from `CleaningCompletePage.vue`:

- `cleaningId` is the primary Complete Cleaning field.
- `reasonCode` is optional.
- `note` is optional.
- Empty `cleaningId` is blocked client-side and displays:
  - `CLEANING_NOT_FOUND`
  - `cleaning.not_found`
- Submit generates an `Idempotency-Key` with prefix `cleaning:complete-cleaning:`.

Runtime validation through Vite proxy:

- Endpoint reached:
  - `POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete`
- Request body:
  - `{"reasonCode":null,"note":null}`
- Body excludes:
  - `tenantId`
  - `tableId`
  - `tableGroupId`
- Result:
  - Attempted against the `cleaningId` returned by Start Cleaning.
  - The request reached backend application code.
  - Backend logged `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`.
  - The full browser-to-backend Start -> Complete closed loop is blocked by this backend runtime issue.

Notes:

- A first runtime attempt without `stringtype=unspecified` also exposed PostgreSQL JSONB binding sensitivity for `idempotency_records.response_snapshot`.
- The datasource URL was corrected to the same pattern used by backend integration tests.
- After that correction, Start Cleaning succeeded and Complete Cleaning exposed the rollback issue above.

## 7. API Client Validation

Validated client:

```text
src/api/cleaningApi.ts
```

Start endpoint:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
```

Complete endpoint:

```text
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

Confirmed:

- Uses path `storeId`.
- Uses path `seatingId` for Start.
- Uses path `cleaningId` for Complete.
- Sends `Idempotency-Key`.
- Request body is normalized to only:
  - `reasonCode`
  - `note`
- Does not send `tenantId`, `tableId`, or `tableGroupId`.
- Handles API error envelope and network failure.

## 8. Idempotency-Key Validation

Confirmed:

- Page generates a key before valid Start submit.
- Page generates a key before valid Complete submit.
- API client sends `Idempotency-Key`.
- Start Cleaning persisted a completed idempotency record.
- Complete Cleaning was attempted with a separate key, but backend rollback prevented completed persistence.

## 9. Success Display Result

Start Cleaning success display is supported by the page and was backed by a real successful backend response.

Fields rendered by the page:

- `cleaningId`
- `seatingId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency.status`
- `idempotency.replayed`

Observed successful Start result:

- `cleaningStatus = cleaning`
- `tableStatus = cleaning`
- `idempotency.status = completed`

Complete Cleaning success display is implemented in the page, but real runtime success was not reached because of the backend rollback blocker.

## 10. Error Display Result

Page displays:

- `error.code`
- `error.messageKey`

Static validation:

- Missing `seatingId` displays `SEATING_NOT_FOUND` / `cleaning.seating_not_found`.
- Missing `cleaningId` displays `CLEANING_NOT_FOUND` / `cleaning.not_found`.

Backend error behavior:

- Invalid and forbidden backend cases are supported by the API contract/client shape.
- Complete runtime blocker currently returns a backend exception path instead of a clean API error envelope in the live Start -> Complete scenario.

No complete i18n system was introduced.

## 11. Mobile-first Validation

Confirmed from `CleaningCompletePage.vue`:

- Single-column layout.
- Start Cleaning and Complete Cleaning forms are clearly separated.
- `seatingId` and `cleaningId` are primary inputs.
- `reasonCode` and `note` are inside collapsed `details` sections.
- Submit buttons are large and visible.
- Success and error panels are compact.
- No complex table map.
- No drag-and-drop layout.

## 12. Browser-to-Backend Validation Result

Attempted: Yes.

Validated:

- Vite dev server route can serve the Cleaning page.
- Vite proxy reaches the real local backend.
- Start Cleaning UI/API chain reaches:

```text
CleaningCompletePage.vue intent
-> cleaningApi.ts
-> Vite proxy
-> CleaningController
-> CleaningApplicationService
-> Persistence
-> PostgreSQL
```

Result:

- Start Cleaning: Passed through real local backend.
- Complete Cleaning: Blocked by backend runtime rollback when completing the Cleaning created by Start Cleaning.

Blocker:

```text
UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

Impact:

- The full UI closed loop cannot yet be marked passed.
- Existing backend tests still pass, but they do not currently cover Start Cleaning followed immediately by Complete Cleaning on the same Cleaning record in one browser-to-backend runtime flow.

## 13. Commands Executed

- `npm run build`
  - Result: Passed
- `npm run dev -- --host 127.0.0.1 --port 5173`
  - Result: Started for local runtime validation, then stopped
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`
  - Result: Started for local runtime validation, then stopped
- `mvn test`
  - Initial result: Failed due stale boundary tests that did not allow the already approved `CleaningCompletePage.vue`.
  - Final result after boundary allowlist update: Passed, 164 tests, 0 failures, 0 errors.

## 14. Build Result

Final `npm run build` result:

- Passed.
- `vue-tsc --noEmit`: passed.
- `vite build`: passed.

The generated `dist/` artifact was removed after validation.

## 15. Boundary Check

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
Cleaning query API created: No  
Seating query API created: No  
Customer search API created: No  
Backend API changed: No  
Migration changed: No  
Database structure changed: No  
Production database touched: No  
Production seed data inserted: No  
Full Auth system implemented: No  
Full store switcher implemented: No  

## 16. Files Changed

Created:

- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`

Updated for local validation support:

- `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`

Updated stale boundary test allowlists to recognize the already approved Cleaning UI:

- `src/test/java/com/rpb/reservation/cleaning/api/CleaningControllerTest.java`
- `src/test/java/com/rpb/reservation/cleaning/integration/CleaningCompleteApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingControllerTest.java`

No frontend source file was changed in this validation round.

## 17. Open Questions

- Should the next backend fix round add an integration test for the real closed loop: WalkIn Direct Seating -> Start Cleaning -> Complete Cleaning on the same resource?
- Should local runtime documentation explicitly require `SPRING_DATASOURCE_URL=jdbc:postgresql://.../postgres?stringtype=unspecified` until JSONB fields are mapped with a stronger Hibernate JSON type?

## 18. Open Conflicts

- Browser-to-backend validation found a backend closed-loop blocker: Complete Cleaning fails after Start Cleaning creates the Cleaning record in the same local runtime flow.
- Existing backend integration tests pass because Start and Complete are tested as separate fixture-backed scenarios, not as one continuous browser-to-backend flow.

## 19. Next Step Recommendation

Recommended next round:

```text
Cleaning Complete Runtime Closed Loop Fix
```

Suggested scope:

- Add a backend integration test that performs Start Cleaning and then Complete Cleaning using the returned `cleaningId`.
- Fix the persistence/domain version or transaction behavior that causes the runtime rollback.
- Re-run `mvn test`.
- Re-run Store Staff Cleaning Complete UI Validation.
- Do not add Reservation, Queue, Turnover UI, complex table map, new migration, or production auth.

## 20. Runtime Fix Revalidation Update

Status after `Cleaning Complete Runtime Closed Loop Fix`:

- Previous blocker: Resolved.
- Root cause: `DiningTablePersistenceAdapter` lost the current JPA optimistic-lock `version` when saving an existing table after Start Cleaning.
- Fix: existing scoped `DiningTableEntity` metadata and `version` are now preserved before saving the updated table status.

Revalidated local runtime flow:

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
  - `business_events` include `cleaning.started`, `table.cleaning`, `cleaning.completed`, and `table.available`
  - `state_transition_logs = 4`
  - `audit_logs = 2`
  - Reservation / QueueTicket / Turnover counts remain `0,0,0`

Commands re-run:

- `mvn -q '-Dtest=CleaningCompleteApiIntegrationTest' test`
  - Passed, 29 tests, 0 failures, 0 errors
- `mvn test`
  - Passed, 165 tests, 0 failures, 0 errors
- `npm run build`
  - Passed

Updated result:

- Full Cleaning Complete UI-to-backend closed loop: Passed.
- Production database touched: No.
- Migration changed: No.
- Reservation / Queue / Turnover UI expanded: No.
