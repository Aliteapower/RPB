# PayNow Staff Daily Report Design

## Purpose

Store staff need a same-day payment report on the Quick Payment workflow so they can see their real collected amount without opening tenant-admin records. The report must default to the current terminal line and current logged-in cashier, while allowing the cashier to switch to the whole terminal line.

## Scope

Build a read-only staff report for PayNow Quick Payment records.

- The default scope is current `terminalCode` plus the current authenticated user's `username`.
- The alternate scope is current `terminalCode` for all cashiers.
- The selected scope is persisted per `storeId` and `terminalCode` in browser storage.
- The report uses the current open payment business day when available. If no day is open, it uses today's date from the current clock.
- The report summarizes Quick Pay records only. Reservation, queue, POS, and other payment sources stay out of scope.

## Data Semantics

The report shows three status groups:

- `paid`: payment success. This is the real collected amount.
- `pending`: waiting for customer payment.
- `awaiting_verification`: receipt submitted or matched but still waiting for verification confirmation.

Amounts are summed from `payment_intents.amount`. Counts are counts of Quick Pay records in the selected scope and business date.

Expired, cancelled, and failed records are not included in the primary employee report cards. They may still appear in tenant-admin records through the existing records page.

## API Design

Extend the existing read-only endpoint:

`GET /api/v1/stores/{storeId}/payments/intents/quick-pay-records`

Add optional query parameter:

- `cashierName`: exact cashier name filter after trimming.

Extend response summary:

```json
{
  "success": true,
  "records": [],
  "summary": {
    "count": 3,
    "pendingCount": 1,
    "awaitingVerificationCount": 1,
    "paidCount": 1,
    "totalAmount": "25.00",
    "pendingAmount": "5.00",
    "awaitingVerificationAmount": "8.00",
    "paidAmount": "12.00",
    "currency": "SGD"
  }
}
```

Compatibility:

- Existing consumers can continue reading the previous summary fields.
- The new fields are additive.
- The endpoint continues to require App Gate `payment.intent.view`.
- Store and tenant isolation remain enforced in `PaymentIntentController` and `PaymentIntentService`.

## UI Design

Add a compact report panel to `PaymentQuickPayPage.vue` below the payment settings strip and above the keypad.

Panel content:

- Header: `今日收款报表` / `Today's payment report`.
- Context line: terminal code, displayed business date, and selected scope.
- Segmented control:
  - `我的收款`: current terminal plus current cashier.
  - `T1 全线`: current terminal for all cashiers, with the actual terminal code in the label where possible.
- Cards:
  - `真实收款`: paid amount and count.
  - `待支付`: pending amount and count.
  - `待检验确认`: awaiting verification amount and count.
- Refresh button and loading/error/empty states.

Interaction:

- The panel loads when the page mounts, when store, terminal, business date, or report mode changes, and after creating or manually confirming a Quick Pay session.
- The selected mode is saved to localStorage under a key that includes store and terminal.
- When current user name is unavailable, `我的收款` still loads using an empty cashier filter and displays the same empty-safe UI.

## Testing

Backend tests:

- `PaymentIntentServiceTest` verifies `cashierName` is trimmed and passed to the repository.
- `PaymentIntentServiceTest` verifies invalid status validation still rejects unknown statuses.
- `PaymentIntentControllerTest` verifies the records endpoint signature still requires `payment.intent.view`.
- Response summary tests verify pending, awaiting verification, and paid counts and amounts.

Frontend validation:

- `npm run build` must pass.
- Existing UI acceptance validation for PayNow pages must pass.
- Manual or screenshot validation should check the report panel on mobile-width and desktop-width layouts.

## Non-Goals

- No database migration.
- No new App Gate permission.
- No settlement, payout, or bank reconciliation workflow.
- No admin-only export.
- No cross-terminal store-wide report in this slice.

## Release Notes

The completed implementation should add a release note under `docs/release-notes/2026-08-10-paynow-staff-daily-report.md` covering new behavior, no-migration impact, unchanged permissions, validation, and rollback.
