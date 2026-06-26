# Platform Product Line Pricing And Billing Usability Design

## Status

Design round only. This document does not create executable schema, Java code, Vue code, runtime configuration, dependency changes, production data changes, or seed data changes.

This is a Phase 1.1 refinement on top of:

- `docs/superpowers/specs/2026-06-26-platform-product-line-billing-design.md`
- `docs/superpowers/plans/2026-06-26-platform-product-line-billing.md`

Phase 1.1 keeps the approved boundary:

```text
Lightweight manual billing + App Gate entitlement activation.
```

It improves platform-admin usability by adding product-line prices and replacing manual date picking with duration-based period calculation.

## Goals

1. Let platform admins maintain monthly and yearly prices on each product line.
2. Let tenant billing operate as a product-line checklist for one tenant.
3. Default tenant billing amount from product-line pricing.
4. Let platform admins enter month or year count instead of choosing effective start and end dates.
5. Calculate billing periods consistently on the backend and preview them in the frontend.
6. Keep App Gate as the final runtime authorization boundary.
7. Keep payment collection manual; do not add payment gateway, invoice, webhook, or automatic charging.

## Non-Goals

- No online payment gateway.
- No automatic recurring charge.
- No tenant self-service checkout.
- No invoice, tax, receipt, or accounting export.
- No discount, coupon, promotion, or bundled product-line pricing.
- No per-store product-line price.
- No change to reservation, queue, call screen, seating, cleaning, or walk-in business state machines.
- No direct billing checks inside business controllers or services.

## Core Product Decision

### Product Line Pricing

`基础设置 > 产品线` owns the standard platform price for each product line.

For `reservation_queue`, show:

```text
预约排队叫号产线
月付价格
年付价格
币种
状态
说明
排序
```

The product-line price is a default quote, not a historical billing record. When an admin purchases or renews a subscription, the current price is copied into that billing operation as a snapshot.

### Tenant Billing As Product-Line Selection

`平台 > 租户管理 > 计费` opens the selected tenant's subscription page.

The tenant subscription page should read like a product-line checklist:

```text
租户：食刻租户

[x] 预约排队叫号产线
    状态：生效中
    当前有效期：2026-06-26 15:00 ~ 2026-07-26 15:00
    计费周期：月付
    本次续费：3 个月
    标准单价：128.00 SGD / 月
    本次金额：384.00 SGD
```

Checking a product line means purchase or reactivate access for that tenant. Unchecking an active product line must not silently cancel access; it must ask the admin to choose `暂停` or `取消` with confirmation.

### Billing Workspace

`计费 > 租户计费` is a tenant billing workbench. It lists or searches tenants and routes into the same tenant-level `订阅 / 计费` page. It does not create a second product-line configuration surface.

## Duration-Based Period Calculation

The UI must not require admins to pick `currentPeriodStart` or `currentPeriodEnd` for monthly and yearly subscriptions.

Instead, the admin enters:

```text
计费周期：月付 / 年付
购买数量：N 个月 / N 年
```

### Calculation Rules

The backend is the source of truth for period calculation.

| Operation | Start anchor | End calculation |
|---|---|---|
| purchase with no existing subscription | `now(clock)` | start + duration |
| renew active finite subscription whose end is in future | current period end | current period end + duration |
| renew expired finite subscription | `now(clock)` | now + duration |
| renew suspended finite subscription | max(current period end, now) | start + duration |
| convert legacy grant | `now(clock)` | now + duration |
| reactivate cancelled subscription | `now(clock)` | now + duration |

Duration rules:

- `billingCycle = monthly` means `durationCount` is a number of calendar months.
- `billingCycle = yearly` means `durationCount` is a number of calendar years.
- `durationCount` must be an integer from 1 to 120 for monthly billing.
- `durationCount` must be an integer from 1 to 10 for yearly billing.
- Calendar arithmetic uses Java time `plusMonths` and `plusYears`; edge dates follow Java's normal adjustment behavior, for example January 31 plus one month becomes the last valid day in February.
- The frontend may preview dates, but the backend result is authoritative.

### Manual Period Editing

Phase 1.1 removes visible date inputs from the normal purchase and renew workflow.

Manual date adjustment remains out of the primary flow. A future advanced adjustment workflow may expose exact dates behind a separate `manual_adjust` action, but that is not part of this Phase 1.1 usability pass.

## Price Calculation

The product-line price table stores one monthly price and one yearly price per product line.

For a billing command:

```text
defaultAmount = unitPrice * durationCount
```

Examples:

| Cycle | Unit price | Duration count | Default amount |
|---|---:|---:|---:|
| monthly | 128.00 SGD | 1 | 128.00 SGD |
| monthly | 128.00 SGD | 3 | 384.00 SGD |
| yearly | 1200.00 SGD | 1 | 1200.00 SGD |
| yearly | 1200.00 SGD | 2 | 2400.00 SGD |

