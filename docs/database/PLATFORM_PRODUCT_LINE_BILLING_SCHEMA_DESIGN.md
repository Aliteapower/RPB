# Platform Product Line Billing Schema Design

Status: Phase 1 implemented, Phase 1.1 pricing implemented, Phase 1.2 store billing line items implemented, Phase 1.3 store item renewal implemented

## Migration

Phase 1 schema is owned by:

- `src/main/resources/db/migration/V008__platform_product_line_billing.sql`

Phase 1.1 pricing schema is owned by:

- `src/main/resources/db/migration/V010__platform_product_line_prices.sql`

Phase 1.2 store billing line item schema is owned by:

- `src/main/resources/db/migration/V036__tenant_subscription_store_billing_items.sql`

Phase 1.3 store item period schema is owned by:

- `src/main/resources/db/migration/V041__store_level_subscription_item_periods.sql`
- `src/main/resources/db/migration/V042__allow_store_item_subscription_events.sql`

## Tables

`tenant_product_subscriptions`

- One commercial subscription row per `(tenant_id, app_key)`.
- Supports `billing_cycle`: `monthly`, `yearly`, `legacy_grant`, `manual`.
- Supports `status`: `active`, `suspended`, `cancelled`, `expired`.
- Stores current period, amount, currency, note, operator, timestamps, and optimistic `version`.

`tenant_product_subscription_events`

- Append-only event history for manual billing actions.
- Supports `purchase`, `renew`, `renew_item`, `suspend`, `cancel`, `convert_from_legacy`, `manual_adjust`.
- Enforces idempotency by `(tenant_id, app_key, event_type, idempotency_key)`.
- Stores quote snapshots in `event_payload` for duration-based purchase, renew, and legacy conversion.

`platform_product_line_prices`

- One monthly price and one yearly price per `platform_apps.app_key`.
- Supports `billing_cycle`: `monthly`, `yearly`.
- Stores default amount, currency, status, timestamps, and optimistic `version`.
- Enforces unique `(app_key, billing_cycle)`.
- Seeds `reservation_queue` monthly and yearly rows with `0.00 SGD` for platform-admin configuration.

`tenant_product_subscription_items`

- One current commercial detail row per `(subscription_id, store_id)` for store-scoped billing.
- Keeps `tenant_id`, `app_key`, and `(store_id, tenant_id)` FK for tenant-safe joins and cross-tenant protection.
- Supports `scope_type = store` in this slice; `tenant` is reserved for future tenant-level fixed fees.
- Stores billing cycle, current period, quantity, per-store unit amount, line amount, currency, status, payment note, timestamps, and optimistic `version`.
- Backfills existing tenant subscriptions against active, undeleted stores.
- Does not replace `tenant_app_entitlements`; App Gate remains tenant scoped.

## Product Line Source

`platform_apps.app_key` remains the product line identity.

V008 updates:

- `reservation_queue` display name to `预约排队叫号产线`
- description to `预约、排队、叫号一体化产线`

## Legacy Grant Backfill

V008 inserts legacy subscriptions for existing App Gate entitlements where:

- `tenant_app_entitlements.app_key = reservation_queue`
- `status = enabled`
- `valid_until is null`

The entitlement itself is not changed. `valid_until = null` remains permanent until a platform admin manually converts the subscription.

## Permissions

V008 grants existing platform admin accounts:

- `platform.product_line.manage`
- `platform.billing.manage`

Phase 1 APIs also accept `platform.tenant.manage` for compatibility.

## App Gate Sync Boundary

Billing writes commercial state to its own tables, records events, then syncs App Gate entitlement state:

- purchase / renew / convert: `enabled` with matching `valid_from` and `valid_until`
- suspend: `suspended`
- cancel: `disabled`
- legacy grant: keeps `valid_until = null`

No reservation, queue, queue display, cleaning, or walk-in business table is changed by billing.

## Phase 1.1 Duration And Quote Boundary

Normal purchase, renew, and legacy conversion commands use `durationCount` instead of user-selected dates.

The backend calculates:

- `current_period_start`
- `current_period_end`
- default amount from product-line price times duration
- App Gate `valid_from` and `valid_until`

The frontend may preview values, but the backend result is authoritative.

## Phase 1.2 Store Billing Item Boundary

For duration-based monthly and yearly commands, product-line price is interpreted as a per-store unit price.

The billing service calculates:

```text
storeUnitAmount = unitAmount * durationCount
defaultAmount = storeUnitAmount * active billable store count
```

Purchase, bulk renewal, and legacy conversion replace the subscription's store item rows using active stores in the tenant. Suspend and cancel update the item status with the subscription status. Single-store item renewal updates only the selected store item and recomputes the aggregate tenant subscription from active item rows.

No reservation, queue, queue display, cleaning, walk-in, or App Gate table reads these item rows.
