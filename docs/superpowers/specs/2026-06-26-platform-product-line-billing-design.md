# Platform Product Line Billing Design

## Status

Design round only. This document does not create executable schema, Java code, Vue code, runtime configuration, dependency changes, production data changes, or seed data changes.

This design records the approved V1 approach:

```text
Lightweight manual billing + App Gate entitlement activation.
```

V1 does not connect a payment gateway, automatic renewal, invoice generation, webhook processing, external accounting, coupons, prepaid cards, POS payment, or tenant self-service checkout.

## Source Context

- Current platform backoffice tenant API: `GET/POST/PATCH/DELETE /api/v1/platform/tenants`.
- Current platform navigation has a disabled `基础设置` item.
- Current App Gate tables:
  - `platform_apps`
  - `tenant_app_entitlements`
  - `store_app_settings`
  - `app_gate_audit_logs`
- Current first App Gate app:

```text
app_key = reservation_queue
current app_name = 订位排号系统
approved platform display name = 预约排队叫号产线
```

- Current App Gate runtime checks:
  1. Platform app exists and is active.
  2. Tenant entitlement exists.
  3. Tenant entitlement is enabled or valid trial.
  4. Tenant entitlement has not expired by `valid_until`.
  5. Store app setting exists and is enabled.
  6. Actor can access the store.
  7. Actor has the endpoint permission.

## Goals

1. Add a platform product-line catalog backed by App Gate apps.
2. Treat each V1 product line as exactly one App Gate app.
3. Add a lightweight tenant subscription and billing model for manual platform-admin operations.
4. Keep existing `reservation_queue` tenants with `valid_until = null` working as historical permanent grants.
5. Let platform admins manually purchase, renew, suspend, cancel, and convert legacy grants.
6. Synchronize subscription state into `tenant_app_entitlements` so existing App Gate enforcement remains the runtime source of truth.
7. Preserve store-level app settings as operational visibility and availability toggles.
8. Keep billing rules out of Reservation, Queue, Walk-in, Seating, Cleaning, Queue Display, and tenant-admin business modules.

## Non-Goals

- No fine-grained product lines for `reservation_checkin`, `queue_call`, `call_screen`, `cleaning`, or individual page actions.
- No new runtime authorization mechanism outside App Gate.
- No direct billing checks inside reservation, queue, walk-in, seating, cleaning, or call-screen business services.
- No payment gateway integration.
- No automatic monthly or yearly charge collection.
- No invoice, tax, receipt, or accounting module.
- No tenant self-service checkout.
- No webhook retry or external payment notification handling.
- No migration that makes existing enabled tenants expire.

## Product Line Boundary

V1 product line identity is App Gate app identity:

```text
platform_apps.app_key = productLine.appKey
```

The first product line is:

| Field | Value |
|---|---|
| `app_key` | `reservation_queue` |
| Platform display name | `预约排队叫号产线` |
| Runtime app key | `reservation_queue` |
| Runtime endpoint permissions | Existing reservation, queue, walk-in, table, cleaning, and queue display permissions |

Do not split the first product line into smaller product lines. Endpoint-level capabilities remain permissions under the single `reservation_queue` product line.

## Module Boundaries And OOD

Create a focused platform billing module. The module name should describe platform commercial access, not restaurant customer payment.

Recommended backend package:

```text
com.rpb.reservation.platformbilling
  api
  application
  domain
  persistence
```

Object boundaries:

| Object | Owner Module | Responsibility | Must Not Do |
|---|---|---|---|
| `PlatformProductLine` | `platformbilling` | Platform-owned product catalog projection over `platform_apps` | Decide runtime user permissions |
| `TenantProductSubscription` | `platformbilling` | Tenant-owned commercial state for one product line | Store-level visibility or business workflow authorization |
| `TenantProductSubscriptionEvent` | `platformbilling` | Append-only billing operation history and idempotency anchor | Replace general audit logging |
| `BillingCycle` | `platformbilling.domain` | Valid cycle values and cycle-specific rules | Calculate restaurant reservation time |
| `SubscriptionStatus` | `platformbilling.domain` | Commercial lifecycle state | Replace App Gate deny reasons |
| `BillingPeriod` | `platformbilling.domain` | Valid start/end period semantics | Interpret store business date |
| `EntitlementSyncPolicy` | `platformbilling.domain` | Map subscription operation to App Gate entitlement status and validity | Query or mutate business workflow tables |
| `SubscriptionIdempotencyRule` | `platformbilling.domain` | Validate and classify repeated platform billing commands | Own shared idempotency infrastructure globally |

