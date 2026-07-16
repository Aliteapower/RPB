# Platform Product Line Billing Backend Implementation Report

Status: Phase 1 implemented, Phase 1.1 pricing implemented

## Backend Module

New module:

- `com.rpb.reservation.platformbilling`

Layers:

- `application`: product line and subscription orchestration
- `persistence`: `JdbcTemplate` repositories and App Gate entitlement sync gateway
- `api`: platform REST controllers, request/response contracts, permission checks

## Implemented Capabilities

- product line catalog backed by `platform_apps`
- product line monthly/yearly pricing backed by `platform_product_line_prices`
- `reservation_queue` display name as `预约排队叫号产线`
- manual purchase
- manual renew
- duration-count based purchase, renew, and legacy conversion period calculation
- quote snapshot recording in subscription event payload
- suspend
- cancel
- convert from legacy grant
- event recording
- idempotency guard
- optimistic version guard
- App Gate entitlement sync

## Architecture Boundary

`platformbilling` writes App Gate entitlement state through a narrow sync gateway.

`appgate` does not depend on `platformbilling`.

Reservation, queue, queue display, cleaning, and walk-in modules do not depend on billing.

## Legacy Grant Handling

V008 backfills existing enabled permanent App Gate entitlements into `tenant_product_subscriptions` as:

- `billing_cycle = legacy_grant`
- `status = active`
- `current_period_end = null`
- `amount = 0`

Existing App Gate `valid_until = null` values are preserved.

## Product Line Pricing

V010 adds `platform_product_line_prices` with one monthly and one yearly row per product line.

Tenant billing commands use the current product-line price as the default quote. The final amount is copied into the tenant subscription and the quote snapshot is stored in the subscription event payload.

## Tests

Coverage added for:

- migration constraints and legacy backfill
- product line service
- product line price migration and service behavior
- subscription lifecycle and idempotency
- billing duration and quote calculation
- platform API permissions
- App Gate rejection for expired enabled entitlement
- module dependency boundary
