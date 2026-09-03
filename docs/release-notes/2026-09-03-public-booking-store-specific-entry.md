# Release Notes

## Version / Date

2026-09-03 public booking store-specific admin entry.

## New

- Added a focused frontend behavior test for public booking links generated from tenant and root hosts.

## Changed

- Tenant admin public booking links and QR codes now identify the selected Store through `/book/{storeId}`.
- Tenant-prefixed public hosts are retained, so the Lsc83 entry under tenant `lsc106` is generated as `https://lsc106.booking.yumstone.sg/book/855df0ef-ade8-4209-aac4-a3afd2de91e3`.

## Fixed

- Fixed tenant admin pages generating the ambiguous tenant-level `/book` entry when more than one Store under the Tenant has public booking enabled.
- Kept the existing tenant-level `/book` conflict response for multiple enabled Stores, preventing the backend from guessing a Store.

## Migration

- No database migration.
- No data change.

## Permission

- No App Gate, role, or permission change.

## Deployment

- Frontend release commit: `40b6fdf3`.
- Uploaded artifact: `/home/ubuntu/rpb-40b6fdf3-frontend.tgz`.
- Artifact SHA-256: `9d75fd7af1d4c11d5c029b116a9a1444b2af93dde9df2d31baecfdb43c02b0e1`.
- Production frontend backup: `/opt/rpb/backups/20260903-215245-40b6fdf3-public-booking-store-entry-frontend/frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260903-215245-40b6fdf3-public-booking-store-entry`.
- Live entry asset: `/assets/index-WFZusn_o.js`.
- Public Lsc83 Store entry and booking context returned `200`; the context identified the Jln Sultan Store.
- Tenant-level `lsc106` booking entry continues to return `409 multiple_enabled_stores` as designed.
- Backend service remained active and `/api/v1/auth/me` returned `401` throughout the frontend-only deployment.

## Risk

- Low frontend routing risk. The generated link now uses the existing and already supported `/book/:storeId` route.
- No Reservation creation, availability, tenant isolation, or Store scope behavior changed.

## Rollback Notes

- Revert the two tenant-host URL return values in `src/utils/hostContext.ts` to remove the Store ID path segment.
- Remove the focused `src/utils/hostContext.test.mjs` regression test if the old ambiguous tenant-entry behavior is intentionally restored.
