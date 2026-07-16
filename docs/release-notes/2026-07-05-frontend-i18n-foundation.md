# Release Notes

## Version / Date

2026-07-05 frontend i18n foundation round 1.

## New

- Added the Vue i18n frontend foundation with `zh-CN` fallback and first-round `en-SG` support.
- Added locale dictionaries for shared shell copy, navigation, login, App Gate friendly errors, reservation-create error messages, and Store Staff Home copy.
- Added frontend i18n source-validation coverage for the first migration slice.

## Changed

- Platform, tenant, and staff navigation now render labels through i18n keys.
- Login visible copy and login error text now resolve through i18n keys.
- Store Staff Home visible copy, aria labels, KPI labels, row labels, and fallback App Gate messages now resolve through i18n keys.
- Password visibility labels now resolve through i18n keys.
- Architecture and frontend docs now clarify that frontend shell fallback is `zh-CN`, while Store operational locale remains the long-term display context.

## Fixed

- Reduced hardcoded Chinese text in the shared frontend shell and Staff Home migration slice.

## Migration

- No database migration.
- No i18n catalog seed data.
- No backend API change.

## Permission

- No App Gate permission changes.
- No role or tenant/store authorization changes.

## Risk

- Frontend bundle size increases because `vue-i18n` is now included.
- Pages outside this first migration slice still contain hardcoded Chinese and should be migrated in later rounds.
- Runtime locale switching UI is not introduced in this round; locale resolution uses stored/browser fallback behavior.

## Rollback Notes

- Revert `vue-i18n` from `package.json` and `package-lock.json`.
- Revert `src/i18n/**`, `src/main.ts`, the migrated frontend files, and the i18n docs/release note.
- No database rollback is required.
