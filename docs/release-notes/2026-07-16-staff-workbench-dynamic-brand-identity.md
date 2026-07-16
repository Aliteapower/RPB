# Release Notes

## Version / Date

2026-07-16 - Tenant staff workbench dynamic brand identity

## New

- Added a reusable staff brand identity component used by all eight employee pages with the shared workbench top bar.
- Added authenticated, read-only tenant Logo delivery at `GET /api/v1/me/stores/{storeId}/logo/media/{assetId}` for the current account's authorized stores.

## Changed

- The employee top bar now shows the current store's configured sharing display name and tenant Logo instead of the fixed `食刻 · 管理` / `Shike Ops` identity.
- Blank sharing names fall back to the backend store name; missing or failed Logo images fall back to the resolved name's first Unicode character.
- `GET /api/v1/me/stores` adds nullable `shareDisplayName` and `tenantLogoMediaUrl` fields. Existing fields, ordering, and request behavior are unchanged.
- Long brand names truncate within the existing responsive top bar on phone, tablet portrait, and tablet landscape layouts.

## Fixed

- Prevented regular staff Logo requests from depending on the platform-admin Logo endpoint or the `queue.display.view` permission.
- Preserved the existing store switcher label and all Reservation, Queue, Walk-in, Seating, and Table workflow behavior.

## Migration

- No database migration or data backfill is required. Existing `stores.share_display_name`, `stores.display_name`, `tenants.logo_media_asset_id`, and media assets are reused.

## Permission

- No permission codes or App Gate entitlements were added or changed.
- Logo reads require a valid staff session, an active store authorization for the requested `storeId`, and a match with that tenant's current Logo asset. Tenant-admin and platform-admin management APIs remain unchanged.

## Risk

- API risk is low and additive; older clients can ignore the two new nullable store fields.
- UI risk is limited to the shared employee top-bar identity area. Workflow routes, commands, filters, store switching, and business state are unchanged.
- Tenant isolation is enforced before media loading through the existing account/access/tenant/store query; an unauthorized or cross-tenant store is rejected with `STORE_ACCESS_DENIED`.

## Verification

- `AuthApiIntegrationTest` covers configured and missing branding, authorized Logo reads, inactive-store behavior, and unauthorized cross-tenant rejection.
- `StaffWorkbenchBrandIdentityUiValidationTest` covers the shared resolver/component, Logo error fallback, localized fixed-brand removal, and all eight top-bar page integrations.
- Related authentication/i18n/responsive workbench regression suites and the frontend production build pass.

## Rollback Notes

- Revert the dynamic-brand backend, frontend, tests, and documentation commits together.
- Restore the fixed top-bar identity and remove the two additive authorized-store fields plus the staff Logo read endpoint.
- No schema, data, permission, uploaded-media, or cache migration rollback is needed.
