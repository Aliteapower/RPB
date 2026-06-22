# Store Staff Closed Loop Demo Readiness Review V1

## 1. Read Documents

- `docs/frontend/STORE_STAFF_OPERATIONAL_HANDOFF.md`
- `docs/frontend/STORE_STAFF_OPERATIONAL_HANDOFF_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_UI_POLISH_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_RUNTIME_FIX_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed prior results:

- Staff home route exists: `/stores/:storeId/staff`.
- WalkIn Direct Seating route exists.
- Cleaning route exists.
- Store staff closed loop runtime smoke passed.
- Previous backend `mvn test` passed: 165 tests, 0 failures, 0 errors.
- Reservation UI/API created: No.
- Queue UI/API created: No.
- Turnover UI/API created: No.
- Migration changed: No.
- Production database touched: No.

## 2. Demo Scope

Review scope:

```text
Staff Home
-> WalkIn Direct Seating
-> Start Cleaning
-> Complete Cleaning
-> Table available
```

This review does not add a feature. It checks demo readiness, local handoff clarity, known limitations, and current boundary compliance.

Non-scope:

- Reservation
- Queue
- Turnover BI
- complex table map
- new backend API
- new migration
- frontend test framework
- production auth

## 3. Local Run Commands

Operational handoff includes local runtime commands:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
npm run dev
```

Validation commands:

```text
npm run build
mvn test
```

Runtime environment documented:

- frontend URL: `http://127.0.0.1:5173`
- backend URL: `http://127.0.0.1:8080`
- Vite proxy: `/api -> VITE_API_PROXY_TARGET or http://127.0.0.1:8080`
- local/test actor placeholder only
- local PostgreSQL only
- production database touched: No

## 4. Staff Home Review

Route:

```text
/stores/:storeId/staff
```

Verified in `src/router/index.ts`:

- root `/` redirects to staff home.
- fallback route redirects to staff home.
- staff home component is `StoreStaffHomePage.vue`.

Allowed links only:

- WalkIn Direct Seating
- Cleaning Complete

Verified current `src/pages` Vue files:

- `StoreStaffHomePage.vue`
- `WalkInDirectSeatingPage.vue`
- `CleaningCompletePage.vue`

Forbidden entries not found in `src/pages`, `src/router`, or `package.json`:

- Reservation
- Queue
- Turnover
- POS
- Payment
- Marketing
- Member
- TableMap
- Drag
- Vitest
- Cypress
- Playwright

## 5. WalkIn Demo Review

Demo path:

1. Open staff home.
2. Enter WalkIn Direct Seating.
3. Input `partySize`.
4. Submit.

Current UI supports:

- `partySize` as the primary input.
- optional `customerName`.
- optional `customerNickname`.
- optional `phoneE164`.
- optional `tableId`.
- optional `tableGroupId`.
- generated `Idempotency-Key`.
- success display with `walkInId`.
- success display with `seatingId`.
- success display with `resource`.
- success display with `status`.
- success display with `idempotency`.
- `开始清台` handoff link when `seatingId` exists.

Referenced runtime smoke observed:

- `status = occupied`.
- `idempotency = completed`.
- `开始清台` link routed to `/stores/:storeId/cleaning?seatingId=<seatingId>`.

## 6. Cleaning Demo Review

Demo path:

1. Click `开始清台`.
2. Open Cleaning page.
3. Confirm `seatingId` is loaded from route query.
4. Click Start Cleaning.
5. Confirm Start Cleaning result.
6. Confirm returned `cleaningId` fills Complete Cleaning.
7. Click Complete Cleaning.
8. Confirm final table availability.

Current UI supports:

- route query `seatingId` auto-fill.
- Start Cleaning request with generated `Idempotency-Key`.
- Start Cleaning success display with `cleaningStatus`.
- Start Cleaning success display with `tableStatus`.
- returned `cleaningId` auto-filled into Complete Cleaning form.
- Complete Cleaning request with generated `Idempotency-Key`.
- Complete Cleaning success display with `cleaningStatus`.
- Complete Cleaning success display with `tableStatus`.
- final title `桌位已释放` when `cleaningStatus=released` and `tableStatus=available`.

