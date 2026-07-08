# Store-Level Platform Billing Design

## Status

Design round only. This document does not create executable schema, Java code, Vue code, runtime configuration, dependency changes, production data changes, or seed data changes.

This design records the approved product direction:

```text
Platform billing renews and calculates by operating entity and by store.
```

The current implementation already has store billing line items, but those rows are still commercial details under one tenant-level subscription. This design upgrades those line items into the current billing state for each store, while keeping the tenant-level subscription row as the aggregate product-line container.

## User Requirement

The platform billing page must let platform admins manage product-line billing by group and by store:

- A group tenant may contain multiple operating entities.
- Each operating entity owns its own stores.
- Platform admins need to renew stores independently.
- Stores must not be mixed across tenants or operating entities in the UI.
- The model should reserve a clean path for future store self-payment.

## Current State

The existing product-line billing model is:

- `tenant_product_subscriptions`: one subscription per tenant and product line.
- `tenant_product_subscription_items`: per-store commercial detail rows created under the subscription.
- `tenant_product_subscription_events`: tenant-level purchase, renew, suspend, cancel, and conversion events.
- App Gate entitlement remains tenant-scoped through `tenant_app_entitlements`.

The current duration-based purchase and renewal flow calculates:

```text
unit price * duration * active billable store count
```

Then it replaces all store item rows for the tenant subscription. This is good for displaying per-store details, but it does not allow one store to renew, expire, suspend, or pay independently.

## Goals

1. Make each store under a group tenant independently billable and renewable.
2. Keep stores grouped by operating entity in the platform billing UI.
3. Prevent cross-tenant and cross-operating-entity confusion in both API validation and UI selection.
4. Preserve existing tenant-level subscription APIs where possible for compatibility.
5. Add store-specific platform billing APIs for opening, renewing, suspending, cancelling, and later self-payment.
6. Keep the quote logic simple: one store renewal equals one store unit price multiplied by the selected duration.
7. Recompute tenant-level subscription aggregate fields from the store billing rows.
8. Reserve event and API fields for future store self-payment without adding a payment gateway in this slice.

## Non-Goals

- No payment gateway integration.
- No invoice, tax, receipt, accounting, webhook, or automatic collection module.
- No tenant self-service payment UI in this slice.
- No automatic retry, dunning, or payment reconciliation flow.
- No new product-line catalog model.
- No broad rewrite of App Gate runtime authorization in this slice.
- No cross-tenant store access or cross-tenant billing transfer.

## Key Decision

### Store Items Become Store Billing State

`tenant_product_subscription_items` should no longer be treated as disposable display details only. It becomes the current billing state row for a store under one tenant product-line subscription.

Each row represents:

```text
tenant + product line + store = current store billing state
```

The row must carry its own billing period, billing cycle, status, amount, and version so a platform admin can renew one store without touching the other stores.

### Tenant Subscription Remains the Aggregate Container

`tenant_product_subscriptions` remains the aggregate product-line subscription row for compatibility and App Gate synchronization.

It should be derived from store items after store-level mutations:

- `status`: active if at least one store item is effectively active; suspended/cancelled/expired only when all item states resolve that way.
- `amount`: aggregate amount of currently billable store items.
- `current_period_start`: earliest current store period start for active/current rows.
- `current_period_end`: latest current store period end for active/current rows.
- `version`: increments when aggregate state changes.

This keeps existing product-line listing and App Gate sync stable while allowing platform billing to operate at the store level.

### App Gate Scope For This Slice

Runtime App Gate entitlement remains tenant-scoped in this slice.

The platform billing state is store-level, but product access enforcement remains:

```text
tenant product entitlement is enabled while at least one store item is active and unexpired
```

This is intentionally scoped. If the business requirement becomes "one unpaid store must lose product access while sibling stores keep access", that needs a later store-scoped App Gate extension. This design reserves clean billing state for that future work, but does not add store-scoped App Gate enforcement now.

## Data Model

### `tenant_product_subscription_items`

Add store-current-state fields:

| Column | Purpose |
|---|---|
| `billing_cycle` | Store row billing cycle, such as monthly or yearly. |
| `current_period_start` | Store billing period start. |
| `current_period_end` | Store billing period end. |
| `payment_note` | Latest manual platform billing note for this store row. |
| `last_event_id` | Optional link to the latest store billing event for traceability. |