Service boundaries:

| Service | Responsibility |
|---|---|
| `PlatformProductLineService` | List and update platform product-line catalog rows. |
| `ProductSubscriptionService` | Orchestrate purchase, renew, suspend, cancel, convert-from-legacy, and manual adjust. |
| `TenantProductEntitlementSyncRepository` | The only persistence adapter in this module allowed to write `tenant_app_entitlements` for billing operations. |
| `ProductSubscriptionAuditService` | Append platform audit rows for commercial operations. |

Dependency rules:

1. `platformbilling` may read and write `tenant_product_subscriptions` and `tenant_product_subscription_events`.
2. `platformbilling` may update `platform_apps` only through product-line catalog commands.
3. `platformbilling` may synchronize `tenant_app_entitlements` and upsert missing `store_app_settings` only through a narrow entitlement-sync adapter.
4. `appgate` must not depend on `platformbilling`. App Gate continues to evaluate `platform_apps`, `tenant_app_entitlements`, `store_app_settings`, store access, and actor permissions.
5. Reservation, Queue, Walk-in, Seating, Cleaning, Queue Display, Tenant Admin, and Store modules must not call `platformbilling`.
6. `platform` tenant management must not absorb subscription logic into `PlatformTenantService`; tenant lifecycle and tenant commercial access are separate modules.
7. Controllers must call application services, not repositories.
8. Domain rules must normalize billing cycles, periods, status transitions, legacy conversion, and entitlement synchronization decisions before persistence writes happen.

This keeps the design object-oriented around stable business concepts instead of scattering commercial access checks across controllers, pages, and workflow services.

## Existing Code Findings

- `PlatformAdminNav.vue` already has a disabled `基础设置` entry. This is the correct navigation area for product-line and billing pages.
- `TenantAdminSettingsPage.vue` is store-level settings and must not own platform product lines or billing.
- `PlatformTenantController` uses `platform_admin` plus `platform.tenant.manage`; the billing module should follow the same platform-scope pattern while introducing dedicated permissions.
- `PlatformCallScreenSeedController` shows the preferred compatibility pattern: a dedicated platform permission plus temporary acceptance of `platform.tenant.manage`.
- `AppGateService` already rejects expired entitlements by comparing `tenant_app_entitlements.valid_until` to current time.
- `AppGateCommandService` can enable, disable, suspend, and update tenant app config, but cannot currently set `valid_from` or `valid_until`.
- No billing, subscription, price, invoice, or payment module exists in the current codebase.

## Data Ownership

| Data | Scope | `tenant_id` | `store_id` | Notes |
|---|---:|---:|---:|---|
| Product line catalog | Platform | No | No | Backed by `platform_apps` |
| Product line price template | Platform | No | No | Optional Phase 2 table |
| Tenant subscription current state | Tenant | Yes | No | One current row per tenant and app |
| Tenant subscription event | Tenant | Yes | No | Immutable operation history |
| Tenant App Gate entitlement | Tenant | Yes | No | Runtime tenant-level gate |
| Store app setting | Store | Yes | Yes | Runtime store-level toggle and entry visibility |

## Data Design

### Reuse Existing `platform_apps`

Do not create a separate `platform_product_lines` table in V1. Use `platform_apps` as the product-line catalog.

Recommended API projection:

| API field | Source |
|---|---|
| `appKey` | `platform_apps.app_key` |
| `displayName` | `platform_apps.app_name` |
| `status` | `platform_apps.status` |
| `defaultEntryRoute` | `platform_apps.default_entry_route` |
| `description` | `platform_apps.description` |
| `sortOrder` | `platform_apps.sort_order` |
| `config` | `platform_apps.config_json` |
| `createdAt` | `platform_apps.created_at` |
| `updatedAt` | `platform_apps.updated_at` |

The implementation migration should update the seed display name for `reservation_queue` to `预约排队叫号产线` without changing `app_key`.

