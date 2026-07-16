# Release Notes

## Version / Date

Reservation meal period schedule / 2026-06-29

## New

- Added platform reservation meal period seed management for lunch and dinner windows.
- Added store-effective reservation meal period settings under tenant admin settings.
- Added staff reservation time-slot picking from effective store meal periods.
- Added time-slot API support for reservation creation workflows.

## Changed

- Reservation creation now validates the selected start time against the effective store meal period slots.
- Store settings use platform seed meal periods by default, while tenant admins can copy seed periods or maintain store-specific periods.
- Staff reservation creation no longer depends on free-form split time picking for start time selection.
- Staff reservation creation now filters visible time slots by the effective meal period names returned by the backend.

## Fixed

- Reservation start choices are constrained to configured meal period intervals, for example 11:00, 11:30, 12:00 through 15:00.
- Cross-day dinner periods such as 17:00 through next-day 00:30 are generated as one selectable schedule.
- Past time slots are no longer shown in the staff reservation creation picker.

## Migration

- Added `V019__reservation_meal_period_schedule.sql`.
- New tables: `platform_reservation_meal_period_seeds`, `store_reservation_meal_period_settings`, and `store_reservation_meal_periods`.
- Seed data creates default platform lunch and dinner periods with 30-minute intervals.

## Permission

- New platform permission: `platform.reservation_meal_period.manage`.
- Platform meal period seed APIs require `platform_admin` and the new manage permission.
- Store meal period settings remain under tenant admin store-scoped access.

## Risk

- Existing API clients can still omit `businessDate`, but cross-day dinner reservations should send it to bind the slot to the intended business date.
- Tenant-specific custom periods are store-scoped, so multi-store tenants must configure each store independently when they do not use platform seed periods.
- Vite still reports the existing large chunk warning during frontend build.

## Rollback Notes

- Roll back frontend by removing the platform meal period seed page, tenant admin meal period settings UI, and time-slot picker wiring.
- Roll back APIs by removing meal period controllers, DTOs, services, repository, and reservation creation slot validation.
- Database rollback requires removing V019-created tables and the `platform.reservation_meal_period.manage` permission after confirming no store has copied or customized periods.
