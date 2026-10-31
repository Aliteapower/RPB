# Troubleshooting Report

## Symptom

New tenant `pay` logged in as its default tenant administrator and opened `/stores/{storeId}/admin/payment/settings`, but saving PayNow settings displayed `请联系管理员调整员工权限后再使用。`

The expected behavior is that a tenant administrator can configure PayNow settings without a separate employee-permission adjustment.

## Evidence

- The page route and tenant admin navigation entry already exist.
- The blocking action is the PayNow profile save API, which requires `payment.settings.manage`.
- `PlatformTenantAdminAccountRepository` granted new tenant administrators `tenant.admin.manage` and reservation/queue entry permissions, but did not grant PayNow tenant-admin permissions.
- Focused red test confirmed a newly created tenant administrator was missing all four PayNow permissions:
  - `payment.settings.manage`
  - `payment.intent.view`
  - `payment.intent.create`
  - `payment.verification.review`
- A second red test confirmed tenant administrators created after V049 would not be covered by previous V048/V049 backfills.

## Root Cause

V048 backfilled PayNow permissions for tenant administrators that existed at the time V048 ran, but the platform tenant-creation path did not persist the same PayNow permission defaults for future tenant administrators. Tenants created after V048 could therefore have active PayNow product-line UI and store access while the default tenant admin still lacked `payment.settings.manage`.

The platform branch store-manager creation path also did not persist future PayNow tenant-admin permissions, even though branch store managers are granted the `tenant_admin` role and `tenant.admin.manage` for branch administration.

## Affected Files

- `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- `src/main/java/com/rpb/reservation/platform/persistence/PlatformTenantAdminAccountRepository.java`
- `src/main/java/com/rpb/reservation/platform/persistence/PlatformStoreAdminAccountRepository.java`
- `src/main/resources/db/migration/V050__paynow_recent_tenant_admin_permissions.sql`
- `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`
- `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`

## Fix Plan

1. Add explicit PayNow permission groups to `AppGateRequiredPermission`.
2. Grant PayNow tenant-admin permissions whenever platform creates or updates a tenant admin account.
3. Grant PayNow tenant-admin permissions whenever platform creates or updates a branch store-manager account.
4. Add V050 to backfill active non-platform tenant-admin accounts that still lack PayNow tenant-admin permissions.
5. Add integration and migration tests so the persistence rule stays fixed for future tenants.

## Verification

- RED, then PASS: `mvn -q "-Dtest=PlatformTenantApiIntegrationTest#creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope" test`
- RED, then PASS: `mvn -q "-Dtest=PaymentMigrationTest#grantsPaymentPermissionsToTenantAdminsCreatedAfterPreviousBackfills" test`
- RED, then PASS: `mvn -q "-Dtest=PlatformTenantApiIntegrationTest#platformAdminCreatesBranchStoreManagerWithSeparatePassword" test`
- PASS: `mvn -q "-Dtest=PaymentMigrationTest,PlatformTenantApiIntegrationTest#creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope+platformAdminCreatesBranchStoreManagerWithSeparatePassword" test`

## Remaining Risk

V050 grants PayNow tenant-admin permissions to all active non-platform accounts with the `tenant_admin` role. That matches the previous V048 tenant-admin model and the requested behavior that tenant administrators can configure PayNow. Store app activation and App Gate store access still restrict where the product line can be used.
