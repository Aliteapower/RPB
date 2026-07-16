# Frontend I18n Foundation

## Scope

This frontend i18n foundation covers the shared shell, App Gate friendly errors, login copy, navigation, staff home, shared staff controls, reservation workbench components, platform/tenant admin pages, public booking, queue, seating, table, walk-in, and cleaning frontend pages.

The migration uses two frontend locale data layers:

- Curated module dictionaries in `src/i18n/locales/zh-CN.ts` and `src/i18n/locales/en-SG.ts` for shared modules and high-change components.
- Generated page dictionaries in `src/i18n/locales/generated-*.ts` for remaining page-level text extracted mechanically from the legacy UI. These keys keep the source code free of hardcoded display copy and can be incrementally promoted into curated module dictionaries.

## Default Locale Strategy

- Frontend fallback locale: `zh-CN`.
- Supported first-round locales: `zh-CN`, `en-SG`.
- A global `FrontendLocaleSwitcher` is mounted from `App.vue` so public pages, login, and authenticated workspaces all expose the same locale entrypoint.
- Store locale remains the operational display context for date, time, currency, and later tenant/store-specific display behavior.
- If no user preference is stored, the frontend may use browser language when it maps to a supported locale.
- If browser language is unsupported, the frontend falls back to `zh-CN`.

This keeps the current Chinese-first admin/staff UI stable while preserving the architecture rule that Store operational display context can still be `en-SG`.

## Catalog Boundary

Do not move all static UI labels into `i18n_message_catalog`. Static application shell copy should live in frontend locale dictionaries. The database catalog remains reserved for configurable platform, tenant, or store messages, including reason labels, status display overrides, and future tenant-managed wording.

## Backend Catalog Administration

The catalog administration round adds a controlled backend registry for platform defaults and tenant/store overrides:

- `i18n_message_key_registry` defines which keys are maintainable, their namespace, text kind, tenant editability, placeholder allowlist, status, and sort order.
- `i18n_message_catalog` stores platform defaults, tenant overrides, and store overrides. Runtime resolution follows `store override -> tenant override -> platform default -> frontend fallback`.
- Platform admin can maintain platform default messages through `/api/v1/platform/i18n/catalog`.
- Tenant admin can maintain only registry-authorized tenant/store override messages through `/api/v1/stores/{storeId}/tenant-admin/i18n/catalog`.
- Static product UI copy, login copy, navigation, permission errors, route names, API paths, and generated page dictionaries remain source-controlled frontend locale data unless a specific key is deliberately promoted into the backend registry.

Runtime consumers of configurable copy must use the backend i18n resolver instead of translating locally in Vue pages or querying `i18n_message_catalog` directly from their own module. The resolver supports `store override -> tenant override -> platform default -> zh-CN fallback`; frontend dictionaries remain the fallback for static product UI only.

The backend catalog migration also imports configurable business copy from existing data sources without changing their runtime ownership in the same release:

- Platform reservation share template seeds become platform catalog defaults.
- Customized store reservation share templates and arrival notes become store overrides.
- Platform and store reservation meal period display names become catalog messages.
- Platform call-screen text seed slides become platform defaults, and tenant text-slide edits linked to platform seeds become tenant overrides.

## Migration Rule

When migrating a Vue page:

- Replace visible static text with `t('...')`.
- Keep route names, permission strings, API paths, status codes, and idempotency keys unchanged.
- Use existing API `messageKey` values as lookup inputs for frontend messages.
- Keep user-entered text and business data as data, not translation keys.
- Add or update source-validation tests for the migrated slice.
- Prefer curated module namespaces for shared components and behavior. Use generated page keys only as a mechanical bridge for legacy page copy.
- When touching a generated page key for product work, promote that key into a named module namespace if it is shared, reused, or business-significant.

## Initial Foundation Non-Scope

The first frontend foundation migration intentionally had no backend API change, no database migration, no `i18n_message_catalog` seed data, and no backend resolver/catalog API. Those items are now handled separately by the backend catalog administration round above.
