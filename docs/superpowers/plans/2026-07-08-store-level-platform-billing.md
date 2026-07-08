# Store-Level Platform Billing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let platform admins renew a selected store billing item without renewing every sibling store under the same tenant.

**Architecture:** Keep `tenant_product_subscriptions` as the tenant/product aggregate. Extend `tenant_product_subscription_items` with item-level billing cycle and period fields, then add item-level backend renewal and Vue controls that target one selected store item.

**Tech Stack:** Spring Boot Java, JdbcTemplate, PostgreSQL/Flyway, Vue 3, TypeScript, existing platform billing API conventions.

## Global Constraints

- API paths must stay under `/api/v1`.
- Store billing commands must validate `tenantId`, `subscriptionId`, and `itemId` ownership before mutation.
- Single-store renewal must not delete, replace, or renew sibling store items.
- Existing tenant-level purchase/renew endpoints remain compatible.
- App Gate remains tenant-scoped in this slice.
- Tests must cover normal path, cross-tenant/item mismatch, stale version, and UI targeting.

---

### Task 1: Store Item Period Schema

**Files:**
- Create: `src/main/resources/db/migration/V037__store_level_subscription_item_periods.sql`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/PlatformBillingMigrationTest.java`

**Interfaces:**
- Produces item columns `billing_cycle`, `current_period_start`, `current_period_end`, `payment_note`.

- [ ] **Step 1: Write migration validation**

Add assertions that `tenant_product_subscription_items` has the new columns and indexes.

- [ ] **Step 2: Add migration**

Add nullable columns first, backfill from `tenant_product_subscriptions`, then add period/index constraints that tolerate historical nulls.

- [ ] **Step 3: Run migration test**

Run: `mvn -q "-Dtest=PlatformBillingMigrationTest" test`

### Task 2: Backend Item Renewal

**Files:**
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionItem.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/persistence/ProductSubscriptionItemRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/persistence/JdbcProductSubscriptionItemRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionService.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/ProductSubscriptionResponses.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionController.java`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionControllerTest.java`

**Interfaces:**
- Produces: `ProductSubscriptionService.renewItem(UUID tenantId, UUID subscriptionId, UUID itemId, ProductSubscriptionCommand command, PlatformBillingOperator operator)`.
- Produces: `POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew`.

- [ ] **Step 1: Add failing service tests**

Cover renewing one item only, stale item version, and item/subscription mismatch.

- [ ] **Step 2: Add repository methods**

Add `findByTenantSubscriptionAndId`, `updateItem`, and aggregate helper reads.

- [ ] **Step 3: Implement service renewal**

Quote one store, calculate item period from the item end date, update only that item, recompute aggregate subscription, sync App Gate.

- [ ] **Step 4: Add API endpoint and response fields**

Expose item billing cycle and period fields in list and mutation responses.

- [ ] **Step 5: Run backend tests**

Run: `mvn -q "-Dtest=ProductSubscriptionServiceTest,PlatformTenantProductSubscriptionControllerTest" test`

### Task 3: Tenant Billing Detail UI

**Files:**
- Modify: `src/api/platformProductLineBillingApi.ts`
- Modify: `src/types/platformProductLineBilling.ts`
- Modify: `src/pages/PlatformTenantBillingPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiImplementationValidationTest.java`

**Interfaces:**
- Consumes item renew API from Task 2.
- Produces selected-store renewal UI where amount preview uses one store.

- [ ] **Step 1: Add UI validation test**

Assert the Vue page calls `renewProductSubscriptionItem`, tracks `selectedStoreItemId`, and no longer bases store renewal amount on all active stores.

- [ ] **Step 2: Add TypeScript API client**

Add `renewProductSubscriptionItem(tenantId, subscriptionId, itemId, request)`.

- [ ] **Step 3: Update page state**

Let the admin select a store item under the selected product line. The form targets that item and shows one-store amount.

- [ ] **Step 4: Run frontend build/static tests**

Run: `mvn -q "-Dtest=PlatformProductLineBillingUiImplementationValidationTest" test`
Run: `npm run build`

### Task 4: Documentation, Review, And Release

**Files:**
- Modify: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Modify: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Modify: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`
- Create: `docs/release-notes/2026-07-08-store-level-platform-billing.md`

**Interfaces:**
- Produces release and rollback notes for deployment.

- [ ] **Step 1: Update docs**

Document the item-level renewal endpoint and item period fields.

- [ ] **Step 2: Run targeted verification**

Run backend tests and production build.

- [ ] **Step 3: Commit and push**

Commit implementation and push current branch.
