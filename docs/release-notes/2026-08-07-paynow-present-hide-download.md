# Release Notes

## Version / Date

2026-08-07 PayNow present screen download button removal

## New

- Added a `showDownload` switch to the shared QR component, defaulting to enabled for existing screens.

## Changed

- The PayNow customer present screen no longer shows the download payment QR button.

## Fixed

- Removed the operator-facing download action from the customer display QR card.

## Migration

- No database migration.

## Permission

- No App Gate or permission changes.

## Risk

- Frontend-only change. Settings and payment display pages still keep their QR download behavior.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle.

## Production Deployment

- Deployment date: 2026-08-07.
- Deployed commit: `0cba4012`.
- Branch: `codex/paynow-payment-product-line-staging`.
- Deployment type: frontend static assets only; backend JAR and Flyway were not changed.
- Frontend artifact built from clean worktree `target/deploy-worktree-0cba4012`.
- Uploaded artifact: `/home/ubuntu/rpb-0cba4012-frontend.tgz`.
- Frontend backup: `/opt/rpb/backups/20260807-1542-0cba4012-paynow-present-hide-download-frontend/frontend`.
- Smoke checks:
  - `https://booking.yumstone.sg/login` returned `200` and loaded `/assets/index-DAIGQz7w.js`.
  - `https://booking.yumstone.sg/assets/index-DAIGQz7w.js` returned `200`.
  - `https://booking.yumstone.sg/stores/20000000-0000-0000-0000-000000000983/payments/present/T1` returned `200`.
  - `rpb-backend` remained `active`; recent backend `ERROR` count was `0`.
