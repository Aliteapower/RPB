# Release Notes

## Version / Date

2026-07-07 - Management tenant multi-store operating entities foundation

## New

- Added `operating_entities` as the business-operator submodel under a management tenant.
- Added `stores.operating_entity_id` so each store can be tied to an operating entity while remaining tenant-scoped.
- Added `tenant_host_aliases` schema foundation for later mapping legacy prefixes such as `lsc106` and `20000000` to a management tenant and default store.
- Added platform APIs to list, create, and update tenant operating entities and stores.
- Added platform tenant edit UI panels for operating entities and stores, with zh-CN and en-SG copy.
- Added a platform tenant list row action that jumps directly to the operating-entity and store management panel.

## Changed

- Extended `/api/v1/me/stores` and platform tenant admin store options with `tenantId`, `tenantCode`, `operatingEntityId`, and `operatingEntityName`.
- Store switchers and staff authorization labels can now show the operating entity beside the store name.
- Platform tenant editing now supports the long-term model where one tenant manages multiple stores that may belong to different operating entities.

## Fixed

- Closed the product gap where platform admins could authorize tenant admins to existing stores but could not create or assign the additional stores needed for a management-tenant model.

## Migration

- Adds Flyway migration `V035__tenant_operating_entities_and_store_structure.sql`.
- `stores.operating_entity_id` is nullable for the compatibility window; existing stores continue to load before backfill.
- This release does not merge or move production data for `lsc106` or `20000000`.
- Production consolidation of existing tenant/store data remains a separate, parameterized operational migration.

## Permission

- Reuses existing `platform.tenant.manage` for platform operating-entity and store management.
- No tenant-admin permission is added for creating stores or operating entities.
- Tenant-side store switching remains driven by explicit `auth_account_store_access`.

## Risk

- Medium database compatibility risk because new schema objects and a nullable store column are introduced.
- Platform store creation now requires an active operating entity in the same tenant.
- Host alias runtime resolution is not enabled in this slice; the alias table is schema foundation only.
- Existing one-store tenants remain compatible, but production data consolidation must be rehearsed separately before `lsc106` and `20000000` are moved under one management tenant.

## Rollback Notes

- Roll back by reverting the backend/frontend commit and restoring the database before Flyway V035, or by applying an explicit rollback migration in environments where destructive rollback is not allowed.
- Do not partially roll back only the frontend after data starts using operating entities, because platform users would lose the management UI for the new records.
- Do not run the later `lsc106` / `20000000` consolidation migration unless this foundation release is deployed and verified.

## Validation

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=PlatformTenantStructureMigrationSourceValidationTest,PlatformTenantLocalRuntimeSecurityTest,AuthLoginUiValidationTest" test`
- `mvn -q "-Dtest=PlatformTenantApiIntegrationTest#platformAdminCreatesOperatingEntityStoreAndAuthorizesTenantAdminAcrossStores" test`
- `npm run build`
