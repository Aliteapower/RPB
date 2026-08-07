# Release Notes

## Version / Date

2026-08-07 PayNow payment business day

## New

- Added a store-scoped PayNow payment business day state.
- Added Quick Pay business day status display with Refresh and Open Today actions.
- Added backend business day endpoints for reading the current payment business day and opening today's business day.

## Changed

- Quick Pay intent creation now writes `payment_sessions.business_date` from the opened payment business day instead of browser or request calendar time.
- If no payment business day is open, Quick Pay creation auto-opens today's business day before creating the PayNow QR so payment code generation can continue.

## Fixed

- Cross-date Quick Pay receipts now stay attached to the open payment business day until staff opens today.
- PayNow QR creation no longer depends on the frontend guessing the business date.

## Migration

- Adds `payment_business_days` with tenant and store scope, a unique day per store, and a partial unique index allowing only one open payment business day per store.

## Permission

- The payment business day read and open endpoints use App Gate `payment.intent.create`, matching staff Quick Pay creation.

## Risk

- Migration is additive. Existing payment sessions keep their current `business_date`; new Quick Pay sessions use the open payment business day.
- Reopening today closes any other open payment business day for the same tenant and store.

## Rollback Notes

- Roll back the application code first, then drop `payment_business_days` only if no released code still depends on the table.
