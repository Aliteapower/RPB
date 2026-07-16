# Release Notes

## Version / Date
2026-07-05 password visibility toggle

## New
- Added a reusable password input control with an eye icon toggle for showing and hiding entered secrets.

## Changed
- Login password, platform tenant admin password, tenant staff password, SMTP secret, and Facebook App Secret fields now support show/hide password.

## Fixed
None.

## Migration
No database migration.

## Permission
No App Gate or permission change.

## Risk
Low frontend-only risk. Password payloads, validation rules, API contracts, and saved secret handling are unchanged.

## Rollback Notes
Revert `src/components/common/PasswordInput.vue`, the affected Vue form replacements, the password visibility UI validation test, and this release note.
