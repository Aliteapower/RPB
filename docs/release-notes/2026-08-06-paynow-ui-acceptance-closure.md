# PayNow UI Acceptance Closure

## Version / Date

- Date: 2026-08-06
- Branch: `codex/paynow-payment-product-line-staging`

## New

- Added tenant admin PayNow settings route: `/stores/:storeId/admin/payment/settings`.
- Added staff quick PayNow payment route: `/stores/:storeId/payments`.
- Added authenticated payment QR display route: `/stores/:storeId/payments/display/:sessionNo`.
- Added frontend Payment API/types for profile read/update, quick pay intent creation, and payment session lookup.

## Changed

- Tenant admin navigation now includes PayNow payment settings.
- Store staff home and bottom navigation now include a payment entry when the staff account has `payment.intent.create`.
- Staff PayNow QR rendering reuses the existing RPB `DownloadableQrCode` component.

## Fixed

- Closed the acceptance gap where PayNow existed as a backend product line but had no tenant-level configuration page or staff-facing quick pay pages.

## Migration

- No new database migration in this UI closure round.
- Uses the existing `V047` payment product line schema and seed.

## Permission

- Existing tenant admin payment profile API remains protected by `payment.profile.manage`.
- Quick pay creation remains protected by `payment.intent.create`.
- QR display reload uses the new backend session lookup API protected by `payment.intent.view`.

## Risk

- The display page is authenticated and store-scoped, not public. Operators must stay logged in on the display device.
- End-to-end production smoke should avoid completing a real payment unless a controlled test PayNow identifier is configured.
- Full hardcoded-Chinese validation still has pre-existing failures in `PublicBookingPage.vue` and `TenantAdminCustomersPage.vue`; this release did not expand that debt.

## Rollback Notes

- Revert the PayNow UI commit and the session lookup API commit if the new UI needs to be removed.
- No schema rollback is required for this round.

## 2026-08-06 Quick Pay Terminal Optimization

### New

- Added an RPB-native PayNow customer display route: `/stores/:storeId/payments/present/:terminalCode`.
- Added same-browser display synchronization through `BroadcastChannel` with `localStorage` fallback.
- Added a 120-second customer-display TTL that clears the current QR and returns the display to “Waiting for new payment”.

### Changed

- Reworked the staff PayNow page into a terminal-style calculator workflow with numeric keypad input.
- Changed default preset amounts to `5 / 10 / 20 / 50 / 100 / 200`.
- Added employee-editable preset amounts stored locally per store.
- Quick Pay now opens or reuses the customer display window before creating the payment intent, then pushes the generated QR to that display.

### Migration

- No database migration.
- No new backend API endpoint.

### Permission

- Payment intent creation remains protected by `payment.intent.create`.
- The new customer display route is still under the authenticated staff app shell and does not expose a public unauthenticated QR endpoint.

### Risk

- This phase supports same-browser cashier/display windows. A separate device display will require a backend active-session feed or polling API in a later phase.
- Browser popup blocking can still prevent the customer display from opening if an operator blocks popups for the site.

### Rollback Notes

- Revert the frontend commit for this optimization. Existing PayNow profile, intent creation, and session display APIs remain compatible.

### Production Deployment

- Production frontend deployed commit `90810be4`.
- Deployment type: frontend-only; backend JAR was not changed and Flyway was not run.
- Frontend artifact built from clean worktree `target/deploy-worktree-90810be4`.
- Uploaded artifact: `/home/ubuntu/rpb-90810be4-frontend.tgz`.
- Frontend backup: `/opt/rpb/backups/20260806-0911-90810be4-paynow-terminal-ui/frontend`.
- Live entry asset: `/assets/index-CW3Vnp6N.js`.
- PayNow quick pay asset: `PaymentQuickPayPage-DN7gL_sn.js`.
- PayNow customer display asset: `PaymentPresentPage-BlG9qfpK.js`.
- `rpb-backend` state remained `active`.
- Server-side `/api/v1/auth/me` returned `401`.
- Public smoke returned `200` for `/login`, `/stores/20000000-0000-0000-0000-000000000983/payments`, and `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1`.
- Host-prefix smoke returned `200` for `platform.booking.yumstone.sg/login`, `20000000.booking.yumstone.sg/login`, and `20000000.booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments`.

## 2026-08-06 Customer Display Quantity Settings

### New

- Added staff-editable PayNow customer display settings on the Quick Pay page.
- Operators can choose the maximum active display payments: `1 / 2 / 3 / 4`.
- The customer display page now renders multiple active PayNow QR cards in a responsive grid, each keeping the existing 120-second expiry behavior.

### Changed

- The same-browser PayNow display bridge now stores active display payloads as a bounded queue instead of a single current payload.
- Display settings are stored locally per store and terminal, and are synchronized to the already-open display window through `BroadcastChannel` with `localStorage` fallback.
- `QR per payment` remains fixed at `1` and primary QR remains `SGQR` until RPB exposes a second QR payload source.

