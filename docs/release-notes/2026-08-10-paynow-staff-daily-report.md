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

## Follow-Up: Independent Staff Report Page

- Date: 2026-08-10.
- Scope: frontend-only Quick Payment staff workflow refinement.
- New:
  - Quick Payment now opens the daily report through a top-bar `今日收款报表` button.
  - The report lives on `/stores/{storeId}/payments/report/{terminalCode}` and opens as its own popup/new page for the active terminal.
  - The report page keeps the existing default of current terminal plus current cashier, with a persisted switch to the whole terminal line.
- Changed:
  - The Quick Payment calculator page no longer renders or loads the full report card, so the keypad starts higher on small screens.
  - The report page refreshes when focused and still has an explicit refresh button.
- Migration: no database migration.
- Permission: reuses the existing `payment` App Gate app and `payment.intent.view` permission.
- Risk: low frontend-only navigation and layout risk; the backend API contract and PayNow payment creation flow are unchanged.
- Validation:
  - `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
- Rollback: revert `PaymentQuickPayReportPage.vue`, the new router entry, and the Quick Payment top-bar report button; restore the previous inline report panel if needed.

### Independent Staff Report Page Deployment

- Deployment date: 2026-08-10.
- Deployed commit: `c1f80fba feat: move paynow daily report to popup page`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment type: frontend static assets only; backend JAR, Flyway migrations, environment variables, and App Gate permission seeds were not changed.
- Clean deploy worktree: `target/deploy-worktree-c1f80fba`.
- Uploaded artifact: `/home/ubuntu/rpb-c1f80fba-frontend.tgz`.
- Frontend tarball SHA-256: `46DECDFB1355F7536684AC53732248FBF9FC8DFC8165895DECDA67F808E61017`.
- Production frontend backup: `/opt/rpb/backups/20260810-2037-c1f80fba-paynow-report-popup-frontend/frontend`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260810-2037-c1f80fba-paynow-report-popup-frontend`.
- Public `/login` loaded frontend entry asset `/assets/index-BqfEC5a9.js` and CSS `/assets/index-D26kJdZF.css`.
- Production smoke:
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/report/T1`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/proof-review`: `200`.
  - `PaymentQuickPayReportPage-Bgh1qTBx.js`, `PaymentQuickPayReportPage-CCjDm0S8.css`, `PaymentQuickPayPage-G3_hw1j9.js`, `PaymentQuickPayPage-CJYwTm2n.css`, and `api-DyB1w4mK.js`: `200`.
  - Production `rpb-backend`: `active`.
  - Production `rpb-backend` recent 8-minute `ERROR` count after deployment: `0`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260810-2037-c1f80fba-paynow-report-popup-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260810-2037-c1f80fba-paynow-report-popup-frontend`, then reload nginx.

## Follow-Up: Staff Report Card Detail Query

- Date: 2026-08-10.
- Scope: frontend-only Quick Payment staff report refinement.
- New:
  - The three daily report cards are clickable filters.
  - Clicking `真实收款`, `待支付`, or `待检验确认` queries the existing quick-pay records endpoint with the matching status and shows the detailed rows below the cards.
  - Each detail row shows display number, amount, Ref, cashier, created time, and session number.
- Migration: no database migration.
- Permission: reuses the existing `payment` App Gate app and `payment.intent.view` permission.
- Risk: low frontend-only query and layout risk; the backend API contract already supports the `status` filter and PayNow payment creation is unchanged.
- Validation:
  - `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`
  - `npm run build`
- Rollback: revert the card button/detail list additions in `PaymentQuickPayReportPage.vue`, the generated quick-pay report copy keys, and the UI acceptance assertions.

### Staff Report Card Detail Query Deployment

- Deployment date: 2026-08-11.
- Deployed commit: `0f3df812 feat: show paynow report card details`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment type: frontend static assets only; backend JAR, Flyway migrations, environment variables, and App Gate permission seeds were not changed.
- Clean deploy worktree: `target/deploy-worktree-0f3df812`.
- Uploaded artifact: `/home/ubuntu/rpb-0f3df812-frontend.tgz`.
- Frontend tarball SHA-256: `8BE2E4B2470F46600B6E9E8985351A3CF25423AF4723EDCA69E2B5CBE315B9E0`.
- Production frontend backup: `/opt/rpb/backups/20260811-0638-0f3df812-paynow-report-details-frontend/frontend`.
- Previous frontend kept at `/opt/rpb/frontend.previous-20260811-0638-0f3df812-paynow-report-details-frontend`.
- Public `/login` loaded frontend entry asset `/assets/index-Hca3u2Za.js` and CSS `/assets/index-D26kJdZF.css`.
- Production smoke:
  - `https://booking.yumstone.sg/api/v1/auth/me`: `401`.
  - `https://booking.yumstone.sg/login`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`: `200`.
  - `https://booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments/report/T1`: `200`.
  - `PaymentQuickPayReportPage-DWRsPnO-.js`, `PaymentQuickPayReportPage-CC229YOZ.css`, `PaymentQuickPayPage-CGuwCvf-.js`, `PaymentQuickPayPage-CJYwTm2n.css`, `api-CpxzK1ap.js`, and `i18n-LFHLp3w4.js`: `200`.
  - Production `rpb-backend`: `active`.
  - Production `rpb-backend` recent 10-minute `ERROR` count after deployment: `0`.
- Rollback: restore `/opt/rpb/frontend` from `/opt/rpb/backups/20260811-0638-0f3df812-paynow-report-details-frontend/frontend` or switch back to `/opt/rpb/frontend.previous-20260811-0638-0f3df812-paynow-report-details-frontend`, then reload nginx.