### New Table: `tenant_product_subscriptions`

Purpose: tenant-level current commercial state for one product line.

Design-level DDL:

```sql
create table tenant_product_subscriptions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    billing_cycle text not null,
    status text not null,
    current_period_start timestamptz null,
    current_period_end timestamptz null,
    amount numeric(12, 2) not null default 0,
    currency text not null,
    payment_note text null,
    operator_user_id uuid null references auth_accounts(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint uq_tenant_product_subscriptions_scope unique (tenant_id, app_key),
    constraint ck_tenant_product_subscriptions_cycle check (
        billing_cycle in ('monthly', 'yearly', 'legacy_grant', 'manual')
    ),
    constraint ck_tenant_product_subscriptions_status check (
        status in ('active', 'suspended', 'cancelled', 'expired')
    ),
    constraint ck_tenant_product_subscriptions_amount check (amount >= 0),
    constraint ck_tenant_product_subscriptions_period check (
        current_period_end is null
        or current_period_start is null
        or current_period_end > current_period_start
    ),
    constraint ck_tenant_product_subscriptions_legacy check (
        billing_cycle <> 'legacy_grant'
        or (status = 'active' and current_period_end is null and amount = 0)
    )
);
```

Expected indexes:

```sql
create index ix_tenant_product_subscriptions_tenant
    on tenant_product_subscriptions (tenant_id, status, app_key);

create index ix_tenant_product_subscriptions_period_end
    on tenant_product_subscriptions (status, current_period_end);
```

### New Table: `tenant_product_subscription_events`

Purpose: append-only operation history and idempotency anchor for manual billing commands.

Design-level DDL:

```sql
create table tenant_product_subscription_events (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid not null references tenant_product_subscriptions(id),
    tenant_id uuid not null references tenants(id),
    app_key text not null references platform_apps(app_key),
    event_type text not null,
    idempotency_key text not null,
    previous_status text null,
    new_status text not null,
    previous_billing_cycle text null,
    new_billing_cycle text not null,
    previous_period_start timestamptz null,
    previous_period_end timestamptz null,
    new_period_start timestamptz null,
    new_period_end timestamptz null,
    amount numeric(12, 2) not null default 0,
    currency text not null,
    payment_note text null,
    operator_user_id uuid null references auth_accounts(id),
    created_at timestamptz not null default now(),
    constraint ck_tenant_product_subscription_events_type check (
        event_type in (
            'purchase',
            'renew',
            'suspend',
            'cancel',
            'convert_from_legacy',
            'manual_adjust'
        )
    ),
    constraint ck_tenant_product_subscription_events_status check (
        new_status in ('active', 'suspended', 'cancelled', 'expired')
    ),
    constraint ck_tenant_product_subscription_events_cycle check (
        new_billing_cycle in ('monthly', 'yearly', 'legacy_grant', 'manual')
    ),
    constraint ck_tenant_product_subscription_events_amount check (amount >= 0)
);

create unique index ux_tenant_product_subscription_events_idempotency
    on tenant_product_subscription_events (tenant_id, app_key, event_type, idempotency_key);
```

The idempotency rule is command-specific: repeating the same endpoint with the same `idempotencyKey` must return the already-recorded result and must not extend the subscription twice.

### Optional Phase 2 Table: `platform_product_line_prices`

V1 can operate without price templates because platform admins manually enter `amount`, `currency`, and `payment_note` on purchase and renewal.

If pricing templates become necessary, add this table in Phase 2:

```sql
create table platform_product_line_prices (
    id uuid primary key default gen_random_uuid(),
    app_key text not null references platform_apps(app_key),
    billing_cycle text not null,
    amount numeric(12, 2) not null,
    currency text not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_platform_product_line_prices_scope unique (app_key, billing_cycle, currency),
    constraint ck_platform_product_line_prices_cycle check (billing_cycle in ('monthly', 'yearly')),
    constraint ck_platform_product_line_prices_status check (status in ('active', 'disabled')),
    constraint ck_platform_product_line_prices_amount check (amount >= 0)
);
```

## Legacy Grant Handling

Approved V1 rule:

```text
Existing enabled tenant_app_entitlements with valid_until = null are historical permanent grants.
```

Implementation requirements:

1. Do not update existing `tenant_app_entitlements.valid_until` during the billing migration.
2. Do not make existing tenants expire because the subscription module is introduced.
3. Backfill a current subscription row for each legacy grant:

```text
billing_cycle = legacy_grant
status = active
current_period_start = tenant_app_entitlements.valid_from or enabled_at or created_at
current_period_end = null
amount = 0
currency = tenant/store default currency fallback, recommended SGD if no tenant currency is available
payment_note = Historical permanent grant migrated from App Gate entitlement.
```

4. The backfill must not alter `tenant_app_entitlements`.
5. The subscription page may show `legacy_grant` as `历史赠送 / 永久有效`.
6. `convert-from-legacy` is the only V1 command that changes a legacy grant into monthly, yearly, or manual billing.
7. Conversion from legacy must set a finite `current_period_end` unless billing cycle is `manual` and the request explicitly chooses open-ended manual access.

Recommended conversion behavior:

| Before | Command | After subscription | After entitlement |
|---|---|---|---|
| `legacy_grant`, `active`, period end null | `convert_from_legacy` to monthly/yearly | `active`, finite period | `enabled`, finite `valid_until` |
| `legacy_grant`, `active`, period end null | `convert_from_legacy` to manual open-ended | `active`, period end null | `enabled`, `valid_until = null` |

## Subscription State Rules

### Purchase

Use when a tenant has no current subscription for the product line.

Allowed request billing cycles:

```text
monthly
yearly
manual
```

Effects:

1. Create `tenant_product_subscriptions`.
2. Append `purchase` event.
3. Upsert `tenant_app_entitlements` to:

```text
status = enabled
valid_from = request.currentPeriodStart
valid_until = request.currentPeriodEnd
enabled_by = operator_user_id
enabled_at = now()
```

4. Upsert `store_app_settings` for all active stores under the tenant and the purchased app only when a setting is missing:

```text
is_enabled = true
entry_visible = true
```

Existing store settings must keep their current `is_enabled` and `entry_visible` values.

### Renew

Use when a tenant already has a subscription.

Effects:

1. Update billing cycle, amount, currency, note, and period.
2. Append `renew` event.
3. Upsert entitlement to `enabled`.
4. Set `valid_until` to the new current period end.

Validation:

- `currentPeriodEnd` must be after `currentPeriodStart` when both are present.
- For monthly/yearly renewals, `currentPeriodEnd` must be finite.
- For a non-legacy active subscription with an existing finite period, the new period end must be after the existing period end unless the event type is `manual_adjust`.

### Suspend

Use when access should be administratively blocked without cancelling commercial history.

Effects:

```text
subscription.status = suspended
tenant_app_entitlements.status = suspended
```

Store settings remain unchanged.

### Cancel

Use when the subscription is ended by platform administration.

Effects:

```text
subscription.status = cancelled
tenant_app_entitlements.status = disabled
```

Store settings remain unchanged so that a later purchase can reactivate tenant access without losing store visibility preferences.

### Expired

App Gate already rejects enabled/trial entitlements whose `valid_until` is not after current time.

V1 can mark `tenant_product_subscriptions.status = expired` in either of two ways:

1. Lazy read: subscription query projects `expired` when current period end is in the past.
2. Manual or scheduled maintenance: a future job updates status to `expired`.

Phase 1 uses lazy read for the UI and tests App Gate runtime denial with finite past `valid_until`. It does not require a scheduler.

### Manual Adjust

Use when platform admins need to correct period, amount, currency, or note without modelling a purchase or renewal.

Effects:

1. Update subscription fields.
2. Append `manual_adjust` event.
3. Synchronize entitlement according to the new subscription status and period.

## App Gate Synchronization Boundary

The subscription service owns tenant entitlement synchronization for billing-related operations.

Rules:

| Billing operation | Entitlement status | Entitlement dates |
|---|---|---|
| purchase | `enabled` | copy current period |
| renew | `enabled` | copy current period |
| convert_from_legacy | `enabled` | copy converted period |
| manual_adjust active | `enabled` | copy adjusted period |
| suspend | `suspended` | preserve dates |
| cancel | `disabled` | preserve dates |
| legacy grant | existing value, normally `enabled` | `valid_until = null` |