Because Phase 1.1 remains manual billing, the UI should allow `本次金额` to be adjusted after defaulting from price. The event payload must record both:

- standard unit price used for the quote
- final charged amount entered by the platform admin

This preserves manual flexibility without losing traceability.

## Data Design

### New Table: `platform_product_line_prices`

Phase 1.1 should add a formal price table.

Next migration number in the current repository sequence:

```text
V010__platform_product_line_prices.sql
```

Design-level DDL:

```sql
create table platform_product_line_prices (
    id uuid primary key default gen_random_uuid(),
    app_key text not null references platform_apps(app_key),
    billing_cycle text not null,
    amount numeric(12, 2) not null,
    currency text not null default 'SGD',
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint ux_platform_product_line_prices_scope unique (app_key, billing_cycle),
    constraint ck_platform_product_line_prices_cycle check (billing_cycle in ('monthly', 'yearly')),
    constraint ck_platform_product_line_prices_amount check (amount >= 0),
    constraint ck_platform_product_line_prices_currency check (currency = upper(currency) and length(currency) = 3),
    constraint ck_platform_product_line_prices_status check (status in ('active', 'disabled'))
);
```

Seed rows:

```text
reservation_queue monthly SGD 0.00 active
reservation_queue yearly  SGD 0.00 active
```

The zero values are explicit initial prices that require platform administrators to configure commercial amounts in the UI. They are not commercial recommendations.

### Subscription Tables

Do not replace `tenant_product_subscriptions`.

The current subscription row remains the current commercial state:

```text
tenant_id
app_key
billing_cycle
status
current_period_start
current_period_end
amount
currency
payment_note
operator_user_id
version
```

The `amount` field stores the final amount for the latest operation. It may equal the default calculated amount or a manually adjusted amount.

### Event Payload Price Snapshot

Use `tenant_product_subscription_events.event_payload` to store quote details for each purchase, renew, and legacy conversion:

```json
{
  "durationCount": 3,
  "durationUnit": "month",
  "unitAmount": "128.00",
  "defaultAmount": "384.00",
  "finalAmount": "384.00",
  "currency": "SGD",
  "priceSource": "platform_product_line_prices",
  "periodCalculatedBy": "backend"
}
```

This avoids adding many current-row columns while keeping an auditable price snapshot per operation.

## API Design

### Product Lines

Keep the existing endpoints:

```http
GET /api/v1/platform/product-lines
PATCH /api/v1/platform/product-lines/{appKey}
```

Add price data to the list response:

```json
{
  "success": true,
  "productLines": [
    {
      "appKey": "reservation_queue",
      "displayName": "预约排队叫号产线",
      "status": "active",
      "defaultEntryRoute": "/stores/:storeId/staff",
      "description": "预约、排队、叫号一体化产线",
      "sortOrder": 10,
      "prices": [
        {
          "billingCycle": "monthly",
          "amount": 128.00,
          "currency": "SGD",
          "status": "active",
          "version": 0
        },
        {
          "billingCycle": "yearly",
          "amount": 1200.00,
          "currency": "SGD",
          "status": "active",
          "version": 0
        }
      ]
    }
  ]
}
```

Add a focused price update endpoint:

```http
PATCH /api/v1/platform/product-lines/{appKey}/prices
```

Request:

```json
{
  "prices": [
    {
      "billingCycle": "monthly",
      "amount": 128.00,
      "currency": "SGD",
      "status": "active",
      "version": 0
    },
    {
      "billingCycle": "yearly",
      "amount": 1200.00,
      "currency": "SGD",
      "status": "active",
      "version": 0
    }
  ]
}
```

Permission remains:

```text
platform_admin + platform.product_line.manage
compatibility: platform_admin + platform.tenant.manage
```

### Tenant Product Subscriptions

Keep existing endpoint names:

```http
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy
```

Phase 1.1 request shape:

```json
{
  "idempotencyKey": "purchase-reservation-queue-20260626-001",
  "appKey": "reservation_queue",
  "billingCycle": "monthly",
  "durationCount": 3,
  "amount": 384.00,
  "currency": "SGD",
  "paymentNote": "Manual bank transfer"
}
```

Rules:

- `durationCount` is required for `monthly` and `yearly`.
- `currentPeriodStart` and `currentPeriodEnd` are not used by the normal UI.
- The backend calculates start and end.
- If `amount` is absent, the backend uses product-line price times `durationCount`.
- If `amount` is present, it is treated as the final manual amount and must be non-negative.
- Currency defaults from product-line price when absent.

Response includes calculated period and quote:

