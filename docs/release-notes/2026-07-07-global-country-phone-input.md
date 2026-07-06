# Release Notes

## Version / Date

2026-07-07 global country phone input.

## New

- Added a shared country phone utility with Singapore `+65` / 8-digit local number defaults and a China `+86` / 11-digit configuration for future expansion.
- Added `CountryPhoneField`, a reusable Vue phone input that displays a fixed country prefix while users type only the local national number.

## Changed

- Platform profile, platform tenant, tenant profile, tenant staff, tenant customer, tenant reservation share, and public booking phone editors now use the shared country phone input.
- Existing staff guest phone entry keeps its `StaffSingaporePhoneField` API but delegates rendering and sanitization to the shared component.
- Public booking no longer carries page-local phone input CSS; it uses the same shared component as admin screens.

## Fixed

- Removed remaining customer-facing and admin form placeholders that asked users to type full `+65...` phone numbers directly.

## Migration

- No database migration.
- Existing API fields remain unchanged and continue to submit E.164 values such as `+6591234567`.

## Permission

- No App Gate or permission changes.

## Risk

- Low UI compatibility risk: phone editing is now Singapore-local by default and preserves the existing backend payload contract.
- Non-Singapore legacy values are not silently coerced into Singapore numbers; they remain as stored values until a user enters a new local number.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle or reverting the shared `CountryPhoneField` adoption changes.
- Backend rollback is not required because endpoints, DTOs, permissions, and database schema are unchanged.

## Deployment Validation

- Deployed the frontend bundle to `booking.yumstone.sg` on 2026-07-07.
- Production frontend backup: `/opt/rpb/backups/frontend-20260707062826`.
- Smoke checks passed:
  - `https://booking.yumstone.sg/api/v1/auth/me` returned `401` for unauthenticated access.
  - `https://20000000.booking.yumstone.sg/book` returned `200`.
  - H5 phone input displayed fixed prefix `+65`, accepted local value `91234567`, and exposed `maxlength="8"` / `pattern="[0-9]{8}"`.
  - Intercepted the H5 reservation submit request before server creation; payload contained `phoneE164: "+6591234567"`.
- Smoke test customer auth artifacts created during verification were soft-deleted after validation.
