# WalkIn Direct Seating UI Validation Report

## 1. Read Documents

- `docs/frontend/FRONTEND_PROJECT_STRUCTURE_REPORT.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_PLAN.md`
- `docs/frontend/WALKIN_DIRECT_SEATING_UI_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_CONTRACT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_IMPLEMENTATION_REPORT.md`
- `docs/api/WALKIN_DIRECT_SEATING_API_INTEGRATION_VALIDATION_REPORT.md`
- `docs/api/API_ERROR_CONTRACT.md`
- `docs/api/API_IDEMPOTENCY_CONTRACT.md`

Confirmed:

- Vue 3 / Vite / TypeScript / Pinia / Vue Router frontend structure exists.
- WalkIn Direct Seating API integration validation passed before UI validation.
- Backend `mvn test` previously passed with 89 tests before the frontend UI boundary became stale.

## 2. Frontend Validation Environment

- OS/runtime: local Windows workspace
- Node.js: `v22.16.0`
- npm: `10.9.2`
- Vite: `6.4.3`
- Browser validation: in-app browser against Vite dev server
- Mobile viewport: `390 x 844`
- Frontend dev server: `http://127.0.0.1:5173`

## 3. Backend Validation Environment

- Real local backend service: Started on `http://127.0.0.1:8080`
- Spring profile: `local`
- Datasource: temporary local PostgreSQL on `127.0.0.1:54306`
- Production database touched: No
- Production data inserted: No
- Runtime fixture data: temporary local validation tenant/store/table records only
- Migration changed: No
- Flyway runtime migration: disabled for the local runtime run after V001 was applied manually to the temporary database
- JPA validation: enabled with `spring.jpa.hibernate.ddl-auto=validate`

## 4. Route Validated

Validated route:

```text
/stores/:storeId/walk-ins/direct-seating
```

Concrete URL opened:

```text
http://127.0.0.1:5173/stores/20000000-0000-0000-0000-000000000101/walk-ins/direct-seating
```

Observed:

- Page opens.
- Store id is read from the route param.
- `partySize` input is rendered.
- Customer section is collapsible.
- Resource section is collapsible.
- Override section is collapsible.

## 5. Form Validation Result

Validated:

- `partySize = 0` is blocked by browser native `number` / `min` / `required` validation before submit.
- Invalid `phoneE164` displays:
  - `INVALID_PHONE_E164`
  - `walkin.direct_seating.invalid_phone_e164`
- `tableId` and `tableGroupId` both present displays:
  - `SEATING_RESOURCE_INVALID`
  - `walkin.direct_seating.resource_invalid`
- Valid submit generates `Idempotency-Key`.

Backend remains the source of truth for final validation.

## 6. API Client Validation Result

API client:

- Endpoint: `POST /api/v1/stores/{storeId}/walk-ins/direct-seating`
- Uses path `storeId`.
- Sends `Idempotency-Key`.
- Does not send `tenantId` in request body.
- Handles success envelope.
- Handles API error envelope.
- Handles network/backend unavailable path.

Vite dev proxy:

```text
/api -> http://127.0.0.1:8080
```

The proxy target can be overridden with:

```text
VITE_API_PROXY_TARGET
```

Live browser-to-backend call status:

- Executed successfully through Vite proxy.
- Browser request used route `storeId`.
- Request body excluded `tenantId`.
- Header included generated `Idempotency-Key`.

## 7. Idempotency-Key Validation Result

Validated:

- No idempotency key is generated when frontend validation blocks submit.
- A key is generated when a valid submit is attempted.
- The key is rendered in the page after submit.
- The key is sent as `Idempotency-Key` header.
- Same key + same request body replayed through the backend returned HTTP 200 with `replayed=true`.

Observed completed key:

```text
ebd2bbc8-3842-4967-9290-5dc73e87f64f
```

## 8. Success Response Display Result

Real backend success display was validated from the browser.

Input:

- `partySize`: `2`
- `customerName`: `Guest Runtime`
- `phoneE164`: empty
- `tableId`: empty
- `tableGroupId`: empty

Observed success display:

- `walkInId`: `ef1086ae-c40a-4d67-9188-4e930ffcd3d3`
- `seatingId`: `a5442494-ba1c-450b-b426-e7656bddcc11`
- `resource`: `TABLE 40000000-0000-0000-0000-000000000106`
- `status`: `occupied`
- `idempotency`: `completed`

