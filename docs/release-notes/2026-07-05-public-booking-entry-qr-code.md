# Public Booking Entry QR Code

## Version / Date
2026-07-05

## New
- Tenant public booking settings now show a reusable public entry panel with the booking URL, copy action, QR preview, and QR download.
- Added a reusable `DownloadableQrCode` Vue component backed by a shared QR rendering utility.
- QR codes use the tenant logo in the center when a tenant logo is configured and loadable.

## Changed
- The public booking entry action remains available even when customer login providers are not configured.
- The QR download file name is generated from the current store ID.

## Fixed
- None.

## Migration
- No database migration.
- No backend API contract change.

## Permission
- No App Gate or role permission change.

## Risk
- Low frontend-only risk. The new npm dependency adds client-side QR generation but does not alter reservation, queue, seating, or tenant isolation behavior.

## Rollback Notes
- Roll back by restoring the previous `/opt/rpb/frontend` static bundle backup.
