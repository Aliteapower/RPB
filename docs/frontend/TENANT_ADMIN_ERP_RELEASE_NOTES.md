# Release Notes

## Version / Date

Tenant Admin ERP slice / 2026-06-25, updated 2026-06-26

## New

- Added tenant admin ERP pages for staff management, table management, and store basic settings.
- Added scoped APIs under `/api/v1/stores/{storeId}/tenant-admin`.
- Added split list/form routes for staff and dining tables.
- Added tenant admin login routing to `/stores/{storeId}/admin/staff`.
- Added API contract notes for tenant admin endpoints.
- Added up/down controls for tenant admin table area and child table ordering.

## Changed

- New tenant admin accounts created by platform tenant management now receive `tenant.admin.manage`.
- Local runtime allowlist permits tenant admin API paths so controller-level auth can make the final decision.
- New table areas and child tables with omitted sort values are appended to the end of their current ordering.
- Busy dining tables can now accept sort-only updates while preserving runtime status.

## Fixed

- Existing tenant `20000000` can now see and maintain staff account `1000`.
- Tenant admin APIs reject store scope mismatch instead of allowing a tenant admin role to drift across stores.
- Reimporting an exported table workbook no longer fails just because an exported enabled table is currently occupied.

## Migration

- `V006__tenant_admin_management_foundation.sql` adds `contact_phone` and `email` to `auth_accounts`.
- V006 seeds contact data for staff `1000` and grants `tenant.admin.manage` to tenant admin accounts.
- This sorting/import refinement does not require a new database migration.

## Permission

- New permission: `tenant.admin.manage`.
- Required with role `tenant_admin` for all tenant admin endpoints.

## Risk

- Staff accounts use the global active username uniqueness in `auth_accounts`, so employee numbers remain globally unique for now.
- Table business-field edits are limited to maintainable statuses: `available` and `inactive`; non-maintainable statuses only allow safe sort-only changes.

## Rollback Notes

- Roll back frontend by removing tenant admin routes/pages and reverting `authSession` tenant admin default route changes.
- Roll back APIs by removing the `tenantadmin` module and local runtime allowlist entry.
- Database rollback requires removing V006-added columns and permission rows only after confirming no user data depends on staff contact fields.
