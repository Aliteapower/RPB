# Release Notes

## Version / Date

2026-07-05 public booking weekly rule clear.

## New

None.

## Changed

- Tenant admin public booking weekly rules now allow saving with no weekdays selected.
- An empty weekday selection clears the matching weekly rule group instead of blocking with a validation error.

## Fixed

- Removed the incorrect "at least one weekday" validation from public booking weekly rule editing.

## Migration

No database migration.

## Permission

No permission or App Gate changes.

## Risk

Low. This is a frontend behavior change that uses the existing weekly rule synchronization and delete API.

## Rollback Notes

Redeploy the previous frontend bundle from `/opt/rpb/backups` if rollback is required.
