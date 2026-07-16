# Tenant Profile Save Logo Upload

## Version / Date
2026-07-05

## New
- None.

## Changed
- Tenant admin profile save now also uploads the selected tenant LOGO file after the profile and share fields are saved.
- The standalone LOGO upload action remains available.
- LOGO upload and clear buttons are disabled while the main profile save is running to avoid duplicate submissions.

## Fixed
- Reduced confusion where selecting a LOGO and clicking the bottom save button saved text fields but did not upload the selected LOGO.

## Migration
- No database migration.
- No backend API contract change.

## Permission
- No App Gate or role permission change.

## Risk
- Low frontend-only risk. The change reuses the existing tenant profile LOGO upload endpoint and does not alter tenant isolation, media validation, or storage behavior.

## Rollback Notes
- Roll back by restoring the previous `/opt/rpb/frontend` static bundle backup.
