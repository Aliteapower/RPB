# Release Notes

## Version / Date

2026-07-17 - Operating entity deletion

## New

- Platform administrators can delete an operating entity when it has no current stores. The final no-store operating entity can also be deleted, and the add-entity action remains available.
- The platform tenant page now shows the localized delete action only for eligible entities, requests confirmation, prevents duplicate submissions, refreshes the structure after success, and explains the current-store conflict.
- Added `DELETE /api/v1/platform/tenants/{tenantId}/operating-entities/{operatingEntityId}`. Every non-deleted current-store assignment returns HTTP 409 with `OPERATING_ENTITY_HAS_STORES`, regardless of whether the store status is `created`, `active`, or `inactive`.

## Changed

- Successful deletion archives and soft-deletes the operating entity while retaining its history and writing an audit record.
- Soft-deleted historical stores do not prevent deletion; every store with `deleted_at is null` does, regardless of status.

## Fixed

- Platform administrators no longer need to retain an unused operating entity merely because the tenant has no other entities.

## Migration

- No database migration, dependency, or runtime configuration change is required. Existing entity history is retained through the existing soft-delete fields.

## Permission

- The additive endpoint requires the existing `platform_admin` role and `platform.tenant.manage` permission. No App Gate permission code or entitlement changes were made.
- The local validation profile's source allowlist includes the new route and continues to use its existing configured actor; this code wiring requires no external runtime configuration.

## Risk

- API risk is low and additive; existing clients remain compatible.
- Tenant scope and cross-tenant not-found behavior are preserved. Row locking serializes deletion against a concurrent store assignment.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows are unchanged.

## Validation

- `mvn -q "-Dtest=AuthLoginUiValidationTest" test` passed: 16 tests.
- `npx vitest run src/components/platform/__tests__/PlatformTenantStructurePanel.behavior.spec.ts src/pages/__tests__/PlatformTenantFormPage.behavior.spec.ts src/api/platformApi.spec.ts` passed: 11 tests across 3 files.
- `npm run build` passed.
- `mvn -q "-Dtest=PlatformTenantApiIntegrationTest,PlatformTenantLocalRuntimeSecurityTest,AuthLoginUiValidationTest" test` passed: 47 tests (23 platform tenant API, 8 local runtime security, 16 frontend source guards).
- `mvn -q -DskipTests package` passed. The full PostgreSQL-dependent integration suite was not run.

## Rollback Notes

- Remove the frontend action and the additive endpoint in a follow-up release, then redeploy the prior compatible application version.
- Previously soft-deleted entities remain retained historical records; no schema or data rollback is required.
