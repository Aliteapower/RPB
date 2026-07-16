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

## Deployment

- Deployed the full-stack release from source commit `04e5628a7d67fdcea2acee864c43489581ac647e` to the shared production environment on 2026-07-16 at 14:52 SGT. The release applies to all production tenant hosts, not only `lsc106`.
- The deployed backend JAR SHA-256 is `25b98ff3067019bfb647f43e862a5f00379b38016f54f0dc452bdf1f9b0ba275`; the uploaded frontend archive SHA-256 is `10c66a1a53fc503666beea06036a2c35e3d97d5f73253bac8c99f048c200bb30`.
- `booking`, `lsc106`, `lsc83`, `20000000`, and `platform` production login pages all returned HTTP 200 and `/assets/index-C7bWH8IG.js` after the switch. The backend service remained active, and authenticated-route guards continued to return HTTP 401 without a session.
- Authenticated Chrome verification on the `lsc106` reservation page showed `老四川川菜`, the configured tenant Logo loaded from the staff-safe media route, and no fixed `食刻 · 管理` text. The existing reservation remark entry remained visible.
- No migration ran for this release. Production Flyway history remained successful through version `045`; no permission, App Gate, or runtime environment changes were made.
- The pre-switch full backup is `/opt/rpb/backups/20260716-145244-04e5628a-full`.

## Rollback Notes

- Restore `/opt/rpb/backups/20260716-145244-04e5628a-full/reservation-platform.jar` to `/opt/rpb/app/reservation-platform.jar` and restore that backup's frontend contents to `/opt/rpb/frontend`, then restart `rpb-backend`.
- Verify the backend service is active, `/api/v1/auth/me` returns HTTP 401 without a session, and the production login pages again serve the previous `/assets/index-BWyijP9Z.js` entry asset.
- No schema, data, permission, uploaded-media, cache, or Flyway rollback is required.
