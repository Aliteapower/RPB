# 2026-07-05 Frontend I18n Full Page Migration

## Summary

Completed the frontend hardcoded-copy migration for the remaining Vue pages.

## Changes

- Migrated remaining page-level Chinese UI copy into frontend locale data.
- Added generated locale dictionaries for legacy page text, with a documented path to promote reused keys into curated module namespaces.
- Added a frontend source guard that fails when `.vue` or `.ts` frontend source files contain hardcoded Chinese outside locale dictionaries and tests.
- Kept database `i18n_message_catalog` unchanged; configurable tenant/store copy remains a later backend/catalog API slice.

## Validation

- `npm run build`
- `mvn -q "-Dtest=FrontendHardcodedChineseMigrationValidationTest" test`
