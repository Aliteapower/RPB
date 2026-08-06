# Release Notes

## Version / Date

PayNow permission backfill, 2026-08-06.

## New

- Added Flyway `V048` to backfill payment permissions for existing non-platform tenant admin accounts.

## Changed

- PayNow settings now displays App Gate permission and product-line errors through the shared App Gate error-message mapping instead of falling back to the generic operation-failed banner.

## Fixed

- Existing tenant admins can save PayNow settings after deployment because they receive `payment.settings.manage`.
- Existing tenant admins can use the PayNow quick-pay and display flows because they receive `payment.intent.create` and `payment.intent.view`.

## Migration

- `V048__paynow_existing_tenant_admin_permissions.sql`
- Inserts only missing active permission rows into `auth_account_permissions`.
- Does not modify tenant entitlement, store app settings, payment profile, payment intent, payment session, or other business transaction data.

## Permission

Existing non-platform accounts with active `tenant_admin` role receive:

- `payment.settings.manage`
- `payment.intent.view`
- `payment.intent.create`
- `payment.verification.review`

Ordinary staff accounts are not broadened by this migration.

## Risk

- Low data risk: the migration is additive and idempotent.
- Access-risk tradeoff: tenant admins gain the complete PayNow administration and payment operation permission set, matching the existing tenant-admin operational model.
- Frontend-only UI behavior change: App Gate denials become clearer user-facing messages.

## Rollback Notes

- Backend rollback can restore the previous JAR, but Flyway will keep V048 recorded unless a deliberate corrective migration is created.
- To reverse the permission backfill, run a separately reviewed SQL migration deleting only these four permission rows for accounts that should not retain PayNow tenant-admin access.
- Frontend rollback can restore the previous `/opt/rpb/frontend` backup.

## Production Deployment

- Deployed commit: `6a8c50d8`.
- Deployment time: 2026-08-06 08:28 SGT.
- Flyway status after restart: `048 | paynow existing tenant admin permissions | t`.
- `rpb-backend` status after restart: `active`.
- Startup ERROR count after deploy: `0`.
- Public `/api/v1/auth/me`: `401`.
- PayNow settings page: `200`.
- PayNow quick-pay page: `200`.
- Backend JAR SHA-256: `7aa87666918619e7e1f4e8843ebf6777c61fc083f9cb3a64ca177c34046a498f`.
- Backup: `/opt/rpb/backups/20260806-082817-6a8c50d8-paynow-permission-backfill`.
- Production tenant admin account `30000000-0000-0000-0000-000000000902` now has all four PayNow permissions listed above.

## 2026-08-06 New Tenant Admin Permission Persistence

### Fixed

- New tenants created after V048 now persist PayNow tenant-admin permissions by default, so the default tenant administrator can save PayNow settings without a manual employee-permission adjustment.
- Platform-created branch store managers now persist PayNow tenant-admin permissions by default, matching their `tenant_admin` role and `tenant.admin.manage` branch-admin model.
- Added Flyway `V050__paynow_recent_tenant_admin_permissions.sql` to backfill active non-platform tenant-admin accounts created after earlier PayNow permission backfills.

### Permission

- Tenant administrators receive:
  - `payment.settings.manage`
  - `payment.intent.view`
  - `payment.intent.create`
  - `payment.verification.review`
- Platform-created branch store managers receive the same PayNow tenant-admin permission set because platform store creation grants them the `tenant_admin` role:
  - `payment.settings.manage`
  - `payment.intent.view`
  - `payment.intent.create`
  - `payment.verification.review`

### Validation

- PASS: `mvn -q "-Dtest=PaymentMigrationTest,PlatformTenantApiIntegrationTest#creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope+platformAdminCreatesBranchStoreManagerWithSeparatePassword" test`

### Risk

- V050 is an idempotent insert-only permission backfill. It does not delete or downgrade permissions.
- Existing authenticated sessions resolve permissions from `auth_account_permissions` through the session account, so the backfill can take effect without changing session storage.

### Rollback Notes

- Backend rollback can restore the previous JAR, but Flyway will keep V050 recorded.
- If permission rollback is required, use a separately reviewed SQL migration that deletes only the PayNow permission rows from accounts that should not retain PayNow tenant-admin or branch-manager access.