The App Gate runtime remains the final enforcement mechanism for business APIs. Reservation, queue, walk-in, seating, cleaning, and call-screen controllers continue to use `@RequireAppGate` and must not call billing services directly.

Store-level settings remain a separate App Gate layer:

```text
tenant subscription says whether the tenant bought the product line.
store app setting says whether a store can use and show that product line.
endpoint permission says whether the actor can perform a concrete action.
```

The synchronization policy should be a small domain object, not string logic spread across command handlers:

```text
EntitlementSyncPolicy.from(subscriptionBefore, subscriptionAfter, operation)
  -> entitlementStatus
  -> validFrom
  -> validUntil
  -> shouldUpsertMissingStoreSettings
```

This makes the purchase, renewal, suspension, cancellation, legacy conversion, and manual-adjust mapping testable without a database.

## API Design

All endpoints use `/api/v1` and platform-scope authentication. Request DTOs must not expose persistence entities.

### Product Lines

```http
GET /api/v1/platform/product-lines
PATCH /api/v1/platform/product-lines/{appKey}
```

Permission:

```text
role = platform_admin
permission = platform.product_line.manage
compatibility permission = platform.tenant.manage
```

`GET` response:

```json
{
  "success": true,
  "productLines": [
    {
      "appKey": "reservation_queue",
      "displayName": "预约排队叫号产线",
      "status": "active",
      "defaultEntryRoute": "/stores/:storeId/staff",
      "description": "Reservation, queue, walk-in, seating, and cleaning operational app.",
      "sortOrder": 10,
      "createdAt": "2026-06-19T03:00:00Z",
      "updatedAt": "2026-06-26T03:00:00Z"
    }
  ]
}
```

`PATCH` request:

```json
{
  "displayName": "预约排队叫号产线",
  "status": "active",
  "description": "预约、排队、叫号、入座、清台完整产线",
  "sortOrder": 10,
  "version": 0
}
```

Allowed `status` values:

```text
active
disabled
```

Disabling a product line disables runtime app access for every tenant because App Gate checks `platform_apps.status`. The UI must make that blast radius clear.

### Tenant Product Subscriptions

```http
GET /api/v1/platform/tenants/{tenantId}/product-subscriptions
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/suspend
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/cancel
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy
```

Permission:

```text
role = platform_admin
permission = platform.billing.manage
compatibility permission = platform.tenant.manage
```

`GET` response:

```json
{
  "success": true,
  "tenantId": "10000000-0000-0000-0000-000000000983",
  "subscriptions": [
    {
      "id": "61000000-0000-0000-0000-000000000001",
      "tenantId": "10000000-0000-0000-0000-000000000983",
      "appKey": "reservation_queue",
      "productLineName": "预约排队叫号产线",
      "billingCycle": "legacy_grant",
      "status": "active",
      "effectiveStatus": "active",
      "currentPeriodStart": "2026-06-19T03:00:00Z",
      "currentPeriodEnd": null,
      "amount": "0.00",
      "currency": "SGD",
      "paymentNote": "Historical permanent grant migrated from App Gate entitlement.",
      "entitlementStatus": "enabled",
      "entitlementValidUntil": null,
      "createdAt": "2026-06-26T03:00:00Z",
      "updatedAt": "2026-06-26T03:00:00Z",
      "version": 0
    }
  ]
}
```

`purchase` request:

```json
{
  "idempotencyKey": "purchase-20260626-tenant-20000000-reservation-queue",
  "appKey": "reservation_queue",
  "billingCycle": "monthly",
  "currentPeriodStart": "2026-06-26T00:00:00Z",
  "currentPeriodEnd": "2026-07-26T00:00:00Z",
  "amount": "99.00",
  "currency": "SGD",
  "paymentNote": "Manual bank transfer reference RPB-20260626-001"
}
```

`renew` and `convert-from-legacy` request:

```json
{
  "idempotencyKey": "renew-20260626-tenant-20000000-reservation-queue",
  "billingCycle": "yearly",
  "currentPeriodStart": "2026-07-26T00:00:00Z",
  "currentPeriodEnd": "2027-07-26T00:00:00Z",
  "amount": "999.00",
  "currency": "SGD",
  "paymentNote": "Manual renewal"
}
```