Keep existing fields:

- `subscription_id`
- `tenant_id`
- `app_key`
- `scope_type`
- `store_id`
- `quantity`
- `unit_amount`
- `amount`
- `currency`
- `status`
- `version`

Required constraints:

- Store-scoped rows must have `store_id`.
- Store FK must remain scoped by `(store_id, tenant_id)`.
- Billing cycle must match the existing product-line billing cycle enum.
- Period end must be greater than period start when both are present.
- Amounts must be non-negative.
- Currency must remain a three-character uppercase code.
- The unique current-state key remains one row per `(subscription_id, store_id)` for store scope.

Recommended indexes:

- `(tenant_id, app_key, status)`
- `(tenant_id, store_id, app_key)`
- `(subscription_id, status)`
- `(tenant_id, app_key, current_period_end)`

### Store Billing Events

Create a new `tenant_product_subscription_item_events` table instead of overloading tenant-level subscription events.

The event table should include:

| Column | Purpose |
|---|---|
| `id` | Event id. |
| `subscription_item_id` | Target store billing row. |
| `subscription_id` | Aggregate tenant subscription. |
| `tenant_id` | Tenant scope. |
| `app_key` | Product line. |
| `store_id` | Store scope. |
| `event_type` | open, renew, suspend, cancel, expire, self_payment_reserved. |
| `billing_cycle` | Snapshot cycle. |
| `status` | Resulting item status. |
| `period_start` | Snapshot period start. |
| `period_end` | Snapshot period end. |
| `amount` | Charged/manual amount for this event. |
| `currency` | Event currency. |
| `payment_note` | Manual note. |
| `idempotency_key` | Prevent duplicate operations. |
| `actor_type` | platform_admin now; store_admin/customer later. |
| `payment_channel` | manual now; reserved for gateway/self-pay later. |
| `operator_user_id` | Platform user when available. |
| `event_payload` | JSON details such as quote inputs and override reason. |
| `created_at` | Event time. |

Required constraints:

- `store_id` must belong to `tenant_id`.
- `subscription_item_id` must belong to `subscription_id`.
- Idempotency must be enforced per tenant, app, store, event type, and idempotency key.

## Backend Behavior

### Opening A Store

When an active store under the selected tenant/product line does not have a billing item, platform admins can open that store.

The backend must:

1. Validate the tenant, subscription, product line, and store all match.
2. Validate the store is active and not deleted.
3. Calculate one-store quote:

   ```text
   product-line unit price * duration count
   ```

4. Create or activate the store item with its own period and amount.
5. Append a store billing event.
6. Recompute the tenant subscription aggregate.
7. Sync App Gate tenant entitlement from the aggregate subscription.

### Renewing A Store

Store renewal updates only the target store item.

Renewal period anchoring:

- If the store item is active and `current_period_end` is in the future, renew from that end date.
- Otherwise renew from the requested `current_period_start` or current server time.
- The new end date is calculated from the selected billing cycle and duration count.

The backend must not replace sibling store items during store renewal.

### Suspending Or Cancelling A Store

Store suspension or cancellation changes only the selected store item.

After mutation:

1. Append a store billing event.
2. Recompute the tenant subscription aggregate.
3. Sync App Gate tenant entitlement from aggregate state.

If all store items become inactive, the aggregate tenant subscription and App Gate entitlement should no longer remain enabled.

### Existing Tenant-Level Actions

Existing tenant-level purchase/renew/suspend/cancel endpoints can remain for compatibility, but their behavior must be clearly framed as bulk operations:

- Tenant-level purchase/open can create store items for all selected or all active stores.
- Tenant-level renew can renew all selected or all active store items.
- Tenant-level suspend/cancel can suspend/cancel all selected or all active store items.

If the current UI no longer exposes bulk actions, the backend endpoints can still stay available for administrative compatibility.

## API Design

Keep existing list endpoint:

```text
GET /api/v1/platform/tenants/{tenantId}/product-subscriptions
```

Extend item response fields:

- `billingCycle`
- `currentPeriodStart`
- `currentPeriodEnd`
- `effectiveStatus`
- `paymentNote`
- `lastEventId`
- `version`

Add platform store billing endpoints:

