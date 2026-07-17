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

## Deployment

- Deployed backend and frontend source commit `7d675ed6d82552ad21ed17b9bf82d2fb0a9c6220` to the shared `booking.yumstone.sg` production environment on 2026-07-17 at 18:03 SGT.
- Backend JAR SHA-256: `477e7d127affd9ddc82cb77dc8ce6b52a9b9d6e6eae61d5a3cd010fb6139f0ab`; frontend archive SHA-256: `fdf32a68e7bdd63b4e5ceaf26013090351186cd043146b12e9145237dadd43fa`.
- The pre-switch full backup is `/opt/rpb/backups/20260717-180339-7d675ed6-full`.
- The backend remained `active`, unauthenticated `/api/v1/auth/me` returned HTTP 401, and the post-start backend ERROR log count was zero.
- Production Flyway history remained successful through version `045`; no migration, data change, permission change, App Gate change, or environment change ran.
- The `booking`, `platform`, `20000000`, `lsc106`, and `lsc83` login entries and the `20000000` booking entry returned HTTP 200 with `/assets/index-CsT21tJv.js`. The operating-entity feature chunk returned HTTP 200 and contained the guarded delete flow.
- Authenticated Chrome verification on tenant `fb8e092d-aa34-42eb-bb55-5229044c3885` showed Delete for the no-store operating entity `麻辣烫 (ocd)` and no Delete action for `老成都 (lcd)`, which still owns a current store. No production entity was deleted during validation.

## Rollback Notes

- Restore `/opt/rpb/backups/20260717-180339-7d675ed6-full/reservation-platform.jar` to `/opt/rpb/app/reservation-platform.jar`, restore that backup's `frontend` directory to `/opt/rpb/frontend`, and restart `rpb-backend`.
- Verify the backend is active, unauthenticated `/api/v1/auth/me` returns HTTP 401, and production pages serve the previous frontend entry asset.
- Previously soft-deleted entities remain retained historical records; no schema or data rollback is required.
