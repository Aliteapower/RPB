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

## Production Deployment

- Deployed commit: `e6d47893`
- Deployment type: frontend static bundle only.
- Backup directory: `/opt/rpb/backups/20260807-1630-e6d47893-paynow-present-capacity-lock-frontend`
- Deployed assets:
  - `index-D7SqFVez.js`
  - `PaymentQuickPayPage-DqFzO7z4.js`
  - `paymentPresentBridge-Rf8-e90Y.js`
- Smoke checks:
  - `/login`: HTTP 200 and references `assets/index-D7SqFVez.js`
  - `/assets/index-D7SqFVez.js`: HTTP 200
  - `/assets/PaymentQuickPayPage-DqFzO7z4.js`: HTTP 200
  - `/assets/paymentPresentBridge-Rf8-e90Y.js`: HTTP 200
  - `/stores/20000000-0000-0000-0000-000000000983/payments`: HTTP 200
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1`: HTTP 200
  - Backend service status after frontend deploy: `active`