`suspend` and `cancel` request:

```json
{
  "idempotencyKey": "suspend-20260626-tenant-20000000-reservation-queue",
  "paymentNote": "Payment dispute, manually suspended by platform admin."
}
```

Success response for mutation commands:

```json
{
  "success": true,
  "replayed": false,
  "subscription": {
    "id": "61000000-0000-0000-0000-000000000001",
    "tenantId": "10000000-0000-0000-0000-000000000983",
    "appKey": "reservation_queue",
    "billingCycle": "yearly",
    "status": "active",
    "effectiveStatus": "active",
    "currentPeriodStart": "2026-07-26T00:00:00Z",
    "currentPeriodEnd": "2027-07-26T00:00:00Z",
    "amount": "999.00",
    "currency": "SGD",
    "paymentNote": "Manual renewal",
    "entitlementStatus": "enabled",
    "entitlementValidUntil": "2027-07-26T00:00:00Z",
    "version": 1
  }
}
```

For repeated commands using the same `idempotencyKey`, return the same shape with:

```json
{
  "success": true,
  "replayed": true
}
```

### Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `UNAUTHENTICATED` | No current actor is available. |
| 403 | `FORBIDDEN` | Actor lacks required platform role or permission. |
| 400 | `REQUEST_INVALID` | Body, dates, amount, currency, billing cycle, or idempotency key is invalid. |
| 404 | `TENANT_NOT_FOUND` | Tenant does not exist or is deleted. |
| 404 | `PRODUCT_LINE_NOT_FOUND` | `appKey` does not map to a platform app. |
| 404 | `SUBSCRIPTION_NOT_FOUND` | Subscription id is not found for the tenant. |
| 409 | `SUBSCRIPTION_CONFLICT` | Purchase already exists, renewal period is invalid, or legacy conversion is not allowed. |
| 409 | `VERSION_CONFLICT` | Optimistic version does not match current row. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Platform Backoffice UI

Add two platform pages under `基础设置`.

### Product Line Page

Route recommendation:

```text
/platform/settings/product-lines
```

Required states:

- Loading product lines.
- Empty product-line list.
- Product line list with `reservation_queue`.
- Edit form for display name, status, description, sort order.
- Save success.
- Save error.
- Permission denied.

Required warning:

```text
Disabling a product line disables runtime access for every tenant that uses this product line.
```

### Tenant Subscription / Billing Page

Route recommendations:

```text
/platform/billing/subscriptions
/platform/tenants/:tenantId/billing
```

V1 can start with tenant-detail billing:

```text
/platform/tenants/:tenantId/billing
```

Required workflows:

1. View product-line subscriptions for a tenant.
2. Show legacy grant as permanent historical grant.
3. Purchase a product line.
4. Renew current subscription.
5. Suspend subscription.
6. Cancel subscription.
7. Convert legacy grant to monthly/yearly/manual subscription.
8. Show event history.

Required states:

- Loading.
- No subscriptions.
- Legacy grant.
- Active monthly/yearly/manual subscription.
- Suspended subscription.
- Cancelled subscription.
- Expired effective status.
- Save in progress.
- Error banner.
- Permission denied.

Do not collect card numbers, bank details, tax ids, invoice data, or payment gateway tokens in V1.

## Permissions

Add dedicated platform permissions:

```text
platform.product_line.manage
platform.billing.manage
```

Compatibility rule for first release:

```text
platform_admin + platform.tenant.manage
```

is accepted for both modules so existing platform admins can access the new pages before account permissions are refreshed.

Long-term boundary:

| Permission | Owns |
|---|---|
| `platform.tenant.manage` | Tenant identity, lifecycle, tenant admin bootstrap |
| `platform.product_line.manage` | Platform product-line catalog |
| `platform.billing.manage` | Tenant subscriptions, renewals, commercial access changes |

These are platform backoffice permissions, not App Gate business permissions.

## Audit

Required audit targets:

- Product line update.
- Subscription purchase.
- Subscription renew.
- Subscription suspend.
- Subscription cancel.
- Convert from legacy.
- Manual adjust.
- Entitlement synchronization failure.

Audit must include:

```text
actor id
actor role
tenant id when tenant-scoped
app key
subscription id when available
operation code
before JSON
after JSON
payment note when provided
idempotency key
created time
```

