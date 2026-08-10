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
- Full `mvn -q test` was attempted twice before deployment but exceeded the local execution timeout; no failing assertion was captured.

## Production Deployment

- Deployment date: 2026-08-10.
- Deployed commit: `056b92f2 feat: add paynow staff daily report`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Scope: backend and frontend; Flyway migrations, environment variables, and App Gate permission seeds were not changed.
- Clean deploy worktree: `target/deploy-worktree-056b92f2`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-056b92f2.jar`
  - `/home/ubuntu/rpb-056b92f2-frontend.tgz`
- Backend JAR SHA-256: `A6167ECE108450B6C6CD0B5A78118B113BE5DFBD233935B6EBDDB8AE4E6899D4`.
- Frontend tarball SHA-256: `2D56A34C3D274EC41F203109DE00A0F24A7651A7B3DF3248992AA73F82D110DB`.
- Production backup: `/opt/rpb/backups/20260810-2010-056b92f2-paynow-staff-daily-report`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260810-2010-056b92f2-paynow-staff-daily-report`.
- `rpb-backend`: `active / running`, PID `907112`.
- Public `/login` loaded frontend entry asset `/assets/index-D60w9mJ-.js` and CSS `/assets/index-D26kJdZF.css`.
- Production smoke:
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/present/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - Unauthenticated quick-pay records endpoint: `403`.
  - `PaymentQuickPayPage-CTqV2it7.js`, `api-CAnYWcEo.js`, `PaymentProofReviewPage-DZB7knzh.js`, `PaymentPresentPage-C9rvprU4.js`, and `i18n-Clt2aO_P.js`: `200`.
  - Production `rpb-backend` recent 8-minute `ERROR` count after deployment: `0`.
- Rollback: restore `/opt/rpb/app/reservation-platform.jar` and `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-2010-056b92f2-paynow-staff-daily-report`, or switch frontend back to `/opt/rpb/frontend.previous-20260810-2010-056b92f2-paynow-staff-daily-report`, then restart `rpb-backend` and reload nginx.
