# Store-Level Platform Billing

## Version / Date

2026-07-08

## New

- Adds store-level renewal endpoint:
  - `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew`
- Adds item-level billing period fields to `tenant_product_subscription_items`:
  - `billing_cycle`
  - `current_period_start`
  - `current_period_end`
  - `payment_note`
- Tenant billing page now lets platform admins select one store billing item and renew that store only.

## Changed

- Tenant-level subscription remains the aggregate container.
- Store item renewal quotes `storeCount = 1`.
- After store item renewal, aggregate subscription amount and period are recomputed from active store items.
- Existing tenant-level renew endpoint remains available as a compatibility bulk renewal path.

## Migration

- New Flyway migration:
  - `V041__store_level_subscription_item_periods.sql`
- Existing item rows are backfilled from their parent subscription billing cycle and period.

## Permission

- Platform billing permission remains:
  - `platform.billing.manage`
- Local runtime security allowlist adds only the narrow item-renew path.

## Risk

- App Gate remains tenant-scoped. This release changes platform billing state by store, but does not enforce product access per individual store.

## Rollback Notes

- Frontend rollback restores the previous tenant-level renewal UI.
- Backend rollback should leave the added nullable item columns in place unless a full database rollback is being performed.
- If needed, platform admins can continue using the tenant-level renew endpoint as a bulk operation.

## Deployment

- Deployed commit `febeb969` to `booking.yumstone.sg` on 2026-07-08.
- Production server: `43.134.69.75`.
- Backend backup: `/opt/rpb/backups/20260708-1017-febeb969/reservation-platform.jar`.
- Frontend backup: `/opt/rpb/backups/20260708-1017-febeb969-frontend/frontend`.
- Backend service: `rpb-backend` active.
- Flyway: `041|store level subscription item periods|t`.
- Public smoke checks:
  - `https://booking.yumstone.sg/api/v1/auth/me` returned `401`.
  - `https://booking.yumstone.sg/login` returned `200`.
  - `https://booking.yumstone.sg/platform/tenants/fb8e092d-aa34-42eb-bb55-5229044c3885/billing` returned `200`.
  - `https://booking.yumstone.sg/assets/PlatformTenantBillingPage-DfW6Xr-C.js` returned `200`.
  - Shared API chunk contains the new `/items/{itemId}/renew` path.
