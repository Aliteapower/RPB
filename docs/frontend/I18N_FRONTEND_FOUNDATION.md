# Frontend I18n Foundation

## Scope

This frontend i18n foundation covers the shared shell, App Gate friendly errors, login copy, navigation, staff home, shared staff controls, reservation workbench components, platform/tenant admin pages, public booking, queue, seating, table, walk-in, and cleaning frontend pages.

The migration uses two frontend locale data layers:

- Curated module dictionaries in `src/i18n/locales/zh-CN.ts` and `src/i18n/locales/en-SG.ts` for shared modules and high-change components.
- Generated page dictionaries in `src/i18n/locales/generated-*.ts` for remaining page-level text extracted mechanically from the legacy UI. These keys keep the source code free of hardcoded display copy and can be incrementally promoted into curated module dictionaries.

## Default Locale Strategy

- Frontend fallback locale: `zh-CN`.
- Supported first-round locales: `zh-CN`, `en-SG`.
- Store locale remains the operational display context for date, time, currency, and later tenant/store-specific display behavior.
- If no user preference is stored, the frontend may use browser language when it maps to a supported locale.
- If browser language is unsupported, the frontend falls back to `zh-CN`.

This keeps the current Chinese-first admin/staff UI stable while preserving the architecture rule that Store operational display context can still be `en-SG`.

## Catalog Boundary

Do not move all static UI labels into `i18n_message_catalog`. Static application shell copy should live in frontend locale dictionaries. The database catalog remains reserved for configurable platform, tenant, or store messages, including reason labels, status display overrides, and future tenant-managed wording.

## Migration Rule

When migrating a Vue page:

- Replace visible static text with `t('...')`.
- Keep route names, permission strings, API paths, status codes, and idempotency keys unchanged.
- Use existing API `messageKey` values as lookup inputs for frontend messages.
- Keep user-entered text and business data as data, not translation keys.
- Add or update source-validation tests for the migrated slice.
- Prefer curated module namespaces for shared components and behavior. Use generated page keys only as a mechanical bridge for legacy page copy.
- When touching a generated page key for product work, promote that key into a named module namespace if it is shared, reused, or business-significant.

## Non-Scope

- No backend API change.
- No database migration.
- No `i18n_message_catalog` seed data.
- No backend message resolver or catalog API.