```text
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/stores/{storeId}/open
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/suspend
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/cancel
```

Add a platform-wide store billing index endpoint for the page:

```text
GET /api/v1/platform/billing/store-subscriptions
```

This endpoint exists so `/platform/billing/subscriptions` can show store billing status across groups without doing one request per tenant.

Query parameters:

- `appKey`: product line filter, default first active product line.
- `keyword`: searches tenant code/name, tenant phone/address, operating entity code/name, operating entity phone/address, and store code/name.
- `billingStatus`: all, active, due_soon, expired, suspended, cancelled, not_opened.
- `tenantStatus`: all, active, suspended, closed.
- `dueBefore`: optional due-date filter.
- `limit`
- `offset`

Default due-soon logic:

```text
currentPeriodEnd >= now(clock) and currentPeriodEnd <= now(clock) + 30 days
```

If `dueBefore` is provided, use `currentPeriodEnd <= dueBefore` instead of the default 30-day upper bound. Expired rows remain `expired`, not `due_soon`.

Response rows should represent one tenant/product-line/store slot, including stores that do not have a billing item yet:

- tenant id, code, name, status
- operating entity id, code, name, status
- store id, code, name, status
- product line app key and display name
- subscription id if present
- subscription item id if present
- item billing cycle if present
- item status and effective status
- current period start/end if present
- amount, unit amount, currency if present
- action state: openable, renewable, suspendable, cancellable
- updated timestamp

Pagination is by store slot row, not by tenant. A tenant with five stores can occupy five rows.

The backend must build this response with tenant/store scoped joins. It must not allow a billing item from one tenant to appear under another tenant or operating entity.

Open and renew requests should accept:

- `billingCycle`
- `durationCount`
- `currentPeriodStart` optional
- `currentPeriodEnd` optional override only if existing tenant-level API already supports it
- `amount` optional manual override
- `currency`
- `paymentNote`
- `idempotencyKey`
- `version` for item renew/suspend/cancel

Validation errors should include stable codes for:

- tenant not found
- subscription not found
- store not found in tenant
- store inactive
- item not found in subscription
- product line mismatch
- billing cycle mismatch
- duplicate idempotency key
- version conflict
- invalid duration
- invalid amount

## UI Design

The platform billing page should become store-centered instead of product-line-row-centered.

Recommended layout:

1. Product line selector at the top.
2. Aggregate summary for the selected product line:
   - active billed stores
   - due soon stores
   - expired stores
   - total current amount
   - aggregate entitlement state
3. Operating entity selector or tabs.
4. Store billing table for the selected operating entity only.
5. Store billing operation panel for the selected store.

Store rows show:

- store name
- store code
- operating entity name
- billing status
- billing cycle
- current period end
- current amount
- currency
- actions: open, renew, suspend, cancel

The UI must not show all operating entities' stores as one mixed list. Stores should be grouped under the selected operating entity so platform admins can reason about the group structure and avoid choosing the wrong store.

For stores without an item:

- Show "not opened" billing state.
- Show "open store" action.
- Do not show a renewal action until the item exists.

For future store self-payment:

- Do not expose a tenant self-pay screen yet.
- Keep the store item id, store id, event actor fields, and payment channel fields stable enough that a later store-admin checkout can reuse the same item mutation/event model.

### Platform Billing Subscriptions Page

`/platform/billing/subscriptions` must become a dedicated billing workbench, not the tenant management table in billing mode.

Current route behavior:

```text
/platform/billing/subscriptions -> PlatformTenantsPage with billingOnly=true
```

Target route behavior:

```text
/platform/billing/subscriptions -> PlatformBillingSubscriptionsPage
```

The page should answer the platform admin's daily billing question:

```text
Which group, operating entity, and store needs billing action?
```

Recommended layout:

1. Product line selector.
2. Billing status segmented filters:
   - all
   - due soon
   - expired
   - not opened
   - active
   - suspended/cancelled
3. Search box for group, operating entity, store, phone, or address.
4. Store billing index grouped visually by group and operating entity.
5. Row actions for the target store:
   - open
   - renew
   - suspend
   - cancel
   - details

Desktop table columns:

- group tenant code/name
- operating entity
- store code/name
- product line
- billing status
- period end
- amount
- latest update
- actions