### Migration

- No database migration.
- No backend API endpoint or backend JAR change.

### Permission

- No new App Gate permissions.
- Quick Pay creation remains protected by `payment.intent.create`; the customer display route remains inside the authenticated staff app shell.

### Validation

- PASS: `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`
- PASS: `npm run build`

### Risk

- This remains a same-browser cashier/display workflow. True cross-device display settings and active QR feed still require a backend display session API in a later phase.
- Existing active single-payload display state is read compatibly; newly generated display state is stored as a payload queue.

### Rollback Notes

- Roll back by redeploying the previous frontend bundle. No schema or backend rollback is required.

### Production Deployment

- Production frontend deployed commit `c7038577`.
- Deployment type: frontend-only; backend JAR was not changed and Flyway was not run.
- Frontend artifact built from clean worktree `target/deploy-worktree-c7038577`.
- Uploaded artifact: `/home/ubuntu/rpb-c7038577-frontend.tgz`.
- Frontend backup: `/opt/rpb/backups/20260806-1016-c7038577-paynow-display-settings/frontend`.
- Live entry asset: `/assets/index-TqMnWZ0R.js`.
- PayNow quick pay asset: `PaymentQuickPayPage-CLRJv5LI.js`.
- PayNow customer display asset: `PaymentPresentPage-_pcl5Bvw.js`.
- `rpb-backend` state remained `active`.
- Server-side `/api/v1/auth/me` returned `401`.
- Public smoke returned `200` for `/login`, `/stores/20000000-0000-0000-0000-000000000983/payments`, and `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1`.
- Host-prefix smoke returned `200` for `platform.booking.yumstone.sg/login`, `20000000.booking.yumstone.sg/login`, and `20000000.booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments`.
- To avoid creating real payment operational records, production Quick Pay write APIs were not invoked.

## 2026-08-06 Quick Pay Staff Permission Fix

### Fixed

- Fixed PayNow Quick Pay creation returning `403` for existing store staff and store manager accounts that lacked `payment.intent.create`.
- Quick Pay now maps App Gate `PERMISSION_DENIED` responses to the shared permission-denied message instead of the generic creation-failed banner.

### Changed

- Future staff accounts created in tenant admin staff management now receive `payment.intent.view` and `payment.intent.create`.

### Migration

- Added `V049__paynow_existing_store_staff_permissions.sql`.
- V049 grants existing active `store_staff` and `store_manager` accounts:
  - `payment.intent.view`
  - `payment.intent.create`

### Permission

- This is a permission backfill for the RPB-native PayNow staff workflow.
- No PayNow settings or verification permissions are granted to ordinary staff by V049.

### Validation

- PASS: `mvn -q "-Dtest=PaymentMigrationTest#grantsPaymentIntentPermissionsToExistingStoreStaff" test`
- PASS: `mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest#payNowPagesUseRpbNativePaymentApiAndQrComponent" test`
- PASS: `mvn -q "-Dtest=PaymentMigrationTest,PayNowPaymentUiAcceptanceValidationTest,PaymentIntentControllerTest,PaymentIntentServiceTest" test`
- PASS: `npm run build`

### Risk

- The permissions are account-level. Store access and store-level App Gate enablement still restrict which stores staff can use.

### Rollback Notes

- Roll back the backend JAR and frontend bundle to the previous deployed versions.
- If permission rollback is required after V049 applies, delete only `payment.intent.view` and `payment.intent.create` rows from `auth_account_permissions` for accounts that should not retain PayNow staff collection access.

## 2026-08-06 Production Deployment

- Production backend and frontend deployed commit `8cca5be2`.
- Backend backup: `/opt/rpb/backups/20260806-0501-8cca5be2/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260806-0501-8cca5be2/frontend`.
- Backend JAR SHA-256: `f9b8738d2b1a9ac33ad5f1a0b5e573aa49cc77c1efe3e896c7c8bc908bff442c`.
- Flyway remains at `047|payment product line foundation|t`; no new migration was applied.
- `rpb-backend` status: `active`; startup ERROR count after deployment: `0`.
- Public `/api/v1/auth/me` returned `401`.
- Public `/login` returned `200` and loaded frontend asset `/assets/index-DUl8v7f3.js`.
- Tenant admin PayNow settings route returned `200`: `/stores/20000000-0000-0000-0000-000000000983/admin/payment/settings`.
- Staff quick PayNow route returned `200`: `/stores/20000000-0000-0000-0000-000000000983/payments`.
- Host-prefix smoke returned `200` for `platform.booking.yumstone.sg/login`, `20000000.booking.yumstone.sg/login`, and `20000000.booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments`.
- Protected session lookup smoke returned `403` without an authenticated payment actor, confirming the new QR display lookup is not public.
- To avoid creating real payment operational records, production PayNow profile writes and Quick Pay write APIs were not invoked.
