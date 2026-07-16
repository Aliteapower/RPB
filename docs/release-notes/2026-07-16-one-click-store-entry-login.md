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

## Deployment

- Deployed source commit `9e86b1c41ef01a3ae49aaa06ff38f2f046812f06` to the shared production environment on 2026-07-16 at 17:57 SGT.
- The deployed backend JAR SHA-256 is `06f3324bc3538a63647ae739322cc6057c68ebc408a1ceb84713071ff2f6d1f7`; the uploaded frontend archive SHA-256 is `003fc2b05195cfae143deee4108b37896d20a0fbb2380bd3ccc666f78cb9cd17`.
- `booking`, `platform`, `20000000`, `lsc106`, and `lsc83` login pages returned HTTP 200 with `/assets/index-DT9A_Bpd.js`; the tenant booking entry also returned HTTP 200.
- The backend remained `active`, unauthenticated `/api/v1/auth/me` returned HTTP 401, and the post-start error log count was zero.
- No migration ran. Production Flyway history remained successful through version `045`.
- The pre-switch full backup is `/opt/rpb/backups/20260716-175742-9e86b1c4-full`.

## Rollback Notes

- Restore `/opt/rpb/backups/20260716-175742-9e86b1c4-full/reservation-platform.jar` to `/opt/rpb/app/reservation-platform.jar` and restore that backup's `frontend` contents to `/opt/rpb/frontend`.
- Restart `rpb-backend`, then verify the service is active and unauthenticated `/api/v1/auth/me` returns HTTP 401.
- No database or data rollback is required.
