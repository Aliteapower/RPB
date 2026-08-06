# Release Notes

## Version / Date
- 2026-08-06
- Scope: tenant/staff navigation visibility by App Gate product line.

## New
- Added shared frontend product-line visibility loading through `/api/me/apps`.
- Staff bottom navigation now maps each tab to its owning product line and filters tabs by visible app entitlement.
- Tenant admin navigation now groups PayNow and Reservation Queue features under product-line sections.

## Changed
- PayNow-only staff sessions that land on `/stores/:storeId/staff` are redirected to the Quick Payment terminal when `payment.intent.create` is available.
- Staff home no longer loads reservation/queue overview when `reservation_queue` is not visible for the current tenant and store.
- Reservation, queue, and table staff homepage panels are shown only when `reservation_queue` is enabled and visible.

## Fixed
- PayNow-only tenants no longer see reservation, queue, table, public booking, or call-screen entries in the tenant admin navigation after visible app data loads.
- PayNow-only staff no longer see reservation/queue/table bottom navigation entries after visible app data loads.

## Migration
- No database migration.
- Existing persistent source of truth remains `tenant_app_entitlements`, `store_app_settings`, and `platform_apps`.

## Permission
- No new permission keys.
- Visibility continues to use `/api/me/apps`, which combines tenant entitlement, store app setting, entry visibility, store access, and entry permissions.

## Risk
- Frontend now depends on `/api/me/apps` for tenant admin and bottom navigation filtering. If that endpoint is unavailable, navigation falls back to showing product-line entries until loading completes or returns empty.
- Direct URL access to hidden reservation/queue/table pages still relies on backend App Gate denial; this release focuses on navigation and homepage entry visibility.

## Rollback Notes
- Revert the frontend changes in `src/composables/useStoreVisibleApps.ts`, `src/components/staff/StaffBottomNav.vue`, `src/components/staff/staffBottomNavItems.ts`, `src/pages/StoreStaffHomePage.vue`, and `src/components/tenant-admin/TenantAdminNav.vue`.
- No schema rollback is required.
