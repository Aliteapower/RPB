# Release Notes

## Version / Date

2026-09-03 public booking optional customer login flow.

## New

- Added a focused frontend behavior test for the persisted public-booking customer-login policy.

## Changed

- Public booking now derives its next, previous, and displayed step numbers from each Store's persisted `requireCustomerLogin` setting.
- When customer login is not required, the time-selection step proceeds directly to contact details and reservation submission.
- When customer login is required, the existing email, Google, or Facebook login step remains enforced; an already authenticated customer can continue directly.

## Fixed

- Fixed anonymous public booking being blocked by an unconditional frontend customer-session requirement even though the backend already allowed anonymous creation for Stores configured without required login.
- The fix is system-wide and configuration-driven; it contains no Tenant or Store special case.

## Migration

- No database migration.
- No data change. Existing per-Store public-booking settings remain the source of truth.

## Permission

- No App Gate, role, or permission change.

## Deployment

- Frontend release commit: `41df0502`.
- Uploaded artifact: `/home/ubuntu/rpb-41df0502-frontend.tgz`.
- Artifact SHA-256: `5b31f327d53a1c60ff30e85f6f8c86e3359e96d666a3bfe8a92316023c8d548c`.
- Production frontend backup: `/opt/rpb/backups/20260903-220649-41df0502-public-booking-optional-login-frontend/frontend`.
- Previous frontend directory: `/opt/rpb/frontend.previous-20260903-220649-41df0502-public-booking-optional-login`.
- Live entry asset: `/assets/index-Eh9OHRe-.js`.
- The Lsc83 Store entry and booking context returned `200`; the context returned persisted `requireCustomerLogin=false`, `emailAuthEnabled=false`, and no OAuth providers.
- The live public-booking asset contains the configuration-driven login-flow policy. Lsc83 was used only as a production smoke sample; the implementation contains no Store or Tenant identifier.
- Backend service remained active and `/api/v1/auth/me` returned `401` throughout the frontend-only deployment.

## Risk

- Low frontend workflow risk. Reservation creation API behavior and server-side login enforcement are unchanged.
- A missing or not-yet-loaded setting fails closed and continues to require login before contact details can be entered.

## Rollback Notes

- Restore `PublicBookingPage.vue` to require an authenticated customer before entering contact details and submitting.
- Remove `publicBookingFlow.ts` and its focused regression test if the previous unconditional-login frontend behavior is intentionally restored.
