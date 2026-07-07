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

## Rollback Notes

- Revert the platform store request field additions, store admin account repository/service calls, UI fields, API contract, tests, and this release note.
- No schema rollback is required.
