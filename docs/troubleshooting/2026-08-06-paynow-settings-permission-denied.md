# Troubleshooting Report

## Symptom

Tenant admin PayNow settings at `/stores/{storeId}/admin/payment/settings` displayed `操作失败，请稍后重试` when saving the PayNow merchant profile.

## Evidence

- Production backend service logs showed no startup or request ERROR around the failure window.
- Production App Gate audit rows for store `20000000-0000-0000-0000-000000000983` showed repeated `APP_GATE_DENIED` entries for app `payment`.
- The denied operator was `30000000-0000-0000-0000-000000000902` with role `tenant_admin`.
- Denial payloads showed `denyReason = PERMISSION_DENIED` and `requiredPermission = payment.settings.manage`.
- Related quick-pay attempts also showed `requiredPermission = payment.intent.create`.

## Root Cause

PayNow V047 created the native `payment` product line, operational tables, App Gate-protected APIs, and frontend entries, but did not backfill new payment permissions to existing tenant admin accounts. The UI also treated the App Gate `PERMISSION_DENIED` envelope as an unknown payment API error, causing the generic red banner.

## Affected Files

- `src/main/resources/db/migration/V048__paynow_existing_tenant_admin_permissions.sql`
- `src/pages/TenantAdminPaymentSettingsPage.vue`
- `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`
- `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

## Fix Plan

1. Add V048 to grant existing non-platform `tenant_admin` role accounts:
   - `payment.settings.manage`
   - `payment.intent.view`
   - `payment.intent.create`
   - `payment.verification.review`
2. Reuse App Gate error-message mapping on the PayNow settings page.
3. Add migration and frontend validation tests.

## Verification

- `mvn "-Dtest=PaymentMigrationTest,PayNowPaymentUiAcceptanceValidationTest" test` passed with 5 tests.

## Remaining Risk

V048 intentionally does not grant payment quick-pay permissions to every existing ordinary staff account. Staff payment permissions should be assigned through a separate staff-permission management flow if needed.
