# Platform Product Line Billing Backend Implementation Report

Status: Phase 1 implemented

## Backend Module

New module:

- `com.rpb.reservation.platformbilling`

Layers:

- `application`: product line and subscription orchestration
- `persistence`: `JdbcTemplate` repositories and App Gate entitlement sync gateway
- `api`: platform REST controllers, request/response contracts, permission checks

## Implemented Capabilities

- product line catalog backed by `platform_apps`
- `reservation_queue` display name as `预约排队叫号产线`
- manual purchase
- manual renew
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

## Tests

Coverage added for:

- migration constraints and legacy backfill
- product line service
- subscription lifecycle and idempotency
- platform API permissions
- App Gate rejection for expired enabled entitlement
- module dependency boundary
