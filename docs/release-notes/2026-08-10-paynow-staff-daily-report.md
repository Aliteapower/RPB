# PayNow Staff Daily Report

## Version / Date

- Date: 2026-08-10
- Scope: RPB native PayNow Quick Payment staff workflow

## New

- Added a staff-facing daily PayNow report on the Quick Payment page.
- The report defaults to the current terminal plus the logged-in cashier.
- Staff can switch the report to the whole current terminal line.
- The selected report scope is persisted per store and terminal in browser storage.
- The report shows real collected amount, pending amount, and awaiting-verification amount with counts.

## Changed

- `GET /api/v1/stores/{storeId}/payments/intents/quick-pay-records` now accepts optional `cashierName`.
- Quick Pay record summaries now include `awaitingVerificationCount`, `pendingAmount`, and `awaitingVerificationAmount`.

## Fixed

- Staff no longer need tenant-admin records access to see their current-day collected PayNow amount for the active terminal.

## Migration

- No database migration.
- The report reads existing `payment_intents` and `payment_sessions` rows.

## Permission

- Reuses existing App Gate app key `payment`.
- Reuses existing permission `payment.intent.view`.
- No new permission seed is required.

## Risk

- The endpoint change is additive and keeps existing records fields intact.
- The staff report is bounded by the existing `limit` behavior and requests up to 200 returned rows for the selected business date and terminal.
- `paidAmount` is the only real collected amount. Pending and awaiting-verification totals are operational follow-up numbers.

## Rollback Notes

- Revert the frontend Quick Payment report panel, TypeScript summary fields, API query parameter, backend summary additions, and API contract note.
- No schema rollback is required.

## Validation

- `mvn -q "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest,PaymentIntentResponsesTest" test`
- `mvn -q "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest,PaymentIntentResponsesTest,PayNowPaymentUiAcceptanceValidationTest" test`
- `npm run build`
- `git diff --check` passed with CRLF conversion warnings only.
