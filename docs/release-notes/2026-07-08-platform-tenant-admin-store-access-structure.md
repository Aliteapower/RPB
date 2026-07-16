# Release Notes

## Version / Date

2026-07-08 - Platform tenant admin store access structure alignment

## New

- Platform tenant structure management now shows tenant admin authorised stores under the selected operating entity context.
- Platform administrators can switch operating entities first, then authorise only that entity's visible branches and save the tenant admin access list.
- The default store selector is shown with the selected entity's authorised branches, with a hint when the current default belongs to another operating entity.

## Changed

- The tenant basic form no longer contains the authorised store checklist.
- General tenant save no longer submits admin store access fields; authorised store changes are saved from the tenant structure panel.
- The frontend tenant PATCH type now supports access-only partial updates, matching existing backend update behavior.

## Fixed

- Reduced risk of confusing branches from multiple operating entities in one authorisation list.
- Stabilised the platform tenant API integration test fixture by resetting the validation tenant admin default store before each test.

## Migration

- No database migration.
- No backend API path, request DTO, or response DTO change.

## Permission

- No new App Gate permission.
- Existing platform tenant management permission remains the gate for tenant editing and structure maintenance.

## Risk

- Frontend workflow change only for platform tenant edit UI.
- Backend tenant/store isolation remains enforced by existing tenant-scoped store validation before replacing tenant admin store access.
- The tenant admin default store is still a single global default for the tenant admin account, not a per-operating-entity default.

## Production Deployment

- Deployed frontend commit: `98de314f`.
- Production server: `booking.yumstone.sg` on `43.134.69.75`.
- Production frontend backup: `/opt/rpb/backups/20260708-091305-98de314f-frontend`.
- Live frontend entry asset: `/assets/index-Baj5x29H.js`.
- Live platform tenant edit asset: `/assets/PlatformTenantFormPage-DUU2QFRR.js`.
- Smoke checks: public `https://booking.yumstone.sg/api/v1/auth/me` returned `401`, `/login` returned `200`, `/platform/tenants/{tenantId}/edit` returned `200`, `platform.booking.yumstone.sg/login` returned `200`, `20000000.booking.yumstone.sg/login` returned `200`, and `20000000.booking.yumstone.sg/book` returned `200`.
- Live bundle check: platform tenant edit bundle contains `admin-store-access-panel`, `structure.adminStoreAccess`, and `defaultStoreElsewhere`.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle under `/opt/rpb/frontend`.
- No backend or schema rollback is required for the UI change.