## 9. Error Response Display Result

Validated error displays:

- Backend validation when customer identity is entirely empty:
  - `INVALID_CUSTOMER_IDENTITY`
  - `walkin.direct_seating.invalid_customer_identity`
- Client-side invalid phone:
  - `INVALID_PHONE_E164`
  - `walkin.direct_seating.invalid_phone_e164`
- Client-side resource conflict:
  - `SEATING_RESOURCE_INVALID`
  - `walkin.direct_seating.resource_invalid`

The UI continues to display `error.code` and `error.messageKey`; no complete i18n system was introduced.

## 10. Mobile-first Validation Result

Validated at `390 x 844` viewport:

- Single-column layout.
- `partySize` is the first and primary input.
- Customer, Resource, and Override sections are collapsed by default.
- Success and error feedback are visible below the form.
- No complex table map.
- No drag-and-drop table layout.

## 11. Commands Executed

- `npm run build`
  - Result: Passed
- `npm run dev -- --host 127.0.0.1 --port 5173`
  - Result: Started for browser validation, then stopped
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`
  - Result: Started local backend against temporary PostgreSQL, then stopped
- `mvn test`
  - Result after boundary test fix: Passed, 92 tests, 0 failures, 0 errors

## 12. Build Result

`npm run build` passed:

- `vue-tsc --noEmit`: passed
- `vite build`: passed

The generated `dist/` build artifact was removed after validation.

## 13. Manual Validation Result

Manual/browser validation checklist:

- Open page: Passed
- Route `/stores/:storeId/walk-ins/direct-seating`: Passed
- Input `partySize`: Passed
- Submit without phone: Passed with `customerName` present
- Invalid phone displays error: Passed
- Both `tableId` and `tableGroupId` displays error: Passed
- Submit sends `Idempotency-Key`: Passed
- Success displays `walkInId` / `seatingId` / `resource` / `status`: Passed
- API error displays `code` / `messageKey`: Passed
- Page has no Reservation / Queue / Cleaning / Turnover entry: Passed

## 14. Database Validation Result

After the successful browser submission:

- `walk_ins`: `1`
- `seatings`: `1`
- `seating_resources`: `1`
- `business_events`: `4`
- `state_transition_logs`: `4`
- `audit_logs`: `2`
- `idempotency_records`: `2` (`1 failed` from the empty identity attempt, `1 completed` from the successful attempt)
- `reservations`: `0`
- `queue_tickets`: `0`
- `cleanings`: `0`
- `turnovers`: `0`

Seating boundary:

- `reservation_id`: null
- `queue_ticket_id`: null
- `walk_in_id`: populated
- `party_size_snapshot`: `2`
- `status`: `occupied`

SeatingResource boundary:

- `resource_type`: `dining_table`
- `table_id`: populated
- `table_group_id`: null
- `status`: `active`

Table status:

- Auto-selected table `S1` changed from `available` to `occupied`.

## 15. Boundary Check

- Reservation UI created: No
- Queue UI created: No
- Cleaning UI created: No
- Turnover UI created: No
- POS UI created: No
- Payment UI created: No
- Marketing UI created: No
- Membership UI created: No
- Complex table map created: No
- Drag-and-drop table layout created: No
- Table API created: No
- Customer search API created: No
- Backend API changed: No
- Backend Migration changed: No
- Database structure changed: No
- Production database touched: No
- Seed data inserted: No
- Full login system implemented: No
- Full store switcher implemented: No

## 16. Open Questions

- Should the later production auth round replace the local/test actor boundary with real JWT and RBAC infrastructure?
- Should the eventual Table API replace manual `tableId` / `tableGroupId` inputs with a selector?
- Should completely anonymous WalkIn seating remain invalid, or should Product Owner allow a generated temporary guest identity in a future round?

## 17. Next Step Recommendation

Recommended next round:

```text
WalkIn Direct Seating Production Auth Contract Design
```

Suggested scope:

- Design production JWT/RBAC/store-scope contract.
- Keep Reservation UI, Queue UI, Cleaning UI, Turnover UI, complex table map, backend migration, and database structure out of scope.
