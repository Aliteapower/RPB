# Release Notes

## Version / Date

2026-08-07 PayNow end-day controlled business date

## New

- Added `POST /api/v1/stores/{storeId}/payments/business-day/end-day` for store-scoped PayNow business day closing.
- Added an End Day action on the Quick Pay business day panel when a payment business day is open.

## Changed

- `POST /api/v1/stores/{storeId}/payments/business-day` now returns the current open business day when one exists instead of switching to today's calendar date.
- Quick Pay now shows Open Today only when no payment business day is open.
- Quick Pay creation continues to use the current open payment business day across midnight until End Day is performed.

## Fixed

- PayNow QR generation after midnight no longer risks writing the new calendar date while the previous payment business day is still open.
- Direct backend calls cannot skip the end-day step to switch payment business dates.

## Migration

- No new migration. The existing `payment_business_days.status` and `closed_at` columns support the end-day lifecycle.

## Permission

- The end-day endpoint uses App Gate `payment.intent.create`, matching the existing Quick Pay business day open endpoint.

## Risk

- Low schema risk because no database migration is added.
- Operational risk is limited to PayNow Quick Pay business-day state. Reservation, Queue, Walk-in, Seating, and Cleaning workflows are unchanged.

## Rollback Notes

- Roll back to the previous backend jar and frontend bundle.
- Existing `payment_business_days` rows can remain in place; the previous released code can still read and open business days.
