# Platform Product Line Billing Frontend Implementation Report

Status: Phase 1 implemented, Phase 1.1 pricing and duration flow implemented

## Files

- `src/types/platformProductLineBilling.ts`
- `src/api/platformProductLineBillingApi.ts`
- `src/pages/PlatformProductLinesPage.vue`
- `src/pages/PlatformTenantBillingPage.vue`
- `src/router/index.ts`
- `src/components/platform/PlatformAdminNav.vue`
- `src/components/platform/PlatformTenantTable.vue`
- `src/pages/PlatformTenantsPage.vue`

## User Flows

Platform product line management:

- load product lines
- edit display metadata for `reservation_queue`
- edit monthly and yearly product-line prices
- save changes

Tenant billing management:

- enter billing management from the platform `租户计费` navigation item
- select a tenant from the billing-mode tenant list
- load tenant subscriptions
- show unopened product lines as `未开通`
- identify legacy grant subscriptions
- enter duration count in months or years instead of effective dates
- default amount from product-line price times duration count
- manually purchase a product line
- renew, suspend, cancel, or convert a subscription

## States

Both pages expose loading, error, and saving states.

The tenant billing table shows product line, billing cycle, status, period start, period end, amount, currency, entitlement status, and available manual actions.

## Exclusions

Phase 1.1 does not implement payment checkout, card binding, automatic billing, invoice pages, webhook displays, discounts, tax calculation, media-related features, or queue display/call screen changes.
