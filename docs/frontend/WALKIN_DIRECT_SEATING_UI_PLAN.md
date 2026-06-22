# WalkIn Direct Seating Minimal UI Plan V1

## 1. Context

The current repository does not contain an existing Vue 3 / Vite / TypeScript frontend project.

Confirmed backend/API state:

- `POST /api/v1/stores/{storeId}/walk-ins/direct-seating` is implemented.
- WalkIn Direct Seating API integration validation passed.
- `mvn test` passed with 89 tests, 0 failures, and 0 errors.
- No Reservation API, Queue API, Cleaning API, Turnover API, or Vue UI was implemented in the prior validation round.

Because no frontend project exists, this round does not create a new full frontend application. This document records the minimal UI design to implement once a frontend structure exists.

## 2. UI Scope

Only one store-staff page is in scope:

```text
WalkIn Direct Seating
```

The page supports this business loop:

```text
WalkIn arrival
-> enter party size
-> optionally enter customer context
-> optionally select or input table/table group
-> submit direct seating
-> show result
```

Out of scope:

- Reservation UI
- Queue UI
- Cleaning UI
- Turnover UI
- Customer full search
- Full table management
- Complex table map
- Drag-and-drop table layout
- Payment / POS / Marketing / Membership
- Full auth/login system

## 3. Recommended Route

When a Vue frontend exists, add one route only:

```text
/stores/:storeId/walk-ins/direct-seating
```

Route notes:

- `storeId` comes from the route.
- `tenantId` must not be entered or trusted in the UI body.
- Auth context is expected to come from the future frontend auth/session boundary.

## 4. Minimal Page Layout

Mobile-first layout:

```text
[Header]
WalkIn Direct Seating

[Primary Form]
Party size *
[+ / - stepper or numeric input]

[Primary action]
Seat now

[Optional collapsed section]
Customer name
Customer nickname
Phone E.164
Table ID
Table Group ID
Override reason code
Override note

[Result area]
Success or error envelope
```

Default visible fields:

- `partySize`
- Submit button

Collapsed advanced fields:

- `customerName`
- `customerNickname`
- `phoneE164`
- `tableId`
- `tableGroupId`
- `overrideReasonCode`
- `overrideNote`

This supports the store operation target:

- 3 seconds to create an operational record.
- 5 seconds to assign a table.
- 1 second to identify basic customer context.

## 5. Form Contract

Request body:

```json
{
  "partySize": 2,
  "customerId": null,
  "customerName": "Guest",
  "customerNickname": "Boss friend",
  "phoneE164": "+6591234567",
  "tableId": null,
  "tableGroupId": null,
  "overrideReasonCode": null,
  "overrideNote": null
}
```

Field rules:

| Field | UI Treatment | Rule |
| --- | --- | --- |
| `partySize` | Required numeric input or stepper | Must be greater than 0. |
| `customerName` | Optional text input | Sent only when non-blank. |
| `customerNickname` | Optional text input | Sent only when non-blank. |
| `phoneE164` | Optional tel input | Must be E.164 if present. |
| `tableId` | Optional UUID input or future selector | Mutually exclusive with `tableGroupId`. |
| `tableGroupId` | Optional UUID input or future selector | Mutually exclusive with `tableId`. |
| `overrideReasonCode` | Optional text input | Required when selected valid resource is non-recommended unless `overrideNote` is present. |
| `overrideNote` | Optional text input | Required when selected valid resource is non-recommended unless `overrideReasonCode` is present. |

The UI must not put `tenantId` in the request body.

## 6. API Client Contract

Endpoint:

```text
POST /api/v1/stores/{storeId}/walk-ins/direct-seating
```

Required header:

```text
Idempotency-Key: <generated-key>
```

Client behavior:

- Generate a fresh idempotency key before first submit.
- Keep the same key while the same submit is in progress.
- Generate a new key after a successful completion.
- Generate a new key when the user intentionally retries after `IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY`.
- Do not generate a new key automatically for `IDEMPOTENCY_IN_PROGRESS`; show retry-later state.

Suggested key format:

```text
walkin-direct-seating:<timestamp>:<random-uuid>
```

## 7. Success Display

Show:

- `walkInId`
- `seatingId`
- `resource.type`
- `resource.id`
- `resource.label` when present
- `partySize`
- `status`
- `idempotency.status`
- `idempotency.replayed`

Success display should be compact and operational:

```text
Seated
Resource: TABLE A1
Party size: 2
Status: occupied
```

The visible display text should be i18n-backed in a real frontend. This plan does not create a message catalog.

## 8. Error Handling

The UI must display both:

- `error.code`
- `error.messageKey`

The UI must not replace `messageKey` with hardcoded business display copy.

Required error codes to handle:

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

For local client-side validation:

- Block submit when `partySize <= 0`.
- Mark `phoneE164` invalid when present and not E.164.
- Block submit when both `tableId` and `tableGroupId` are present.

Server response remains the source of truth.

## 9. Component Boundary

When a Vue project exists, use small boundaries:

- `WalkInDirectSeatingPage.vue`
  - Owns page state and submit lifecycle.
- `WalkInDirectSeatingForm.vue`
  - Owns form fields and client-side validation.
- `WalkInDirectSeatingResult.vue`
  - Displays success/error envelope.
- `walkInDirectSeatingApi.ts`
  - Owns API request/response types and `fetch` call.

Do not create a global design system in this round.

## 10. Test Plan

If a frontend test framework exists later, add focused tests:

- Form renders.
- `partySize` is required and must be positive.
- Invalid E.164 phone is rejected or shown before submit.
- Submit success shows resource, seating id, and status.
- Submit error shows `error.code` and `error.messageKey`.
- Idempotency key is generated and sent.
- No Reservation / Queue / Cleaning / Turnover UI routes are created.

No frontend tests are created in this repository now because no frontend project or test framework exists.

## 11. Implementation Notes For Future Frontend Setup

- Prefer existing Vue 3 / Vite / TypeScript conventions if they are introduced later.
- Keep the first screen as the operational form, not a landing page.
- Use a mobile-first single column layout.
- Use native inputs first; add selectors only when a Table read API exists.
- Do not build a table map in this round.
- Do not build full customer search in this round.
- Do not create full auth/login in this page.

## 12. Boundary Check

- Reservation UI created: No
- Queue UI created: No
- Cleaning UI created: No
- Turnover UI created: No
- Complex table map created: No
- Payment/POS/Marketing created: No
- Backend migration changed: No
- Backend API expanded: No
