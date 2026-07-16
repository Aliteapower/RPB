# Release Notes

## Version / Date
2026-07-07 - Tenant store switching and staff store authorization

## New
- Added `GET /api/v1/me/stores` to return the current account's active authorized stores with `storeId`, `storeCode`, `storeName`, `status`, `locale`, and `defaultStore`.
- Added tenant admin staff authorization fields for ordinary staff create/edit: `storeIds` and `defaultStoreId`.
- Added a shared frontend store switcher in the tenant admin navigation and staff workbench top bar. Switching rewrites `/stores/:storeId/...` and does not create a separate switching session.
- Added platform tenant admin store authorization management so platform admins can grant a tenant admin multiple active stores and choose the tenant admin's default store.

## Changed
- Tenant admin staff responses now include `storeIds` and `defaultStoreId`.
- Tenant admin staff create/edit validates that authorized stores are active stores in the same tenant and that the default store is inside the authorized list.
- Tenant admins can no longer rely on the `tenant_admin` role alone for store entry; store access now depends on `auth_account_store_access`.
- `PATCH /api/v1/platform/tenants/{tenantId}` accepts optional `adminStoreIds` and `defaultAdminStoreId`; when omitted, existing tenant admin store authorization remains unchanged.

## Fixed
- Closed the App Gate path where a tenant admin could access a store without an explicit store grant.
- Tenant admin staff management now shows each staff member's authorized stores and default store directly in the staff list.
- Tenant admin staff create/edit forms now expose the store authorization panel consistently, and the protected self-admin page shows store authorization as read-only information.
- Platform tenant edit now exposes a persistent "authorized stores / default store" panel for tenant admins, allowing stores such as `lsc106` to become available in tenant-side staff authorization after platform approval.

## Migration
- No database migration required. Existing `auth_accounts.default_store_id` and `auth_account_store_access` are reused.

## Permission
- No new permission codes.
- Existing `tenant.admin.manage` still gates tenant admin staff management.
- Existing `platform.tenant.manage` gates platform tenant admin store authorization management.
- Platform admins remain in platform routes and are not exposed through the tenant store switcher.

## Risk
- Tenant admins without explicit `auth_account_store_access` rows for a store will be redirected or denied for that store.
- Existing tenant admin seed/bootstrap data must include store access rows for every store the admin should enter.
- Staff authorization changes are stricter: invalid, inactive, deleted, or cross-tenant store ids are rejected.
- Platform tenant admin store authorization rejects invalid, inactive, deleted, null, or cross-tenant store ids.

## Production Deployment
- Deployed commit: `1a26389e`.
- Backend backup: `/opt/rpb/backups/20260707-0908-1a26389e/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260707-0908-1a26389e-frontend/frontend`.
- Live entry asset: `/assets/index-Dof32dpv.js`.
- Live platform tenant edit asset: `/assets/PlatformTenantFormPage-NZHJpr6d.js`.
- Smoke checks: `rpb-backend` active, unauthenticated `/api/v1/auth/me` returned 401, `/login` returned 200, `platform.booking.yumstone.sg/login` returned 200, `20000000.booking.yumstone.sg/login` returned 200, and `20000000.booking.yumstone.sg/book` returned 200.
- Production Flyway history remained at v034; no migration was applied for this release.
- Logged-in Chrome verification before this deployment confirmed the tenant staff list shows authorized/default store summaries and the staff edit page shows the editable store authorization panel.

## Rollback Notes
- Revert the backend auth/store-access changes and the frontend switcher/staff-form changes together.
- If only the frontend is rolled back, the new API and stricter backend access remain safe but unused.
- If only the backend is rolled back, the frontend may show store choices that are not enforced consistently; avoid partial rollback.
