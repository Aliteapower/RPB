# Reservation Queue App Gate Foundation V1 Implementation Report

Date: 2026-06-19

## Development Basis

- `docs/backend/APP_GATE_FOUNDATION_V1_BRIEF.md`
- `docs/superpowers/plans/2026-06-19-app-gate-foundation-v1.md`

## Scope Completed

1. Added `V002__app_gate_foundation.sql`
   - Created `platform_apps`, `tenant_app_entitlements`, `store_app_settings`, and `app_gate_audit_logs`.
   - Seeded `reservation_queue`.
   - Backfilled existing tenants and stores as enabled and visible to avoid surprise 403s after migration.

2. Added App Gate backend foundation
   - Added decision model, deny reasons, app entries, access requests, command service, JPA entities, and repositories.
   - Implemented fixed decision order:
     `platform app active -> tenant enabled/trial valid -> store enabled -> actor store scope -> permission`.
   - Added audit writing for tenant/store app enable, disable, suspend, visibility, and config updates.

3. Added unified API guard
   - Added `@RequireAppGate`.
   - Registered MVC interceptor.
   - Guarded:
     - `POST /api/v1/stores/{storeId}/reservations` with `reservation.create`
     - `POST /api/v1/stores/{storeId}/walk-ins/direct-seating` with `walkin.direct_seating.create`
     - `POST /api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start` with `cleaning.start`
     - `POST /api/v1/stores/{storeId}/cleanings/{cleaningId}/complete` with `cleaning.complete`
   - Kept existing permission keys; no API path or business state-machine rewrite.

4. Unified gate error response
   - Gate denials return HTTP 403 in existing shape:
     `{ "success": false, "error": { "code": "...", "messageKey": "...", "details": {} } }`
   - Implemented codes including `APP_DISABLED`, `TENANT_APP_NOT_ENABLED`, `TENANT_APP_EXPIRED`, `STORE_APP_NOT_ENABLED`, `STORE_ACCESS_DENIED`, and `PERMISSION_DENIED`.

5. Added `/api/me/apps?storeId=xxx`
   - Returns visible and usable app entries for the current actor/store.
   - `entryVisible=false` hides the entry.
   - API access remains enforced by backend guard.

6. Updated frontend staff home
   - Added `src/api/meAppsApi.ts` and `src/types/meApps.ts`.
   - `StoreStaffHomePage.vue` now calls `/api/me/apps` and renders reservation queue operations only when `reservation_queue` is visible for that store.

7. Updated tests and regression fixtures
   - Added migration, AppGate service, command service, guard, and `/api/me/apps` tests.
   - Updated integration fixtures to seed app gate entitlements/settings after test tenants/stores are created.
   - Updated boundary tests for the new app gate controller and the existing DateTime wheel component.

## Smoke And Verification

- `mvn test`
  - Passed.
  - 249 tests, 0 failures, 0 errors.

- `npm run build`
  - Passed.
  - `vue-tsc --noEmit` and Vite production build completed.

- Focused business-chain smoke:
  - `WalkInDirectSeatingApiIntegrationTest`, `ReservationCreateApiIntegrationTest`, and `CleaningCompleteApiIntegrationTest` passed together.
  - Confirms existing reservation/walk-in/cleaning paths still run after the gate is enabled.

## Notes

- Existing controller role checks still return their original `FORBIDDEN` where the request passes App Gate but fails business-role validation.
- Missing app permission is now rejected by App Gate as `PERMISSION_DENIED`.
- Store-scope gate denial is `STORE_ACCESS_DENIED` after the app is enabled for that target store.
- Maven output still includes non-blocking Mockito dynamic-agent deprecation warnings and Spring Data Redis repository-assignment info logs.
