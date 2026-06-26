# Platform Product Line Billing UI Contract

Status: Phase 1 implemented

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
- edit display name, status, description, sort order
- show loading, error, and saving states

### Tenant Billing

Route:

- `/platform/tenants/:tenantId/billing`

Page:

- `src/pages/PlatformTenantBillingPage.vue`

Responsibilities:

- list tenant product subscriptions
- show `历史赠送 / 永久有效` for `legacy_grant`
- manually purchase, renew, suspend, cancel, and convert legacy grant
- generate idempotency keys client-side for manual operations
- show loading, error, and saving states

## Navigation

`PlatformAdminNav.vue` includes:

- `租户管理`
- `产品线`
- `叫号模板`

`PlatformTenantTable.vue` includes a `计费` row action leading to tenant billing.

## Excluded Phase 1 UI

The UI does not include payment gateway configuration, tenant payment checkout, invoices, webhooks, auto-renewal schedules, or price template management.