```json
{
  "success": true,
  "replayed": false,
  "subscription": {
    "appKey": "reservation_queue",
    "billingCycle": "monthly",
    "status": "active",
    "currentPeriodStart": "2026-06-26T07:30:00Z",
    "currentPeriodEnd": "2026-09-26T07:30:00Z",
    "amount": 384.00,
    "currency": "SGD",
    "entitlementStatus": "enabled",
    "entitlementValidUntil": "2026-09-26T07:30:00Z",
    "version": 1
  },
  "quote": {
    "durationCount": 3,
    "durationUnit": "month",
    "unitAmount": 128.00,
    "defaultAmount": 384.00,
    "finalAmount": 384.00,
    "currency": "SGD"
  }
}
```

## Module Boundaries And OOD

Add product-line pricing as a focused part of `platformbilling`.

Recommended objects:

| Object | Responsibility |
|---|---|
| `PlatformProductLinePrice` | Product-line price for one cycle. |
| `ProductLinePriceCatalogService` | List and update monthly/yearly prices. |
| `ProductLinePriceRepository` | Persist `platform_product_line_prices`. |
| `BillingDuration` | Validate duration count and cycle unit. |
| `BillingPeriodCalculator` | Calculate start/end from operation, subscription state, duration, and clock. |
| `SubscriptionQuote` | Unit price, duration, default amount, final amount, currency. |
| `SubscriptionQuoteService` | Resolve current product-line price and create quote snapshots. |

Dependency rules:

1. `platformbilling` owns product-line pricing and subscription quote calculation.
2. `platformbilling` may write App Gate entitlement dates only after backend period calculation.
3. `appgate` does not depend on product-line prices.
4. Reservation, queue, queuedisplay, cleaning, walkin, and tenant-admin modules do not depend on pricing or billing.
5. Controllers receive duration-based commands and delegate calculation to application/domain services.

## Frontend UX

### Product Line Page

Add a `定价` section to `PlatformProductLinesPage.vue`.

Fields:

```text
月付价格
年付价格
币种
价格状态
```

The product-line metadata save and price save may be separate buttons:

```text
保存产品线
保存定价
```

Separate buttons keep product metadata and pricing intent clear.

### Tenant Billing Page

Replace date inputs with duration inputs.

For purchase or renew:

```text
计费周期：月付 / 年付
购买数量：1 [个月/年]
标准单价：128.00 SGD / 月
默认金额：128.00 SGD
本次金额：128.00 SGD
预计有效期：保存后由系统计算；页面先展示预览
```

The UI may show a preview:

```text
预计有效期：当前到期后延长 3 个月
```

The preview is informational. The saved response from backend updates the actual period displayed in the table.

### Checklist Behavior

Product-line rows should be visually scannable:

| Row State | UI |
|---|---|
| no subscription | unchecked, action label `开通` |
| legacy grant | checked, badge `历史赠送 / 永久有效`, action `转付费` |
| active finite | checked, badge `生效中`, action `续费` |
| suspended | checked, badge `已暂停`, actions `恢复并续费`, `取消` |
| cancelled | unchecked or disabled checked, action `重新开通` |
| expired | checked, badge `已到期`, action `续费` |

## Permissions

No new permission is required.

Use existing Phase 1 permissions:

```text
platform.product_line.manage
platform.billing.manage
```

Compatibility remains:

```text
platform_admin + platform.tenant.manage
```

## Test Requirements

### Migration

- `platform_product_line_prices` exists.
- `(app_key, billing_cycle)` is unique.
- Monthly/yearly constraints reject invalid cycles.
- Amount cannot be negative.
- Currency must be uppercase 3-letter code.
- `reservation_queue` has monthly and yearly seed rows.

### Backend Application

- Product-line API returns monthly and yearly prices.
- Price update changes monthly/yearly rows and increments version.
- Missing price returns a stable error for purchase/renew.
- Purchase with `durationCount = 3` monthly calculates three calendar months.
- Renew active subscription extends from current future end.
- Renew expired subscription starts from now.
- Convert legacy starts from now and sets finite end.
- Idempotent replay does not extend period twice.
- Amount defaults from price times duration.
- Manual amount override is stored and event payload preserves unit/default/final amounts.
- App Gate receives calculated `valid_from` and `valid_until`.

### Frontend

- Product-line page shows monthly/yearly price fields.
- Tenant billing page shows product-line checklist.
- Tenant billing page has no visible date inputs in the normal flow.
- Duration count changes update amount preview.
- Saving state disables actions.
- Permission-denied and API error states render.

## Phase 1.1 Boundary

Phase 1.1 includes:

- `platform_product_line_prices`
- monthly/yearly price editing on product-line page
- product-line checklist style tenant billing UI
- duration-count based purchase, renew, and legacy conversion
- backend period calculation
- price snapshot in subscription event payload
- tests and docs for the new behavior

Phase 1.1 excludes:

- payment gateway
- invoice
- webhook
- automatic renewal
- tax calculation
- discount/coupon logic
- tenant self-service payment
- exact-date manual adjustment UI
