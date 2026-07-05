# Release Notes

## Version / Date

2026-07-05 tenant admin staff list tenant phone display.

## New

- None.

## Changed

- Tenant backend staff management now displays the tenant profile phone for the protected tenant administrator row.
- Regular employee rows continue to display each employee account phone.

## Fixed

- Fixed the mismatch where platform tenant management showed the tenant phone, but tenant backend staff management showed `-` for the tenant administrator row.

## Migration

- No database migration.
- No backend API contract change.

## Permission

- No new App Gate permission.
- The page reuses the existing tenant-admin profile API, which is already store-scoped and tenant-admin protected.

## Risk

- Frontend-only display change. The staff list now depends on the existing tenant profile request already used elsewhere in the tenant backend.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows are not changed.

## Validation

- `mvn "-Dtest=AuthLoginUiValidationTest#tenantAdminStaffListShowsTenantProfilePhoneForProtectedAdmin" test`: failed before implementation, passed after implementation.
- `mvn "-Dtest=AuthLoginUiValidationTest" test`: passed, 11 tests.
- `npm run build`: passed.
- `git diff --check`: passed.

## Rollback Notes

- Roll back the deployed frontend assets to the previous `/opt/rpb/frontend` backup.
- No database rollback is required.
