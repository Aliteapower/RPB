# Cleaning Complete Minimal UI Report V1

## 1. Read Documents

- `docs/api/CLEANING_COMPLETE_API_CONTRACT.md`
- `docs/api/CLEANING_COMPLETE_API_IMPLEMENTATION_REPORT.md`
- `docs/api/CLEANING_COMPLETE_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/CLEANING_API_ERROR_CONTRACT.md`
- `docs/api/CLEANING_API_IDEMPOTENCY_CONTRACT.md`
- `docs/frontend/FRONTEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_PLAN.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_VALIDATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/governance/BUSINESS_RULES.md`
- `docs/architecture/ARCHITECTURE.md`

Confirmed:

- Cleaning Complete API integration validation passed.
- Previous backend `mvn test` passed: 164 tests, 0 failures, 0 errors.
- Vue 3 / Vite / TypeScript / Pinia / Vue Router frontend project exists.
- WalkIn Direct Seating page exists.

## 2. Created / Updated Files

Created:

- `src/api/cleaningApi.ts`
- `src/types/cleaning.ts`
- `src/pages/CleaningCompletePage.vue`
- `docs/frontend/CLEANING_COMPLETE_UI_REPORT.md`

Updated:

- `src/router/index.ts`
- `src/pages/WalkInDirectSeatingPage.vue`

## 3. Routes

Created one combined Cleaning page route:

```text
/stores/:storeId/cleaning
```

The page contains two clearly separated forms:

- Start Cleaning by `seatingId`
- Complete Cleaning by `cleaningId`

The page also reads optional query parameters for handoff:

- `?seatingId=<seatingId>`
- `?cleaningId=<cleaningId>`

WalkIn success display now includes a minimal link to:

```text
/stores/:storeId/cleaning?seatingId=<seatingId>
```

## 4. API Client

Created:

```text
src/api/cleaningApi.ts
```

Start Cleaning:

```text
POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start
```

Complete Cleaning:

```text
POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete
```

Header:

```text
Idempotency-Key
```

Request body:

```json
{
  "reasonCode": null,
  "note": null
}
```

The client does not send:

- `tenantId`
- `tableId`
- `tableGroupId`

The client handles:

- success response
- API error envelope
- network failure
- invalid API response shape
- idempotency replay fields

## 5. Types

Created:

```text
src/types/cleaning.ts
```

Included:

- `StartCleaningRequest`
- `CompleteCleaningRequest`
- `StartCleaningResponse`
- `CompleteCleaningResponse`
- `CleaningApiErrorResponse`
- `CleaningResourceType`
- `CleaningIdempotencyStatus`

Types align with the Cleaning Complete API contract.

## 6. Page Behavior

Created:

```text
src/pages/CleaningCompletePage.vue
```

Start Cleaning form:

- `seatingId`
- optional `reasonCode`
- optional `note`
- action: Start Cleaning

Complete Cleaning form:

- `cleaningId`
- optional `reasonCode`
- optional `note`
- action: Complete Cleaning

No `tableId` or `tableGroupId` inputs are present.

## 7. Form Validation

Frontend validation covers:

- Start Cleaning requires `seatingId`.
- Complete Cleaning requires `cleaningId`.
- Submit generates `Idempotency-Key`.
- Request body excludes `tenantId`, `tableId`, and `tableGroupId`.

Backend remains the final source of truth for scope, state, idempotency, and persistence validation.

## 8. Idempotency-Key Handling

The page generates a fresh key for each submit:

```text
cleaning:start-cleaning:<uuid>
cleaning:complete-cleaning:<uuid>
```

Fallback format when `crypto.randomUUID` is unavailable:

```text
cleaning:<action>:<timestamp>:<random>
```

The latest generated key is displayed after submit for local validation and debugging.

## 9. Success Display

Start Cleaning success displays:

- `cleaningId`
- `seatingId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency.status`
- `idempotency.replayed`

Complete Cleaning success displays:

- `cleaningId`
- `resource`
- `cleaningStatus`
- `tableStatus`
- `events`
- `idempotency.status`
- `idempotency.replayed`

## 10. Error Display

The page displays:

- `error.code`
- `error.messageKey`

No complete i18n system was introduced.
The UI does not replace backend `messageKey` with hardcoded business display copy.

## 11. Mobile-first Handling

- Single-column page layout.
- Start Cleaning and Complete Cleaning are separated into two compact form sections.
- `seatingId` and `cleaningId` are the primary fields.
- `reasonCode` and `note` are collapsed under `Reason`.
- Submit buttons are large and visible.
- Success and error panels are compact and readable on mobile.
- No complex table map.
- No drag-and-drop table layout.

## 12. Build / Test Result

Baseline build before implementation:

```text
npm run build
```

Result:

- Passed.

Final build after implementation:

```text
npm run build
```

Result:

- Passed.

Frontend tests:

- `npm test`: Not run.
- Reason: package has no `test` script and no frontend test framework. No complex test framework was introduced in this round.

Backend tests:

- `mvn test`: Not run in this UI round.
- Reason: backend API, migration, Java code, and database schema were not changed.

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
Customer search API created: No  
Backend Controller changed: No  
Backend API changed: No  
Backend Migration changed: No  
SQL file created: No  
Database touched: No  
Production database touched: No  
Seed data inserted: No  
OpenAPI generated: No  

## 14. Open Questions

- Should a later local runtime validation round open this Cleaning page against a temporary backend/database and verify browser-to-backend calls?
- Should a future Table status/read API provide selectable active Cleaning rows instead of manual `seatingId` / `cleaningId` entry?
- Should a lightweight frontend test framework be introduced only after a broader frontend testing contract is approved?

## 15. Next Step Recommendation

Recommended next round:

```text
Store Staff Cleaning Complete UI Validation
```

That round should validate:

- Route opens.
- Start Cleaning form calls the backend API.
- Complete Cleaning form calls the backend API.
- `Idempotency-Key` is sent.
- Success and error displays work.
- Request body excludes `tenantId`, `tableId`, and `tableGroupId`.
- No Reservation / Queue / Turnover UI, complex table map, backend API, migration, or production database changes.
