# Store Staff Closed Loop UI Polish Report V1

## 1. Read Documents

- `docs/frontend/FRONTEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_REPORT.md`
- `docs/frontend/CLEANING_COMPLETE_UI_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_LOCAL_RUNTIME_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_RUNTIME_FIX_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed prior state:

- WalkIn Direct Seating API was implemented and integration validated.
- Cleaning Complete API was implemented and integration validated.
- Cleaning runtime closed loop fix passed.
- Previous `mvn test` passed with 165 tests, 0 failures, 0 errors.
- Previous `npm run build` passed.
- Previous browser-to-backend validation passed after the runtime fix.
- Migration changed: No.
- Production database touched: No.

## 2. Updated Files

- `src/pages/WalkInDirectSeatingPage.vue`
- `src/pages/CleaningCompletePage.vue`
- `docs/frontend/STORE_STAFF_CLOSED_LOOP_UI_POLISH_REPORT.md`

No API client, router, backend, migration, SQL, or database structure file was changed.

## 3. WalkIn Page Polish

Updated `WalkInDirectSeatingPage.vue`:

- Success result still displays:
  - `walkInId`
  - `seatingId`
  - `resource`
  - `status`
  - `idempotency`
- Success result now exposes a clear `开始清台` operation when `seatingId` exists.
- The operation routes to:

```text
/stores/:storeId/cleaning?seatingId=<seatingId>
```

- If the API response does not contain `seatingId`, the handoff action is disabled and shows a technical hint:

```text
seatingId missing; cleaning handoff disabled.
```

Manual ID copy is no longer required for the normal WalkIn success path.

## 4. Cleaning Page Polish

Updated `CleaningCompletePage.vue`:

- The page continues to support manual `seatingId` input.
- The page continues to support manual `cleaningId` input.
- When `?seatingId=<seatingId>` exists, the Start Cleaning form is prefilled.
- The page displays a small handoff note when a route query `seatingId` is present.
- Start Cleaning success now automatically copies returned `cleaningId` into the Complete Cleaning form.
- Start Cleaning success displays:

```text
清台已开始，可继续完成清台
```

- Complete Cleaning success displays `桌位已释放` when:

```text
cleaningStatus = released
tableStatus = available
```

## 5. Route Query Handling

Existing route retained:

```text
/stores/:storeId/cleaning
```

Supported query retained and polished:

```text
/stores/:storeId/cleaning?seatingId=<seatingId>
```

Behavior:

- `seatingId` is read from `route.query.seatingId`.
- The Start Cleaning form is automatically filled.
- Manual override remains possible by editing the input.
- Existing `?cleaningId=<cleaningId>` support remains available for local debugging and recovery scenarios.

No new route hierarchy was introduced.

## 6. Idempotency Handling

No API client contract was changed.

WalkIn Direct Seating continues to call:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

Cleaning continues to call:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

Each submit still generates and sends `Idempotency-Key`.

Cleaning request body remains limited to:

```json
{
  "reasonCode": null,
  "note": null
}
```

Cleaning request body does not include:

- `tenantId`
- `tableId`
- `tableGroupId`

## 7. Success Display

WalkIn success display:

- `walkInId`
- `seatingId`
- `resource`
- `status`
- `idempotency`
- `开始清台` handoff action

Start Cleaning success display:

- `cleaningId`
- `seatingId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency`
- Handoff note to continue completion

Complete Cleaning success display:

- `cleaningId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency`
- `桌位已释放` when the backend returns `released / available`

## 8. Error Display

Error display behavior was preserved.

The UI continues to render:

- `error.code`
- `error.messageKey`

No complete i18n system was introduced.
No backend error messageKey was replaced by hardcoded display copy.

## 9. Mobile-First Check

The page remains mobile-first:

- Single-column flow.
- Primary IDs remain large and easy to scan.
- Reason and note remain collapsed under `details`.
- Submit buttons remain full-width and touch-friendly.
- Success/error panels remain compact.
- No complex table map.
- No drag-and-drop table layout.
- No multi-page back office was introduced.

## 10. Commands Executed

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

- Not run in this UI-only polish round.
- Reason: no backend code, migration, API contract, or database schema was changed.
- Previous backend verification remains `165 tests, 0 failures, 0 errors`.

Browser route/query validation:

```text
npm run dev -- --host 127.0.0.1 --port 5173
```

Result:

- Vite route served successfully.
- WalkIn route opened.
- Cleaning route opened with `?seatingId=11111111-1111-1111-1111-111111111111`.
- Cleaning page auto-filled the Start Cleaning `seatingId` input from the query.
- Backend/database runtime validation was not rerun in this UI polish round.

The generated `dist/` artifact was removed after build validation.

## 11. Build Result

`npm run build` passed.

Build artifact cleanup:

- `dist/` removed after validation.

## 12. Browser Validation Result

Browser route/query validation passed:

- `/stores/:storeId/walk-ins/direct-seating` opened.
- `partySize` input rendered.
- `/stores/:storeId/cleaning?seatingId=<seatingId>` opened.
- Cleaning page rendered.
- Start Cleaning `seatingId` input was auto-filled from route query.
- Complete Cleaning button remained disabled until `cleaningId` exists.

Full browser-to-backend-to-PostgreSQL runtime validation was not rerun because this round changed only frontend polish and did not change backend/runtime behavior.

## 13. Boundary Check

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
Database touched: No  
Production database touched: No  
Seed data inserted: No  
Full login system implemented: No  
Full store switcher implemented: No  

## 14. Open Questions

- Should a future UI testing contract introduce Vitest or another lightweight frontend test runner before additional UI behavior grows?
- Should the future Table/Seating/Cleaning query API replace manual ID fallback with a store staff list or selector?

## 15. Next Step Recommendation

Recommended next round:

```text
Store Staff Closed Loop Runtime Smoke
```

Suggested scope:

- Start local backend and temporary PostgreSQL.
- Validate the polished handoff in one live flow:
  - WalkIn Direct Seating success.
  - Click `开始清台`.
  - Start Cleaning.
  - Auto-filled Complete Cleaning.
  - Complete Cleaning.
  - Final `released / available` display.
- Do not add Reservation, Queue, Turnover BI, complex table map, new backend API, migration, production auth, or database schema changes.