Mobile cards should keep the same hierarchy:

```text
Group tenant
Operating entity
Store
Billing status and action
```

The page should not show only tenant contact fields such as principal, phone, address, and tenant update time. Those are useful for tenant management, but they do not help platform admins renew a specific store.

The primary action can open the same store billing operation panel used by the tenant billing detail page. A secondary details action can navigate to:

```text
/platform/tenants/{tenantId}/billing?appKey={appKey}&operatingEntityId={entityId}&storeId={storeId}
```

This keeps quick renewals available from the global list while preserving the full tenant billing detail page for deeper inspection.

## Isolation Rules

Every store billing mutation must validate:

1. The path `tenantId` owns the subscription.
2. The subscription `appKey` matches the product line being mutated.
3. The store belongs to the same `tenantId`.
4. The item belongs to the subscription.
5. The item store belongs to the same `tenantId`.
6. The operating entity shown in UI is used only as a selector/grouping dimension; tenant and store FKs remain the hard isolation boundary.

The frontend should also filter by selected operating entity, but backend validation is authoritative.

## Test Plan

Backend tests:

1. Migration adds item period fields and store item events without breaking existing rows.
2. Store open creates one item for the selected store and does not create sibling items.
3. Store renew changes only the selected item amount and period.
4. Store renew quote equals one store unit price multiplied by duration.
5. Store renew rejects an item from another tenant or another subscription.
6. Store renew rejects stale item version.
7. Duplicate idempotency key returns the original event/result and does not extend twice.
8. Aggregate tenant subscription recomputes after one store renew.
9. App Gate tenant entitlement remains enabled while at least one store item is active.
10. App Gate tenant entitlement becomes disabled/suspended when no store items are effectively active.

Frontend tests or build validation:

1. Product-line billing page groups store rows by operating entity.
2. Changing operating entity changes the visible store rows.
3. Store operation panel targets the selected store only.
4. Stores without billing items show open action instead of renew.
5. Store amount preview uses one-store pricing.
6. `/platform/billing/subscriptions` uses a dedicated billing subscriptions page instead of the tenant management table.
7. Billing subscriptions page groups index rows by group tenant and operating entity.
8. Billing subscriptions page shows store billing status, period, amount, and store action instead of tenant contact columns only.
9. Production build succeeds.

Store billing index API tests:

1. The index returns active stores without billing items as `not_opened`.
2. The index returns item status and effective status for stores with billing items.
3. Keyword search can find tenant, operating entity, and store fields.
4. Billing status filters return due soon, expired, not opened, and active rows correctly.
5. Rows never join billing items across tenants.
6. Pagination remains stable when a tenant has multiple stores.

Manual smoke:

1. Create or select a group tenant with two operating entities and at least three stores.
2. Open or renew one store under one operating entity.
3. Confirm sibling stores keep their previous period and amount.
4. Confirm platform billing page does not mix stores from different operating entities in the active selection.
5. Confirm product-line aggregate remains coherent after item mutation.
6. Open `/platform/billing/subscriptions` and confirm it shows group, operating entity, store, billing status, due date, amount, and store actions.
7. Filter the global billing page to due soon or not opened stores and renew/open one target store.

## Documentation Updates During Implementation

Update these docs after code implementation:

- `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`
- backend and frontend implementation reports if this repository keeps them current
- release note for store-level billing renewal

## Rollout Notes

Existing tenants with current subscription items need a migration/backfill:

1. Copy the aggregate subscription billing cycle to each existing store item.
2. Copy aggregate current period start/end to existing active store items.
3. Keep existing amount and currency on each item.
4. Create no synthetic events unless audit policy requires it.
5. Recompute aggregate subscription after backfill to confirm unchanged platform behavior.

The migration should be additive and reversible enough for rollback:

- Add nullable columns first.
- Backfill current rows.
- Tighten application-level validation before adding hard database constraints that could fail historical data.

## Open Confirmation Point

This design intentionally keeps runtime App Gate enforcement tenant-scoped for the first implementation. It makes store-level billing, renewal, and payment reservation independent, but does not yet make one unpaid store lose runtime product access while sibling stores remain active.

If store-level runtime disabling is required now, the implementation scope must expand to App Gate store-scoped entitlement checks before coding starts.
