# PayNow Recent Display Retention Release Notes

## Version / Date

- 2026-08-07
- Scope: Quick Payment display settings and customer-facing Recent Display Numbers.

## New

- Tenant staff can configure how many seconds a Recent Display Number remains visible after the 120-second PayNow QR validity countdown expires.
- Default expired hold time is 20 seconds, so recent display numbers remain visible for 140 seconds by default.

## Changed

- Recent Display Numbers are pruned automatically after `120 + configured expired hold seconds`.
- Quick Payment and the customer-facing display refresh recent numbers every second, so expired recent entries disappear without a page reload.
- The PayNow QR validity period remains 120 seconds.
- Full-display capacity blocking still counts only QR codes that are within the 120-second active validity window.

## Fixed

- Prevents old expired display numbers from staying indefinitely in the Recent Display Numbers list.

## Migration

- No database migration.
- Existing local display settings are backward compatible and default to a 20-second expired hold time.
- No backend API contract change.

## Permission

- No App Gate permission change.
- No tenant, store, or staff permission registration change.

## Risk

- Frontend-local display queue behavior only.
- Store and terminal scoping remains based on existing payment presentation bridge storage keys.
- Reservation, Queue, Walk-in, Seating, and Cleaning workflows are unaffected.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle.
- No database rollback, backend restart, or permission rollback is required.

## Production Deployment

- Deployed commit: `dd3047a5`
- Deployment type: frontend static bundle only.
- Backup directory: `/opt/rpb/backups/20260807-2001-dd3047a5-paynow-recent-display-retention-frontend`
- Deployed assets:
  - `index-CkWma_wy.js`
  - `PaymentQuickPayPage-BvLuMRCn.js`
  - `PaymentPresentPage-orUhXMtp.js`
  - `paymentPresentBridge-aXUsMoKW.js`
- Smoke checks:
  - `/login`: HTTP 200 and references `assets/index-CkWma_wy.js`
  - `/assets/index-CkWma_wy.js`: HTTP 200
  - `/assets/PaymentQuickPayPage-BvLuMRCn.js`: HTTP 200
  - `/assets/PaymentPresentPage-orUhXMtp.js`: HTTP 200
  - `/assets/paymentPresentBridge-aXUsMoKW.js`: HTTP 200
  - `/stores/20000000-0000-0000-0000-000000000983/payments`: HTTP 200
  - `/stores/20000000-0000-0000-0000-000000000983/payments/present/T1`: HTTP 200
  - Backend service status after frontend deploy: `active`
