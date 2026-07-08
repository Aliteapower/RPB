# Platform Tenant Product Subscription API Contract

Status: Phase 1 implemented, Phase 1.1 duration billing implemented, Phase 1.2 store billing line items implemented, Phase 1.3 store item renewal implemented

## Scope

This API supports lightweight manual billing. It does not integrate payment gateways, auto-renewal, invoices, webhooks, or tenant self-service payment.

Billing state is synchronized into App Gate entitlements. Business APIs continue to be protected by App Gate.

Store billing line items are commercial detail only. They do not grant or revoke product access per store in this phase.

Store item renewal is supported for platform-admin billing operations. App Gate remains tenant-scoped; the aggregate tenant entitlement is enabled while the aggregate subscription remains active.

## Permissions

All endpoints require a platform admin actor and one of:

- `platform.billing.manage`
- `platform.tenant.manage` as Phase 1 compatibility

## Endpoints

### `GET /api/v1/platform/tenants/{tenantId}/product-subscriptions`

Returns tenant subscriptions, including legacy grants, with store billing line items when they exist.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase`

Creates one subscription per `(tenantId, appKey)`, records a `purchase` event, and syncs App Gate entitlement to `enabled`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew`

Compatibility bulk renewal endpoint. Extends the aggregate commercial period, replaces active store item rows, records a `renew` event, and syncs App Gate entitlement to `enabled` with the new `valid_until`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew`

Renews one store billing item only.

Behavior:

- validates that `tenantId` owns the subscription
- validates that `itemId` belongs to the subscription and tenant
- calculates quote with `storeCount = 1`
- updates only the selected item
- recomputes aggregate subscription amount and period from active store items
- records a `renew_item` event
- syncs tenant-level App Gate entitlement from the aggregate subscription

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/suspend`

Records a `suspend` event and syncs App Gate entitlement to `suspended`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/cancel`

Records a `cancel` event and syncs App Gate entitlement to `disabled`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy`

Converts a `legacy_grant` subscription into a manual paid cycle and records a `convert_from_legacy` event.

## Mutation Request

Normal platform-admin purchase, renew, and convert-from-legacy commands use `durationCount`.

The backend calculates `currentPeriodStart` and `currentPeriodEnd`.

```json
{
  "idempotencyKey": "manual-20260626-001",
  "appKey": "reservation_queue",
  "billingCycle": "monthly",
  "durationCount": 3,
  "amount": 384.00,
  "currency": "SGD",
  "paymentNote": "manual transfer",
  "version": 0
}
```

If `amount` is omitted, the backend defaults it from the product-line price:

```text
unit price * durationCount
```

If `amount` is present, it is stored as the final manual amount. The subscription event payload records unit price, default amount, final amount, duration, currency, and price source.

`version` is required for optimistic protection on renew, suspend, cancel, convert, and item renew. For item renew, `version` is the selected item version.

## Status Request

```json
{
  "idempotencyKey": "manual-20260626-suspend-001",
  "paymentNote": "manual suspend",
  "version": 1
}
```

## Response

```json
{
  "success": true,
  "replayed": false,
  "subscription": {
    "id": "40000000-0000-0000-0000-000000009301",
    "tenantId": "10000000-0000-0000-0000-000000009301",
    "appKey": "reservation_queue",
    "productLineName": "预约排队叫号产线",
    "billingCycle": "monthly",
    "status": "active",
    "effectiveStatus": "active",
    "currentPeriodStart": "2026-07-01T00:00:00Z",
    "currentPeriodEnd": "2026-07-31T23:59:59Z",
    "amount": 384.00,
    "currency": "SGD",
    "paymentNote": "manual transfer",
    "entitlementStatus": "enabled",
    "entitlementValidUntil": "2026-07-31T23:59:59Z",
    "version": 0,
    "items": [
      {
        "id": "50000000-0000-0000-0000-000000009301",
        "scopeType": "store",
        "storeId": "20000000-0000-0000-0000-000000009301",
        "storeCode": "lsc106",
        "storeName": "LSC106",
        "operatingEntityId": "60000000-0000-0000-0000-000000009301",
        "operatingEntityName": "LSC Operating Entity",
        "billingCycle": "monthly",
        "currentPeriodStart": "2026-07-01T00:00:00Z",
        "currentPeriodEnd": "2026-07-31T23:59:59Z",
        "quantity": 1,
        "unitAmount": 128.00,
        "amount": 384.00,
        "currency": "SGD",
        "status": "active",
        "paymentNote": "manual transfer",
        "version": 0
      }
    ]
  },
  "quote": {
    "durationCount": 3,
    "durationUnit": "month",
    "storeCount": 2,
    "unitAmount": 128.00,
    "storeUnitAmount": 384.00,
    "defaultAmount": 768.00,
    "finalAmount": 768.00,
    "currency": "SGD"
  }
}
```

For monthly and yearly duration commands, product-line price is treated as the per-store unit price. The backend calculates:

```text
storeUnitAmount = productLinePrice * durationCount
defaultAmount = storeUnitAmount * active billable store count
```

For item renewal, the backend calculates:

```text
storeUnitAmount = productLinePrice * durationCount
defaultAmount = storeUnitAmount * 1
```

If no active stores exist, purchase, bulk renewal, and legacy conversion requests that use duration billing are rejected with `REQUEST_INVALID`.

## Error Codes

- `UNAUTHENTICATED`
- `FORBIDDEN`
- `REQUEST_INVALID`
- `TENANT_NOT_FOUND`
- `PRODUCT_LINE_NOT_FOUND`
- `SUBSCRIPTION_NOT_FOUND`
- `SUBSCRIPTION_ITEM_NOT_FOUND`
- `SUBSCRIPTION_CONFLICT`
- `VERSION_CONFLICT`
- `PERSISTENCE_ERROR`
