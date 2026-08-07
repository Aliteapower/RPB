# PayNow Present Capacity Lock Release Notes

## Version / Date

- 2026-08-07
- Scope: Quick Payment and customer-facing payment presentation screens.

## New

- Tenant staff can configure up to 6 simultaneous customer-facing PayNow QR slots for a terminal.
- Quick Payment now tracks the active customer-facing QR slots for the current store and terminal.

## Changed

- When all configured presentation slots are occupied by still-valid 120-second PayNow QR codes, Quick Payment blocks creating another QR code and shows `等顾客支付`.
- The capacity check refreshes every second, so Quick Payment becomes available again after a QR expires or is cleared from the customer-facing screen.

## Fixed

- Prevents Quick Payment from creating a backend payment session that would be immediately pushed out of the customer-facing display when all visible slots are already occupied.

## Migration

- No database migration.
- No backend API contract change.
- No payment session schema change.

## Permission

- No App Gate permission change.
- No tenant, store, or staff permission registration change.

## Risk

- Frontend-local display queue behavior only; existing 120-second PayNow display TTL is unchanged.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows are unaffected.
- Existing store and terminal scoping remains based on the payment presentation bridge storage keys.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle.
- No database rollback, backend restart, or permission rollback is required for this change.
