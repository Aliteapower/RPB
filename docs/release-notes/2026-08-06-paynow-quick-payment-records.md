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

## Production Deployment

- Production backend and frontend deployed commit `f8998c12`.
- Deployment time: 2026-08-06 17:43 SGT.
- Backend artifact built from clean worktree `target/deploy-worktree-f8998c12`.
- Frontend artifact built from clean worktree `target/deploy-worktree-f8998c12`.
- Uploaded artifacts:
  - `/home/ubuntu/rpb-f8998c12-reservation-platform.jar`
  - `/home/ubuntu/rpb-f8998c12-frontend.tgz`
- Backend backup: `/opt/rpb/backups/20260806-1743-f8998c12-paynow-records/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260806-1743-f8998c12-paynow-records/frontend`.
- Backend JAR SHA-256: `dfad42261896fef2fe034525630b779c246c37a9fa982b411a8adf50319e477f`.
- Flyway remains at `050|paynow recent tenant admin permissions|true`; no new migration was applied.
- `rpb-backend` status after restart: `active`.
- Startup ERROR entries after deployment: `0`.
- Public `/api/v1/auth/me` returned `401`.
- Public `/login` returned `200` and loaded frontend asset `/assets/index-ah1EKTAi.js`.
- PayNow settings route returned `200`: `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/settings`.
- Quick Payment Records route returned `200`: `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records`.
- PayNow quick pay route returned `200`: `/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`.
- Host-prefix smoke returned `200` for `platform.booking.yumstone.sg/login`, `20000000.booking.yumstone.sg/login`, `20000000.booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/admin/payment/records`, and `20000000.booking.yumstone.sg/stores/d4817b28-cc48-4735-a68f-bc571c3f7989/payments`.
- New frontend chunks returned `200`:
  - `/assets/TenantAdminPaymentRecordsPage-BtfGTGEw.js`
  - `/assets/TenantAdminPaymentSettingsPage-C2a4EQFw.js`
- Protected Quick Payment Records API returned `403` without an authenticated payment actor.
- To avoid creating real payment operational records, production Quick Pay write APIs were not invoked.
