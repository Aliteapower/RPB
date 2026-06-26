# Platform Product Line Billing Schema Design

Status: Phase 1 implemented

## Migration

Phase 1 schema is owned by:

- `src/main/resources/db/migration/V008__platform_product_line_billing.sql`

## Tables

`tenant_product_subscriptions`

- One commercial subscription row per `(tenant_id, app_key)`.
- Supports `billing_cycle`: `monthly`, `yearly`, `legacy_grant`, `manual`.
- Supports `status`: `active`, `suspended`, `cancelled`, `expired`.
- Stores current period, amount, currency, note, operator, timestamps, and optimistic `version`.

`tenant_product_subscription_events`

- Append-only event history for manual billing actions.
- Supports `purchase`, `renew`, `suspend`, `cancel`, `convert_from_legacy`, `manual_adjust`.
- Enforces idempotency by `(tenant_id, app_key, event_type, idempotency_key)`.

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
