# Release Notes

## Version / Date

2026-07-05 tenant profile self-maintenance alignment.

## New

- Tenant backend administrator self-maintenance now includes tenant profile fields aligned with platform tenant editing: tenant code, tenant name, status, default locale, principal, phone, address, and tenant logo.
- The same page keeps administrator account maintenance for name, email, and optional password change.

## Changed

- The tenant administrator self route composes existing tenant profile APIs with the existing current-admin staff APIs.
- In self-maintenance mode, the account phone field is hidden; tenant contact phone is maintained from tenant profile data.
- Selecting a tenant logo and clicking the bottom save button uploads the selected logo after profile/account data saves.

## Fixed

- Platform and tenant backend no longer expose visibly different tenant-maintenance surfaces for the same tenant profile data.

## Migration

- No database migration.
- No backend API contract change.

## Permission

- No new App Gate permission.
- Existing tenant-admin self APIs and tenant profile APIs continue to enforce store scope and tenant-admin access.

## Risk

- Frontend-only composition change. The self page validates required tenant profile fields and optional password format before submitting the combined save, reducing partial-save cases before API calls are made.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows are not changed.

## Validation

- `mvn "-Dtest=AuthLoginUiValidationTest#tenantAdminStaffManagementSupportsProtectedSelfAdminMaintenance" test`: passed.
- `mvn "-Dtest=AuthLoginUiValidationTest,TenantAdminApiIntegrationTest" test`: passed, 22 tests.
- `npm run build`: passed.
- `git diff --check`: passed.

## Rollback Notes

- Roll back the deployed frontend assets to the previous `/opt/rpb/frontend` backup.
- No database rollback is required.