The subscription event table is the commercial event history. The general `audit_logs` table remains the platform operational audit trail.

## API Review Notes

- Paths use `/api/v1`.
- Product line API is platform-scoped and must not require tenant or store route parameters.
- Subscription API is tenant-scoped and must derive the operator from the authenticated platform actor.
- Request and response DTOs must be explicit records; no JPA entity or persistence row is returned directly.
- Error codes are stable and documented.
- Mutating subscription endpoints require an `idempotencyKey`.
- Endpoint permissions must be checked before service mutation.
- App Gate is not used to authorize platform backoffice billing endpoints. App Gate is used for runtime product-line business APIs.

## Database Review Notes

- `platform_apps` remains platform-level.
- `tenant_product_subscriptions` and `tenant_product_subscription_events` require `tenant_id`.
- No `store_id` is added to subscription rows because billing is tenant-level.
- `app_key` references `platform_apps(app_key)`.
- One current subscription row is unique by `(tenant_id, app_key)`.
- Events are append-only and use a unique idempotency key per tenant/app/event type.
- The migration must not update existing `tenant_app_entitlements.valid_until` values.
- Legacy grant backfill inserts subscription rows but does not change runtime entitlement behavior.

## TDD Review Checklist

| Scenario | Required Coverage |
|---|---|
| Migration constraints | Tables, checks, unique keys, FK coverage, legacy backfill |
| Product line display | `reservation_queue` appears as `预约排队叫号产线` |
| Legacy grant | Existing `valid_until = null` tenant remains allowed by App Gate |
| Purchase | Subscription active and entitlement enabled with copied dates |
| Renew | `valid_until` extends and duplicate idempotency key does not extend twice |
| Expired | Finite past `valid_until` is rejected by App Gate |
| Suspend | Subscription suspended and entitlement suspended; business API denied |
| Cancel | Subscription cancelled and entitlement disabled; business API denied |
| Platform permission | Missing dedicated and compatibility permissions returns 403 |
| Cross-tenant safety | Billing API cannot mutate another tenant through body data |
| Frontend loading | Product-line and billing pages render loading states |
| Frontend error | API errors show stable error banners |
| Frontend saving | Save buttons disable while mutation is in flight |

## Phase 1 Boundary

Phase 1 implements:

- Product line catalog page over `platform_apps`.
- Product line display name `预约排队叫号产线` for `reservation_queue`.
- Dedicated platform product-line and billing permissions with compatibility fallback.
- `tenant_product_subscriptions`.
- `tenant_product_subscription_events`.
- Legacy grant backfill without entitlement expiry.
- Manual purchase, renew, suspend, cancel, convert-from-legacy.
- Subscription to App Gate entitlement synchronization.
- Store setting upsert for missing settings on purchase.
- Platform billing pages and API clients.
- Tests listed in the TDD checklist.

Phase 1 does not implement:

- `platform_product_line_prices`.
- Payment gateway.
- Automatic renewal.
- Invoice or tax documents.
- Webhook handling.
- Tenant self-service checkout.
- Billing scheduler.
- External accounting integration.

## Phase 2 Boundary

Phase 2 may add:

- `platform_product_line_prices`.
- Scheduled expiry status update job.
- Automatic renewal reminders.
- Tenant self-service payment flow.
- Payment provider integration.
- Invoice generation.
- Webhook retry and reconciliation.
- Export for accounting.
- Product-line bundles or discounts.

Phase 2 must keep the same runtime boundary: commercial state synchronizes into App Gate entitlement, and business APIs continue to be protected by App Gate.

## Open Implementation Decisions

These decisions are technical implementation choices, not product blockers:

1. Whether to store subscription audit metadata only in `audit_logs`, only in event rows, or both. Recommended: both.
2. Whether idempotency uses the shared `idempotency_records` table or the event table unique key. Recommended Phase 1: event table unique key for platform billing commands.
3. Whether the tenant billing route is only tenant detail or also a global billing search page. Recommended Phase 1: tenant detail route plus product-line filter on tenant list if needed.
4. Whether open-ended manual billing is allowed. Recommended: allowed only with `billingCycle = manual`; monthly/yearly must have finite period end.
