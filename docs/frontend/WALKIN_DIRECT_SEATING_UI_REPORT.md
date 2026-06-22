# WalkIn Direct Seating Minimal UI Report V1

## 1. Frontend Project

Existing frontend project found: No.

Checked repository indicators:

- No `package.json`.
- No `vite.config.*`.
- No `.vue` files.
- No `frontend/`, `web/`, `ui/`, or `client/` application directory.
- Current repository is a Spring Boot backend project with documentation and Java tests.

Per this round's instruction, no new full frontend project was scaffolded.

## 2. Created Page / Component

Created: None.

Reason:

- There is no existing Vue 3 / Vite / TypeScript frontend structure to extend.
- This round forbids forcing a complex frontend project when no frontend structure exists.

Planned future components are documented in:

- `docs/frontend/WALKIN_DIRECT_SEATING_UI_PLAN.md`

## 3. Created API Client

Created: None.

Planned future API client:

```text
walkInDirectSeatingApi.ts
```

Endpoint:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

Required header:

```text
Idempotency-Key: <generated-key>
```

## 4. Route

Created: None.

Planned future route:

```text
/stores/:storeId/walk-ins/direct-seating
```

## 5. Form Fields

Planned minimal form fields:

- `partySize` required
- `customerName` optional
- `customerNickname` optional
- `phoneE164` optional
- `tableId` optional
- `tableGroupId` optional
- `overrideReasonCode` optional
- `overrideNote` optional

The future UI must not place trusted `tenantId` in the request body.

## 6. Error Handling

Planned UI behavior:

- Display `error.code`.
- Display `error.messageKey`.
- Do not replace `messageKey` with hardcoded business display copy.

Required error codes recorded in the plan:

- `MISSING_IDEMPOTENCY_KEY`
- `INVALID_PARTY_SIZE`
- `INVALID_PHONE_E164`
- `TABLE_NOT_AVAILABLE`
- `TABLE_CAPACITY_INSUFFICIENT`
- `TABLE_LOCK_CONFLICT`
- `TABLE_INACTIVE`
- `TABLE_GROUP_INVALID`
- `OVERRIDE_REASON_REQUIRED`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`
- `FORBIDDEN`
- `STORE_SCOPE_MISMATCH`

## 7. Idempotency-Key Handling

Planned behavior:

- Generate a fresh idempotency key before first submit.
- Keep the same key while the same submit is in progress.
- Generate a new key after success.
- Generate a new key when the server returns `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Do not silently replace the key for `IDEMPOTENCY_IN_PROGRESS`.

Suggested format:

```text
walkin-direct-seating:<timestamp>:<random-uuid>
```

## 8. Mobile-First Handling

Planned page model:

- Mobile-first single-column form.
- Required field visible by default: `partySize`.
- Advanced fields collapsed by default.
- Compact success display with resource, seating id, party size, and status.
- No drag-and-drop table map.
- No complex dashboard.
- No full customer search.

Business speed targets preserved:

- 3 seconds to create operational record.
- 5 seconds to assign table.
- 1 second to find or enter customer context.

## 9. Tests

Frontend tests created: No.

Reason:

- No frontend project exists.
- No frontend test framework exists.
- This round forbids introducing a complex test framework solely for this UI planning fallback.

Backend safety verification:

- No backend code was modified in this UI round.
- No backend migration was modified in this UI round.

## 10. Boundary Check

- Reservation UI created: No
- Queue UI created: No
- Cleaning UI created: No
- Turnover UI created: No
- Customer full search created: No
- Table management UI created: No
- Complex table map created: No
- Drag-and-drop table layout created: No
- Payment/POS/Marketing created: No
- Membership UI created: No
- Full auth/login system created: No
- Backend migration changed: No
- Backend API expanded: No

## 11. Open Questions

- Should the next frontend setup round create a minimal Vue 3 / Vite / TypeScript project in this repository?
- Should the first UI use typed UUID inputs for table resources until a Table read API exists?
- Should store context be selected by URL only, or should a future store switcher exist after auth is implemented?

## 12. Next Step Recommendation

Recommended next round:

```text
Frontend Project Structure Setup
```

That round should establish the minimal Vue 3 / Vite / TypeScript application shell first. After that, implement the WalkIn Direct Seating page from `docs/frontend/WALKIN_DIRECT_SEATING_UI_PLAN.md` without expanding into Reservation, Queue, Cleaning, Turnover, Payment, POS, Marketing, or Membership.