Referenced runtime smoke observed:

- Start Cleaning: `cleaningStatus = cleaning`.
- Start Cleaning: `tableStatus = cleaning`.
- Complete Cleaning: `cleaningStatus = released`.
- Complete Cleaning: `tableStatus = available`.
- Complete Cleaning displayed `桌位已释放`.

## 7. Error Display Review

Error display is demo-ready for code/key based diagnostics.

Referenced smoke validation:

- invalid `phoneE164 = not-a-phone`
- displayed `error.code = INVALID_PHONE_E164`
- displayed `error.messageKey = walkin.direct_seating.invalid_phone_e164`

Current page-level validation also supports:

- invalid `partySize`
- invalid `phoneE164`
- both `tableId` and `tableGroupId`
- missing `seatingId`
- missing `cleaningId`

The UI still displays `error.code` and `error.messageKey`. A full i18n translation layer remains out of scope.

## 8. Database Assertions

This round did not rerun a database-backed runtime smoke. Database readiness is referenced from:

- `docs/frontend/STORE_STAFF_CLOSED_LOOP_RUNTIME_SMOKE_REPORT.md`
- `docs/api/CLEANING_COMPLETE_RUNTIME_FIX_REPORT.md`

Referenced successful closed loop assertions:

- `walk_ins` created.
- `seatings` created.
- `seating_resources` created.
- `cleanings.status = released`.
- `dining_tables.status = available`.
- `business_events` created.
- `state_transition_logs` created.
- `audit_logs` created.
- `idempotency_records.status = completed`.
- `reservations = 0`.
- `queue_tickets = 0`.
- `turnovers = 0`.

Production database touched in this review: No.

## 9. Known Limitations

Confirmed in operational handoff documentation:

- no real JWT login yet
- local/test actor placeholder only
- no store switcher yet
- no table selector yet
- no Reservation UI
- no Queue UI
- no Turnover BI
- `seatingId` and `cleaningId` still visible for fallback
- error display uses `code` and `messageKey`
- no frontend test framework yet

## 10. Commands Executed

Frontend build:

```text
npm run build
```

Result:

- Passed.
- `vue-tsc --noEmit`: passed.
- `vite build`: passed.
- Modules transformed: 41.

Backend tests:

```text
mvn test
```

Result:

- Not run in this demo readiness review.
- Reason: no backend code, API, migration, SQL, or database structure was changed.
- Previous runtime smoke verification remains: 165 tests, 0 failures, 0 errors.

Runtime demo:

- Not rerun in this review.
- Reason: no frontend behavior, backend behavior, API, migration, or database schema change was made beyond documentation consistency.
- Database-backed closed-loop evidence is referenced from the previous runtime smoke report.

Build artifact cleanup:

- `dist/` removed after build validation.

## 11. Build / Test Result

- `npm run build`: Passed.
- `mvn test`: Not run, previous result referenced.
- Runtime demo: Not rerun, previous runtime smoke referenced.

## 12. Boundary Check

Reservation UI created: No  
Queue UI created: No  
Turnover UI created: No  
Complex table map created: No  
Reservation API created: No  
Queue API created: No  
Turnover API created: No  
Backend API changed: No  
Migration changed: No  
SQL created: No  
Database structure changed: No  
Production database touched: No  
Production data inserted: No  
Frontend test framework added: No  
Full login system implemented: No  
Full store switcher implemented: No  

## 13. Open Questions

- Should the next Reservation round keep the first slice limited to create Reservation only?
- Should a later approved frontend testing round introduce Vitest for route and form checks?
- Should a later approved Table API round replace manual `tableId`, `seatingId`, and `cleaningId` fallback inputs with selectors?

## 14. Next Step Recommendation

Recommended next round:

```text
Reservation Minimum Vertical Slice Contract
```

Scope recommendation:

- First Reservation round should only design create Reservation.
- Do not include CheckIn, Queue, Seating, No-show, Turnover BI, complex table map, migration changes, or production auth in that first Reservation contract round.
