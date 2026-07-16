# Reservation Meal Period Schedule Design

## Goal

Make reservation creation use store-effective meal periods instead of arbitrary hour/minute picking. Platform admins maintain default seed meal periods, stores use the platform seed by default, and tenant admins can switch a store to its own meal periods.

## Scope

- Platform scope: default reservation meal-period seed rows such as lunch and dinner.
- Store scope: one store can inherit the platform seed or maintain its own meal periods.
- Reservation scope: creation uses generated time slots and rejects starts that are not an effective slot.
- UI scope: store staff selects from grouped slots; past slots are visible but disabled.

Out of scope: queue, seating, cleaning, turnover, POS, payment, marketing, and table assignment changes.

## Data Model

- `platform_reservation_meal_period_seeds`: platform-level seed periods with `period_key`, display name, start/end local time, cross-day flag, interval minutes, status, sort order, and version.
- `store_reservation_meal_period_settings`: one row per store deciding whether the store uses the platform seed.
- `store_reservation_meal_periods`: store-owned period rows copied from seed or maintained by tenant admin.

Platform seed data has no `tenant_id` or `store_id`. Store settings and store periods are scoped by `tenant_id + store_id`.

## API Design

- Platform admin:
  - `GET /api/v1/platform/reservation/meal-period-seed`
  - `PATCH /api/v1/platform/reservation/meal-period-seed`
- Tenant admin:
  - `GET /api/v1/stores/{storeId}/tenant-admin/reservation-meal-periods`
  - `PATCH /api/v1/stores/{storeId}/tenant-admin/reservation-meal-periods`
- Store staff reservation creation:
  - `GET /api/v1/stores/{storeId}/reservations/time-slots?businessDate=YYYY-MM-DD`
  - `POST /api/v1/stores/{storeId}/reservations` accepts optional `businessDate` so cross-day dinner slots remain attached to the selected service date.

## Slot Rules

- A slot is generated from local store time using the store timezone.
- End time is inclusive: lunch `11:00-15:00` with 30-minute interval yields `11:00, 11:30, ... 15:00`.
- Cross-day periods add one day to the local end time: dinner `17:00-00:30` yields `17:00, 17:30, ... 次日 00:30`.
- Slots whose start instant is not after `now` are returned as `selectable=false` and cannot be submitted.
- Reservation create rejects a start time that does not match one effective enabled slot for the submitted business date.

## Module Boundaries

- Platform seed management lives under the reservation module but remains platform-scoped.
- Tenant admin uses store-scoped settings and periods.
- Reservation lifecycle remains unchanged: meal periods are configuration and slot validation, not a new Reservation state.
- Existing capacity, duplicate, idempotency, audit, and App Gate behavior remains unchanged.

## Testing

- Migration test: tables, constraints, seed lunch/dinner, permissions.
- Unit tests: slot generation, cross-day slots, past-slot filtering, invalid arbitrary time rejection.
- Controller/service tests: platform seed, tenant store periods, time-slot query, and reservation create mapping.
- Frontend validation/build: staff dialog uses slot groups and no arbitrary minute wheel for reservation create.
