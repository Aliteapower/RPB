# Release Notes

## Version / Date

2026-07-07 - Platform branch administrator passwords

## New

- Platform tenant store create/update requests now accept optional `adminUsername` and `adminPassword` fields.
- Platform administrators can create or maintain a branch store administrator account separately from the tenant group administrator account.
- The platform branch store form now shows branch administrator username and password fields.

## Changed

- Group tenant creation labels distinguish the group administrator initial password from branch administrator passwords.
- Branch administrator accounts are persisted as store-scoped staff accounts with the `store_manager` role and only the target store in their access scope.

## Fixed

- Group administrator password and branch administrator password are no longer forced through one ambiguous tenant password field during group onboarding.

## Migration

- No database migration.
- Uses existing `auth_accounts`, `auth_account_roles`, `auth_account_permissions`, and `auth_account_store_access` tables.

## Permission

- No new App Gate permission or platform permission.
- Existing platform tenant management permission still gates the platform tenant/store APIs.

## Risk

- `auth_accounts.username` remains globally unique, so branch administrator usernames can conflict with existing accounts.
- Existing clients that omit `adminUsername` and `adminPassword` keep the current store-only behavior.

## Production Deployment

- Deployed backend and frontend commit: `5c63c21e`.
- Production server: `booking.yumstone.sg` on `43.134.69.75`.
- Production backup directory: `/opt/rpb/backups/20260708-052304-5c63c21e`.
- Live frontend entry asset: `/assets/index-Donso0DW.js`.
- Live platform tenant edit asset: `/assets/PlatformTenantFormPage-BmnqHe6X.js`.
- Backend jar contains `PlatformStoreAdminAccountRepository`.
- Smoke checks: `rpb-backend` active, local `/api/v1/auth/me` returned `401`, public `https://booking.yumstone.sg/api/v1/auth/me` returned `401`, `/login` returned `200`, `/platform/tenants/{tenantId}/edit` returned `200`, `platform.booking.yumstone.sg/login` returned `200`, `20000000.booking.yumstone.sg/login` returned `200`, and `20000000.booking.yumstone.sg/book` returned `200`.
- Public platform tenant edit bundle contains `adminUsername`, `adminPassword`, and `branchAdminAccount`.

## Rollback Notes

- Revert the platform store request field additions, store admin account repository/service calls, UI fields, API contract, tests, and this release note.
- No schema rollback is required.
