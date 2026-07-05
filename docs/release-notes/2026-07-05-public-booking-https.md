# Release Notes

## Version / Date
2026-07-05 public booking HTTPS

## New
- Enabled HTTPS for `booking.yumstone.sg` using a Let's Encrypt certificate managed by certbot.
- Added `Strict-Transport-Security: max-age=31536000` on HTTPS responses so browsers remember to use HTTPS.

## Changed
- HTTP requests to public booking pages now redirect to HTTPS.
- The public booking deployment runbook now uses HTTPS smoke-test URLs.

## Fixed
- WhatsApp reservation share links can now open on browsers that require or upgrade to HTTPS.

## Migration
No database migration.

## Permission
No App Gate or permission change.

## Risk
Runtime configuration only. Backend jar, frontend assets, API contracts, tenant data, and reservation share token data were unchanged.

## Rollback Notes
- Nginx configuration backup: `/opt/rpb/backups/20260705-1801-https-booking-yumstone`.
- HSTS header configuration backup: `/opt/rpb/backups/20260705-1824-https-hsts-booking-yumstone`.
- Restore the previous nginx site config from that backup and reload nginx if HTTPS must be rolled back.
