# Release Notes

## Version / Date

2026-07-07 - Tenant code host alias persistence.

## New

- Platform tenant creation now persists the tenant code as a `tenant_host_aliases` tenant alias.
- Existing non-deleted tenants are backfilled with tenant-code aliases when the prefix is not already used by another active alias.

## Changed

- Tenant status changes now keep the tenant alias status aligned with the tenant lifecycle.
- Tenant soft delete archives the tenant alias; restore recreates or reactivates the tenant alias.

## Fixed

- Fixed the gap where a newly created tenant prefix could work through direct tenant-code lookup but was not persisted in the host alias model.

## Migration

- Adds Flyway migration `V039__tenant_host_alias_backfill.sql`.
- The migration inserts only `alias_type = tenant` rows and does not create store aliases or stores.

## Permission

- No new permission codes.
- The behavior stays behind existing platform tenant management permissions.

## Risk

- Low data risk. The migration is additive and skips prefixes that are already used by an active alias.
- HTTPS availability for a new prefix still depends on the production TLS certificate covering that hostname, or on moving production to a wildcard certificate.

## Rollback Notes

- Roll back backend and Flyway together if alias persistence causes issues.
- If data rollback is required, remove only V039-created `tenant_host_aliases` rows where `alias_type = tenant` and the alias is not used by live traffic.

## Validation

- `mvn "-Dtest=PlatformTenantApiIntegrationTest#platformAdminListsRepairsCreatesDeletesAndRestoresTenants+creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope+creatingGroupTenantBootstrapsDefaultOperatingEntityWithoutStore+tenantHostAliasBackfillPersistsExistingTenantCodesAsPrefixes,AuthApiIntegrationTest#storeHostAliasResolvesToOwningTenantForLogin,AuthMigrationTest#backfillsUniqueActiveStoreHostAliases,PlatformTenantStructureMigrationSourceValidationTest" test`
