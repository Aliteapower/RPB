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
