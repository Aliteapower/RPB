# Platform Tenant Product Subscription API Contract

Status: Phase 1 implemented

## Scope

This API supports lightweight manual billing. It does not integrate payment gateways, auto-renewal, invoices, webhooks, or tenant self-service payment.

Billing state is synchronized into App Gate entitlements. Business APIs continue to be protected by App Gate.

## Permissions

All endpoints require a platform admin actor and one of:

- `platform.billing.manage`
- `platform.tenant.manage` as Phase 1 compatibility

## Endpoints

### `GET /api/v1/platform/tenants/{tenantId}/product-subscriptions`

Returns tenant subscriptions, including legacy grants.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase`

Creates one subscription per `(tenantId, appKey)`, records a `purchase` event, and syncs App Gate entitlement to `enabled`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew`

Extends the commercial period, records a `renew` event, and syncs App Gate entitlement to `enabled` with the new `valid_until`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/suspend`

Records a `suspend` event and syncs App Gate entitlement to `suspended`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/cancel`

Records a `cancel` event and syncs App Gate entitlement to `disabled`.

### `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy`

Converts a `legacy_grant` subscription into a manual paid cycle and records a `convert_from_legacy` event.

## Mutation Request

```json
{
  "idempotencyKey": "manual-20260626-001",
  "appKey": "reservation_queue",
  "billingCycle": "monthly",
  "currentPeriodStart": "2026-07-01T00:00:00Z",
  "currentPeriodEnd": "2026-07-31T23:59:59Z",
  "amount": 128.00,
  "currency": "SGD",
  "paymentNote": "manual transfer",
  "version": 0
}
```

`version` is required for optimistic protection on renew, suspend, cancel, and convert.

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
    "amount": 128.00,
    "currency": "SGD",
    "paymentNote": "manual transfer",
    "entitlementStatus": "enabled",
    "entitlementValidUntil": "2026-07-31T23:59:59Z",
    "version": 0
  }
}
```

## Error Codes

- `UNAUTHENTICATED`
- `FORBIDDEN`
- `REQUEST_INVALID`
- `TENANT_NOT_FOUND`
- `PRODUCT_LINE_NOT_FOUND`
- `SUBSCRIPTION_NOT_FOUND`
- `SUBSCRIPTION_CONFLICT`
- `VERSION_CONFLICT`
- `PERSISTENCE_ERROR`
