# Release Notes

## Version / Date

2026-07-07 - Public host certificate automation.

## New

- Added `public_host_bindings` to persist public hostname and TLS coverage status for tenant and store host prefixes.
- Added production reconciliation script `ops/reconcile-public-host-bindings.sh` to expand the current SAN certificate for pending hostnames without using a wildcard certificate.

## Changed

- Tenant and store host alias lifecycle now registers a pending public host binding when `rpb.host-prefix.base-host` is configured.
- Deleting, archiving, or deactivating a host alias archives the corresponding public host binding.

## Migration

- Adds Flyway migration `V040__public_host_bindings.sql`.
- The migration is additive and does not change existing tenant, store, or login API responses.

## Operations

- Copy `ops/reconcile-public-host-bindings.sh` to production, run it as a user allowed to execute certbot and reload nginx, and keep `RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg` configured.
- The script reads pending rows, keeps existing SAN domains, calls `certbot --nginx --expand`, reloads nginx, and updates rows to `covered` or `failed`.

## Security

- Certificate files and private keys are not stored in the business database.
- The application records hostname and TLS coverage status only.

## Risk

- SAN certificates have provider/domain count limits. If the SAN list grows too large, the script marks pending rows as `failed`; moving to a wildcard certificate remains the long-term simplification.
- The script must be run with production certbot/nginx privileges, so it should stay in controlled ops deployment rather than inside the Spring Boot application process.

## Rollback Notes

- Roll back the backend jar if application-level public host binding writes cause issues.
- If needed, stop running the reconcile script and leave `public_host_bindings` rows as operational metadata.

## Validation

- `mvn "-Dtest=PlatformTenantApiIntegrationTest#platformAdminListsRepairsCreatesDeletesAndRestoresTenants+creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope+creatingGroupTenantBootstrapsDefaultOperatingEntityWithoutStore+platformAdminCreatesOperatingEntityStoreAndAuthorizesTenantAdminAcrossStores,PlatformTenantStructureMigrationSourceValidationTest,PublicHostBindingAutomationScriptValidationTest" test`
