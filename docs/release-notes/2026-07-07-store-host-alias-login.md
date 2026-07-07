# Store Host Alias Login

## Version / Date

2026-07-07

## New

- Platform store create/update now persists each store code as a `tenant_host_aliases` store alias.
- Active store aliases can be used as login host prefixes, for example `lsc83.booking.yumstone.sg/login`.

## Changed

- Tenant login resolution now accepts either an active `tenants.tenant_code` or an active host alias owned by an active tenant.
- Store aliases only resolve when their default store is active and not deleted.

## Fixed

- New stores such as `lsc83` under the `lsc106` tenant can be reached by their store code subdomain after platform provisioning and authorization.

## Migration

- Added `V037__tenant_store_host_alias_backfill.sql`.
- The migration backfills active store aliases only when a store code is globally unique among active stores and does not collide with an active tenant code.
- Existing ambiguous store codes are intentionally skipped and must be assigned an explicit alias later.

## Permission

- No new App Gate permissions.
- Store entry remains controlled by existing `auth_account_store_access`; host alias resolution does not grant store access by itself.

## Risk

- A store code that conflicts with another active tenant code or existing alias now causes platform store create/update to fail with the existing store-code conflict path.
- Inactive stores keep an inactive alias and cannot be used as login host prefixes.

## Rollback Notes

- Roll back the backend jar to the previous version and restore the pre-deploy database backup if V037 has already applied.
- Without database restore, delete the newly backfilled `tenant_host_aliases` rows only after confirming no active production store depends on those host prefixes.
