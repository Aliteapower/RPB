# Tenant Administrator Self-Maintenance Design

Date: 2026-07-05

## Goal

Allow a tenant administrator to maintain their own administrator login from tenant backend Employee Management, including optional password change, without asking a platform administrator. The feature must not let a tenant administrator edit other administrator accounts, and ordinary tenant employees must not maintain the administrator account.

## Current State

Tenant administrator and staff logins are both stored in `auth_accounts`. Staff management currently filters only `actor_type = 'staff'`, while platform tenant management owns tenant administrator account creation and password reset through `PlatformTenantAdminAccountRepository`.

The existing schema already has the required columns: `display_name`, `contact_phone`, `email`, `password_hash`, `status`, `tenant_id`, `default_store_id`, and `auth_account_store_access`. No database migration is required.

## Chosen Approach

Add explicit self-maintenance endpoints under tenant admin staff management:

- `GET /api/v1/stores/{storeId}/tenant-admin/staff/me`
- `PATCH /api/v1/stores/{storeId}/tenant-admin/staff/me`

These endpoints map to the authenticated `tenant_admin` account id from `CurrentActor`. They do not accept an arbitrary admin account id, which keeps cross-admin modification out of the API contract. The normal staff list and staff update endpoints continue to operate only on `actor_type = 'staff'`.

## Rules

- A tenant administrator can read and update only their own `tenant_admin` account.
- Updatable fields are name, phone, email, and optional password.
- Username/employee number and status are not self-editable.
- Password remains exactly 6 ASCII letters or digits and is lowercased before BCrypt encoding to match existing login behavior.
- Ordinary staff remain forbidden from tenant admin APIs by `TenantAdminScopeResolver`.
- The tenant admin staff list may show the current administrator as a protected row or companion entry in the UI, but the backend keeps it separate from ordinary staff records.
- Platform administrators keep the existing platform tenant management path for tenant lifecycle and emergency password reset.

## API Contract

The API contract is documented in `docs/api/TENANT_ADMIN_ERP_API_CONTRACT.md`.

Response shape extends `TenantAdminStaffItemResponse` with:

- `accountType`: `"staff"` or `"tenant_admin"`
- `self`: `true` when the row represents the current account
- `editable`: whether the current UI should expose edit action
- `statusEditable`: `false` for tenant admin self rows

Existing staff responses remain backward-compatible because added JSON fields are additive.

## Frontend

`TenantAdminStaffPage.vue` loads normal staff plus the current admin account when the authenticated user is a tenant administrator. It displays the admin row first with a protected label and an edit action.

`TenantAdminStaffFormPage.vue` supports a self-admin mode via route `/stores/:storeId/admin/staff/me/edit`. In that mode it:

- uses `getCurrentTenantAdminStaff` and `updateCurrentTenantAdminStaff`
- keeps employee number read-only
- hides or disables status editing
- keeps password optional
- returns to the staff list after save

## Error Handling

Stable errors reuse tenant admin API codes:

- `UNAUTHENTICATED` -> 401
- `FORBIDDEN` -> 403
- `STORE_SCOPE_MISMATCH` -> 403
- `STAFF_NOT_FOUND` -> 404 when the authenticated tenant admin account cannot be found in the current tenant/store scope
- `REQUEST_INVALID` -> 400 for invalid payload or password
- `PERSISTENCE_ERROR` -> 500

## Test Checklist

- Backend integration: tenant admin can read `staff/me`.
- Backend integration: tenant admin can update their own name, phone, email, and password.
- Backend integration: updated password can be used to log in.
- Backend integration: normal `staff/{staffId}` does not expose or update `tenant_admin` accounts.
- Backend integration: ordinary staff calling `staff/me` receives `FORBIDDEN`.
- Backend integration: tenant admin using a store outside their scope receives `STORE_SCOPE_MISMATCH`.
- Frontend static validation: tenant admin staff list calls the self endpoint and renders a protected admin row.
- Frontend static validation: self-admin edit route uses self endpoint, hides status editing, and keeps employee number read-only.

## Deployment

This change requires backend and frontend deployment. Rollback is to redeploy the previous backend jar and previous frontend directory. No migration rollback is needed.
