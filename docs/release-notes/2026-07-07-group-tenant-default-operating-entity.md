# Release Notes

## Version / Date

2026-07-07 - Group tenant default operating entity.

## New

- Group multi-store tenant creation now prepares a default operating entity immediately, while still leaving stores empty for platform admins to add branches intentionally.
- Existing non-deleted tenants without an operating entity are backfilled with a default operating entity based on their tenant code, name, status, locale, contact phone, address, and principal.

## Changed

- Platform tenant structure copy now frames the next action as adding a branch under the default operating entity.
- The store creation action label changed from "Add store" / "新增门店" to "Add branch" / "新增分店" in the tenant structure workflow.

## Fixed

- Fixed the group setup confusion where a newly created group such as `lcd` appeared to require creating another business entity before the user could add its branches.

## Migration

- Adds Flyway migration `V038__tenant_default_operating_entity_backfill.sql`.
- The migration inserts only missing `operating_entities`; it does not create stores or tenant admin store access rows.

## Permission

- No new permission codes.
- Platform tenant structure changes remain under existing `platform.tenant.manage`.

## Risk

- Low data risk. The migration is additive and skips tenants that already have an operating entity.
- Group tenant admins still have no default store access until platform users create stores and explicitly authorize them.

## Rollback Notes

- Roll back backend and frontend together if the new onboarding behavior causes issues.
- If data rollback is required, remove only default operating entities created for tenants that still have no stores and no explicit business configuration, or restore from the pre-deployment database backup.

## Validation

- `mvn "-Dtest=PlatformTenantApiIntegrationTest#creatingGroupTenantBootstrapsDefaultOperatingEntityWithoutStore+tenantStructureBackfillCreatesDefaultOperatingEntityForExistingGroupTenant,PlatformTenantStructureMigrationSourceValidationTest,PlatformGroupTenantOnboardingUiValidationTest,AuthLoginUiValidationTest" test`
- `npm run build`
