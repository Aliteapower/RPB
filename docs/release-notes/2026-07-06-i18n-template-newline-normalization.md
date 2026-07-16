# 2026-07-06 i18n template newline normalization

## Fixed

- Reservation share templates now normalize escaped newline tokens such as `\n` and `\r\n` into real line breaks before tenant admin editing, staff share rendering, and public reservation share rendering.
- English reservation confirmation templates no longer display literal `\n` text on H5 public share pages.

## Migration

- Added Flyway migration `V029__normalize_i18n_template_escaped_newlines.sql`.
- The migration updates active `i18n_message_catalog` rows whose registered key has `text_kind = 'template'`, replacing escaped newline sequences with real newline characters and incrementing `version`.
- No schema, permission, API contract, or App Gate changes.

## Validation

- `TenantAdminShareProfileServiceTest`
- `ReservationPublicShareApplicationServiceTest`
- `ReservationShareInfoApplicationServiceTest`
- `I18nCatalogMigrationSourceValidationTest`
- `I18nMessageResolverTest`
- `I18nCatalogApiImplementationValidationTest`
- `mvn -q -DskipTests package`
- `npm run build`

## Risk

- Low data compatibility risk. The migration is scoped to maintainable template messages and does not touch prompt, status, or label text.
- Existing templates that intentionally used the literal characters `\n` will now render them as line breaks, which matches the reservation-share template contract.

## Rollback Notes

- Roll back backend by restoring the previous jar backup.
- If migration data rollback is required, restore affected `i18n_message_catalog` rows from the database backup taken before deployment.
