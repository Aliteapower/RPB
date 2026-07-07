# Release Notes

## Version / Date
2026-07-07 - Tenant store switching and staff store authorization

## New
- Added `GET /api/v1/me/stores` to return the current account's active authorized stores with `storeId`, `storeCode`, `storeName`, `status`, `locale`, and `defaultStore`.
- Added tenant admin staff authorization fields for ordinary staff create/edit: `storeIds` and `defaultStoreId`.
- Added a shared frontend store switcher in the tenant admin navigation and staff workbench top bar. Switching rewrites `/stores/:storeId/...` and does not create a separate switching session.

## Changed
- Tenant admin staff responses now include `storeIds` and `defaultStoreId`.
- Tenant admin staff create/edit validates that authorized stores are active stores in the same tenant and that the default store is inside the authorized list.
- Tenant admins can no longer rely on the `tenant_admin` role alone for store entry; store access now depends on `auth_account_store_access`.

## Fixed
- Closed the App Gate path where a tenant admin could access a store without an explicit store grant.
- Tenant admin staff management now shows each staff member's authorized stores and default store directly in the staff list.
- Tenant admin staff create/edit forms now expose the store authorization panel consistently, and the protected self-admin page shows store authorization as read-only information.

## Migration
- No database migration required. Existing `auth_accounts.default_store_id` and `auth_account_store_access` are reused.

## Permission
- No new permission codes.
- Existing `tenant.admin.manage` still gates tenant admin staff management.
- Platform admins remain in platform routes and are not exposed through the tenant store switcher.

## Risk
- Tenant admins without explicit `auth_account_store_access` rows for a store will be redirected or denied for that store.
- Existing tenant admin seed/bootstrap data must include store access rows for every store the admin should enter.
- Staff authorization changes are stricter: invalid, inactive, deleted, or cross-tenant store ids are rejected.

## Rollback Notes
- Revert the backend auth/store-access changes and the frontend switcher/staff-form changes together.
- If only the frontend is rolled back, the new API and stricter backend access remain safe but unused.
- If only the backend is rolled back, the frontend may show store choices that are not enforced consistently; avoid partial rollback.
