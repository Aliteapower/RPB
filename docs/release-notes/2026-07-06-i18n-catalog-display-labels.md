# 2026-07-06 i18n catalog display labels

## Fixed

- Platform and tenant i18n catalog namespace filters now display localized labels instead of raw internal keys such as `call_screen`.
- Catalog row metadata now displays localized namespace, category, and text-kind labels while keeping the stable API keys as filter values.

## Migration

- No database migration.
- No API contract, permission, or App Gate changes.

## Validation

- `I18nCatalogAdminUiValidationTest`
- `npm run build`

## Risk

- Low frontend-only display risk. Filtering still uses the original namespace key, so saved catalog data and API behavior are unchanged.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle.
