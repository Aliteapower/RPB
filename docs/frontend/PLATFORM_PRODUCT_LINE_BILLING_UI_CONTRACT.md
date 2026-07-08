# Platform Product Line Billing UI Contract

Status: Phase 1 implemented, Phase 1.1 pricing and duration flow implemented, Phase 1.2 store billing line items implemented, Phase 1.3 store item renewal implemented

## Pages

### Product Lines

Route:

- `/platform/settings/product-lines`

Page:

- `src/pages/PlatformProductLinesPage.vue`

Responsibilities:

- list product lines
- show `reservation_queue`
- show display name `预约排队叫号产线`
- show the read-only App Gate default entry route
- edit display name, status, description, sort order
- edit monthly and yearly product-line prices
- show loading, error, and saving states

### Tenant Billing

Index route:

- `/platform/billing/subscriptions`

Index page:

- reuses `src/pages/PlatformTenantsPage.vue` in billing mode

Responsibilities:

- list tenants
- provide a clear `订阅/计费` row action
- route into tenant-specific billing management

Route:

- `/platform/tenants/:tenantId/billing`

Page:

- `src/pages/PlatformTenantBillingPage.vue`

Responsibilities:

- list product lines with tenant subscription state
- show `历史赠送 / 永久有效` for `legacy_grant`
- show period start, period end, amount, currency, and entitlement status
- show unopened product lines as `未开通`
- use `购买数量` in months or years instead of visible date inputs
- default purchase and legacy conversion amount from product-line price times duration count times active store count
- default store renewal amount from product-line price times duration count times one selected store
- show `计费门店数`, `单店金额`, and selectable store billing detail rows when the API returns line items
- disable purchase, renewal, and conversion actions when the tenant has no active billable stores
- use selected store item id and item version for normal paid renewals
- manually purchase, renew, suspend, cancel, and convert legacy grant
- generate idempotency keys client-side for manual operations
- show loading, error, and saving states

Normal purchase and renewal UI must not render `datetime-local` inputs. The backend calculates effective dates and returns the authoritative period.

## Navigation

`PlatformAdminNav.vue` includes:

- `租户管理`
- `租户计费`
- `产品线`
- `叫号模板`

`PlatformTenantTable.vue` includes a `计费` row action in tenant management and a clearer `订阅/计费` action in billing mode.

## Excluded Phase 1 UI

The UI does not include payment gateway configuration, tenant payment checkout, invoices, webhooks, auto-renewal schedules, discounts, tax calculation, or exact-date manual adjustment.
