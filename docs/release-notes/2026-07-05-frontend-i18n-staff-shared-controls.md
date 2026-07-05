# Release Notes

## Version / Date

2026-07-05 frontend i18n staff shared controls slice.

## New

- Added `staffControls` locale entries for staff business date switching, shared guest input controls, staff time picker, staff home workflow strip, and table resource picker.
- Added validation coverage for staff shared controls using i18n keys while preserving Chinese and English locale defaults.

## Changed

- Migrated staff shared Vue controls from hardcoded Chinese display text to `vue-i18n` lookups.
- Updated related UI validation tests to assert component key usage plus locale-owned default text.

## Fixed

- Prevented migrated shared controls from reintroducing hardcoded Chinese labels, aria text, status text, and empty states.

## Migration

- No database migration.
- No API contract change.
- No runtime configuration change.

## Permission

- No App Gate permission changes.
- No tenant or store authorization behavior changes.

## Risk

- Frontend-only display risk: missing or mistyped locale keys would surface fallback key text in the affected staff controls.
- Table resource picker status and unavailable reason labels now depend on locale keys, so future status additions should add locale entries with the component update.

## Rollback Notes

- Revert this slice's Vue component, locale, and validation test changes to restore previous hardcoded staff shared control text.
- No database or permission rollback is required.

## Validation

- `rg -n "\p{Han}"` over the migrated staff shared control files returned no matches.
- `npm run build` passed; Vite reported the existing large chunk warning.
- Targeted staff shared controls i18n Maven tests passed.
- `mvn -q test` passed.
