# Tenant Share Profile I18n Catalog Runtime

## Version / Date
2026-07-06

## Fixed
- Tenant admin share profile and booking share template pages now read and save reservation share arrival notes and share templates through `i18n_message_catalog` for the active locale.
- English saves create store-scoped `en-SG` overrides without overwriting legacy Chinese store columns.
- Chinese saves still sync legacy store columns for backward-compatible runtime fallback.

## Migration
- No new database migration.
- Existing `i18n_message_catalog` keys are reused:
  - `reservation.share.arrival_note`
  - `reservation.share.restaurant_reservation_confirmation_v1`

## Permission
- No permission or App Gate changes.

## Risk
- API remains backward compatible; omitted `locale` falls back to `zh-CN`.
- Meal period display names and tenant call-screen ad-set editing remain separate follow-up migrations; runtime call-screen display already resolves catalog text.

## Rollback Notes
- Roll back the backend jar and frontend assets to the previous deployment backup.
- Existing catalog rows created by this release can remain in place; older runtime paths will continue using legacy store columns.
