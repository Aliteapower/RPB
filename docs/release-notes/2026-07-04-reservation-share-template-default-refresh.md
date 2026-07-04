# Release Notes

## Version / Date

2026-07-04 reservation share template default refresh.

## New

None.

## Changed

None.

## Fixed

- Stores that still contain a known legacy reservation share default template are refreshed to the active platform reservation share template seed.
- Custom store templates are not overwritten.

## Migration

- Adds Flyway migration `V027__refresh_legacy_reservation_share_defaults.sql`.
- The migration updates only stores whose normalized template hash matches the known legacy default hashes from the previous default-template cleanup.

## Permission

No permission or App Gate changes.

## Risk

Low. The migration is data-only, store-scoped, and limited to known legacy default-template hashes.

## Rollback Notes

If rollback is needed, restore the affected store `reservation_share_template` values from the pre-deployment database backup or reset the store template manually from tenant admin.
