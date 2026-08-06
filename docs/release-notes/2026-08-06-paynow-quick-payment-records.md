# PayNow Quick Payment Records

## Version / Date

- Date: 2026-08-06
- Scope: RPB native PayNow payment product line, tenant admin backend

## New

- Added tenant admin Quick Payment Records under the PayNow payment product line.
- Added `GET /api/v1/stores/{storeId}/payments/intents/quick-pay-records` for store-scoped quick pay records.
- Added filters for business date, status, terminal code, keyword search, and limit.
- Added summary counts and amount totals for the returned quick payment records.

## Changed

- Tenant admin navigation now groups PayNow features under one product-line parent:
  - PayNow settings
  - Quick Payment Records

## Fixed

- Tenant admins now have a native RPB page to review real quick payment records without relying on the legacy `D:\payment_runtime` admin screen.

## Migration

- No database migration.
- The report reads existing RPB-native `payment_intents` and `payment_sessions` data.

## Permission

- Reuses existing App Gate app key `payment`.
- Reuses existing permission `payment.intent.view`.
- No new permission seed is required.

## Risk

- This is a read-only reporting API and UI.
- Summary totals are calculated from the returned filtered rows, not from an unbounded full-history aggregate.
- Payment proof review remains outside this slice and can be added later as another child module in the same PayNow product line.

## Rollback Notes

- Revert the frontend route/page/navigation changes and the backend quick-pay-records endpoint.
- No schema rollback is required.

## Validation

- `mvn -q "-Dtest=PaymentIntentControllerTest,PaymentIntentServiceTest,PayNowPaymentUiAcceptanceValidationTest" test`
- `npm run build`
