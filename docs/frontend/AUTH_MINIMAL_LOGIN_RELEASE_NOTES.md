# Release Notes

## Version / Date

Auth minimal login closed loop / 2026-06-25

## New

- Added a minimal authentication loop: slider captcha, username/password login, database-backed session cookie, `/api/v1/auth/me`, and logout.
- Added local validation accounts for platform admin, tenant admin, and tenant staff:
  - `sysadmin` / `393930`
  - `20000000` / `393930`
  - `1000` / `393930`
- Added the Vue login page, auth API client, Pinia session store, and route guard for protected ERP routes.

## Changed

- Staff ERP pages now use the authenticated user's `/me` permissions for the compact operation toolbar.
- Local runtime actor resolution prefers the authenticated session actor before falling back to existing local defaults.

## Fixed

- Removed the staff home dependency on duplicated `/me/apps` entry-grid probing for the ERP operation toolbar.

## Migration

- Added `V003__auth_minimal_login.sql`.
- Creates auth account, role, permission, store-access, session, and slider-captcha tables.
- Seeds the three local validation accounts only when the local validation tenant/store baseline exists.

## Permission

- No new App Gate permission names were introduced.
- Seeded local auth accounts receive the current closed-loop reservation, queue, walk-in, seating, cleaning, and table permissions needed by the ERP pages.

## Risk

- Existing local runtime fallbacks remain in place for tests and local tools, so production hardening should still review session TTL, cookie secure settings, and account management before external deployment.
- Local validation data depends on the existing tenant/store baseline.

## Rollback Notes

- Revert the auth backend/frontend files and router guard changes.
- For a database rollback, drop the `auth_*` tables created by `V003__auth_minimal_login.sql` in reverse dependency order, then clear the `RPB_SESSION` browser cookie.
