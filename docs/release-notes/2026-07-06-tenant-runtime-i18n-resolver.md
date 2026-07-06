# Release Notes

## Version / Date

2026-07-06 tenant runtime i18n resolver.

## New

- Added a backend runtime i18n resolver for persisted configurable copy.
- Queue Display state can now resolve text ad slide copy by locale from `i18n_message_catalog`.

## Changed

- `GET /api/v1/stores/{storeId}/queue-display/state` accepts optional `locale=zh-CN|en-SG`.
- Queue Display sends the active frontend locale when loading state and refreshes state after locale changes.
- Runtime configurable copy resolution uses store override, tenant override, platform default, then `zh-CN` fallback.

## Fixed

- Tenant-maintained English call-screen text no longer remains Chinese when the Queue Display is switched to English.
- Queue Display now consumes persisted tenant/platform i18n catalog messages instead of relying only on legacy text-slide fields.

## Migration

- No database migration.
- Existing V028 `i18n_message_catalog` and `i18n_message_key_registry` data is reused.

## Permission

- No App Gate permission changes.
- Queue Display continues to require `reservation_queue` / `queue.display.view`.

## Risk

- API compatibility risk is low because `locale` is optional and the response shape is unchanged.
- Runtime risk is limited to configurable text projection; queue state transitions, call/skip/seat actions, and media authorization are unchanged.
- If a key has no persisted message, legacy slide text remains the final fallback.

## Rollback Notes

- Roll back by redeploying the previous backend jar and frontend bundle.
- No database rollback or data repair is required.
