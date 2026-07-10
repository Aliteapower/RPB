# Release Notes

## Version / Date
2026-07-10 - Platform store delete workflow

## New
- Platform tenant structure now supports deleting a branch store from the operating-entity store list.
- Added `DELETE /api/v1/platform/tenants/{tenantId}/stores/{storeId}` for platform admins with `platform.tenant.manage`.

## Changed
- Store deletion is a soft delete: the store becomes `inactive`, receives `deleted_at`, and disappears from active structure, authorisation, and billing renewal candidates.
- Deleting a store archives its store host aliases and public host bindings, disables store manager accounts that no longer have another active store, removes active store access, repairs default-store references, and cancels active/suspended store-scoped subscription items.
- Parent subscription amount and period are recomputed from remaining active store-scoped subscription items.
- The tenant billing page now hides cancelled or expired store billing items from the single-store renewal picker.

## Fixed
- Deleted branches can no longer leave a usable store manager login, authorised tenant-admin store scope, public subdomain binding, or active single-store renewal item behind.
- Cross-tenant store delete attempts return `STORE_NOT_FOUND`.

## Migration
- No database migration. The feature uses existing `deleted_at`, status, host alias, public host binding, account access, and subscription item fields.

## Permission
- No new permission. The endpoint uses the existing `platform_admin` role and `platform.tenant.manage` permission.
- Local runtime allowlist now includes the new platform store delete route.

## Deployment
- Deployed commit `ab882bd5` to `booking.yumstone.sg` on 2026-07-10.
- Backend backup: `/opt/rpb/backups/20260710-1509-ab882bd5/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260710-1509-ab882bd5-frontend/frontend`.
- Smoke checks:
  - `rpb-backend` active and local `/api/v1/auth/me` returned `401`.
  - Public `https://booking.yumstone.sg/api/v1/auth/me` returned `401`.
  - `https://booking.yumstone.sg/login` returned `200`.
  - `https://booking.yumstone.sg/platform/tenants/fb8e092d-aa34-42eb-bb55-5229044c3885/edit` returned `200`.
  - `https://platform.booking.yumstone.sg/login`, `https://20000000.booking.yumstone.sg/login`, and `https://20000000.booking.yumstone.sg/book` returned `200`.
  - Deployed frontend assets include `PlatformTenantFormPage-jQL2RbfD.js`, `PlatformTenantBillingPage-DJZudNGl.js`, and `api-ByecbZKk.js`; the API asset contains the store `DELETE` call.
- Flyway history remains successful through `042|allow store item subscription events|t`.

## Risk
- Tenant admins whose default store is deleted are moved to another active authorised store when available; if none remain, their default store is cleared.
- Cancelled historical store-scoped subscription items remain for audit, but they are excluded from future single-store renewal and aggregate amount/period recomputation.

## Rollback Notes
- Revert the application changes to remove the delete endpoint and UI button.
- To manually undo a mistaken delete, clear the affected store `deleted_at`, set store status back to `active`, recreate or reactivate the store host alias and public host binding, restore required `auth_account_store_access`, and reactivate any intended store-scoped subscription item.
