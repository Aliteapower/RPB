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
