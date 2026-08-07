# Release Notes

## Version / Date

2026-08-07 PayNow present screen download button removal

## New

- Added a `showDownload` switch to the shared QR component, defaulting to enabled for existing screens.

## Changed

- The PayNow customer present screen no longer shows the download payment QR button.

## Fixed

- Removed the operator-facing download action from the customer display QR card.

## Migration

- No database migration.

## Permission

- No App Gate or permission changes.

## Risk

- Frontend-only change. Settings and payment display pages still keep their QR download behavior.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle.
