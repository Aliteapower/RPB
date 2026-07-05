# Release Notes

## Version / Date

2026-07-05 tenant administrator self-maintenance

## New

- Tenant backend Employee Management now shows the current tenant administrator as a protected administrator row.
- Tenant administrators can edit their own administrator account name, phone, email, and optional 6-character password from tenant backend Employee Management.
- Added explicit self-maintenance API:
  - `GET /api/v1/stores/{storeId}/tenant-admin/staff/me`
  - `PATCH /api/v1/stores/{storeId}/tenant-admin/staff/me`

## Changed

- Tenant admin staff responses now include additive fields: `accountType`, `self`, `editable`, and `statusEditable`.
- The normal staff edit endpoint remains limited to `actor_type = 'staff'`; tenant administrator accounts are maintained only through `staff/me`.
- Tenant administrator self-maintenance writes an `audit_logs` entry with operation code `tenant_admin.account.self_update`.

## Fixed

- Tenant administrators no longer need a platform administrator for routine self profile and password maintenance.
- Ordinary tenant staff still cannot access tenant admin staff management or maintain the tenant administrator account.

## Migration

- No database migration. Existing `auth_accounts`, `auth_account_roles`, `auth_account_permissions`, and `auth_account_store_access` fields already support the feature.

## Permission

- No new permission codes.
- Existing tenant admin requirement remains `role = tenant_admin` and `permission = tenant.admin.manage`.
- Store scope still requires the authenticated account to have access to the requested `storeId`.

## Risk

- API compatibility risk is low because response fields are additive.
- Security risk is controlled by resolving the editable administrator account from the authenticated `actorId`, never from a request-provided account id.
- Operational risk is moderate because backend and frontend are deployed together.
- Password values are not written to audit metadata; only `passwordChanged` and changed field names are recorded.

## Rollback Notes

- Roll back by redeploying the previous backend jar and previous frontend directory.
- No migration rollback or data repair is required.

## Validation

- `mvn "-Dtest=TenantAdminApiIntegrationTest,AuthLoginUiValidationTest" test` passed: 22 tests.
- `npm run build` passed with the existing Vite chunk-size warning.
