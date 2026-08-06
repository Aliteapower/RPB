# Release Notes

## Version / Date
- 2026-08-06
- Scope: Quick Payment terminal entry opens in a popup window.

## Changed
- Staff home Quick Pay action now opens `/stores/:storeId/payments` in a named popup window.
- Staff bottom navigation Pay item now opens the same Quick Payment terminal in a popup window.
- PayNow-only staff home no longer redirects the current tab to `/payments`; the staff home remains the product-line workbench and the user click opens the terminal.

## Implementation Notes
- Added shared frontend helper `paymentQuickPayPopup.ts` with a named popup target and consistent window features.
- Popup links keep `_blank` fallback behavior so the terminal can still open if the browser blocks scripted popup creation.

## Migration
- No database migration.
- Backend service and Flyway were not changed.

## Risk
- Browser popup policy still requires a user gesture. Automatic async popup opening is intentionally avoided.
- If the browser blocks the named popup, the anchor fallback opens the Quick Payment terminal in a new tab/window.

## Verification
- `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test` passed.
- `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest" test` passed with 7 tests.
- `npm run build` passed.
- `git diff --check` passed with only existing CRLF warnings.

## Production Deployment
- Implementation commit deployed: `24893e17`.
- Deployment type: frontend static assets only; backend service and Flyway were not changed.
- Production frontend root: `/opt/rpb/frontend`.
- Production backup: `/opt/rpb/backups/20260806-195254-24893e17-quick-payment-popup-frontend`.
- Smoke checks:
  - `https://booking.yumstone.sg/login` returned `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/staff` returned `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments` returned `200`.
  - New chunks `StaffBottomNav-BbCwKU7i.js`, `StoreStaffHomePage-Bm4tP9ED.js`, and `PaymentQuickPayPage-BRlvZv_W.js` returned `200`.
  - `https://booking.yumstone.sg/api/v1/auth/me` returned `401` when unauthenticated.
  - `nginx` status was `active`; recent nginx ERROR entries: `0`.
