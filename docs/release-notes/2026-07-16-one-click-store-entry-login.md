# One-Click Store Entry Login

## Version / Date

2026-07-16

## New

- `POST /api/v1/auth/login` now returns nullable `entryStoreId` when the current host is an authorised store alias.
- The entry store is login-request context only and remains separate from the account's persistent `defaultStoreId`.

## Changed

- Tenant staff and tenant administrators now enter the current store-domain workbench immediately after successful login.
- Tenant-level and legacy login fall back to the account default store, then the first authorised store.
- Store switching remains available inside staff and tenant-admin workbenches.

## Fixed

- Multi-store staff no longer need to select an already resolved store and click a second "Enter store" button after authentication.

## Migration

- No database schema, Flyway migration, data migration, or dependency change is required.

## Permission

- No App Gate permission or allowlist changes are required.
- Store aliases still require existing `auth_account_store_access`; an unauthorised store-alias login remains `INVALID_CREDENTIALS`.

## API Compatibility

- The response change is additive. Older frontends ignore `entryStoreId`.
- `AuthUser.defaultStoreId` and `GET /api/v1/auth/me` remain unchanged.
- Deploy the backend before or together with the frontend.

## Risk

- Correct direct entry depends on the reverse proxy continuing to forward the public host used by the existing host-prefix resolver.
- An inconsistent `entryStoreId` is rejected by the frontend unless it is present in the authenticated user's `storeIds`; routing then falls back to the account default and first authorised store.
- Reservation, Queue, Walk-in, Seating, Cleaning, tenant data, and store data are not changed.

## Validation

- `npm run test:login-routing`
- `mvn -q "-Dtest=AuthApiIntegrationTest,AuthLoginUiValidationTest" test`
- `npm run build`
- `mvn -q -DskipTests package`
- `git diff --check 8f127a40..HEAD`

All commands completed successfully on 2026-07-16.

## Rollback Notes

- Restore the previous frontend login page and auth-session return type.
- Restore the previous backend login response and remove resolved entry-store propagation.
- No database or data rollback is required.
