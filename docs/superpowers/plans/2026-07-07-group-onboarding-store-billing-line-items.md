# Group Onboarding And Store Billing Line Items Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a group multi-store tenant onboarding mode and persist store-level billing line items under the existing tenant product subscription.

**Architecture:** Keep tenant subscriptions and App Gate entitlement at tenant scope. Add store-scoped commercial line items inside `platformbilling`, and add platform tenant onboarding modes so single-store tenants still bootstrap a default entity/store while group tenants bootstrap a default operating entity and start without stores.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/Flyway, JdbcTemplate, Vue 3, TypeScript, vue-i18n, JUnit/MockMvc.

## Global Constraints

- Do not introduce cross-tenant store authorization.
- Product entitlement remains tenant-scoped in this slice.
- Store billing line items are commercial detail only; reservation, queue, walk-in, seating, cleaning, and App Gate modules must not depend on `platformbilling`.
- Group tenant creation must not create a default store.
- Single-store creation must still create a usable tenant admin login and default store, now attached to a default operating entity.
- New frontend copy must be in `zh-CN` and `en-SG`.

---

### Task 1: Red Tests And Contracts

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/PlatformTenantApiIntegrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionControllerTest.java`
- Modify: `src/test/java/com/rpb/reservation/platformbilling/PlatformBillingMigrationTest.java`
- Modify: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Modify: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Modify: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`

- [ ] Write failing tests for `onboardingMode=group_multi_store`, default operating entity creation in single-store mode, store billing line persistence, API response items, and V036 migration.
- [ ] Run targeted tests and confirm the expected failures.

### Task 2: Backend Group Tenant Onboarding

**Files:**
- Modify: `src/main/java/com/rpb/reservation/platform/api/PlatformTenantMutationRequest.java`
- Modify: `src/main/java/com/rpb/reservation/platform/api/PlatformTenantController.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantMutationCommand.java`
- Modify: `src/main/java/com/rpb/reservation/platform/application/PlatformTenantService.java`
- Modify: `src/main/java/com/rpb/reservation/platform/persistence/PlatformTenantRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platform/persistence/PlatformTenantAdminAccountRepository.java`

- [ ] Add `onboardingMode` request/command field with supported values `single_store` and `group_multi_store`.
- [ ] Make single-store mode create a default operating entity and default store linked to it.
- [ ] Make group mode create only tenant and tenant-admin account with no default store or store access.
- [ ] Keep update behavior backward compatible.

### Task 3: Store Billing Line Items

**Files:**
- Create: `src/main/resources/db/migration/V036__tenant_subscription_store_billing_items.sql`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/BillableStore.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionItem.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionItemDraft.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/persistence/ProductSubscriptionItemRepository.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/persistence/JdbcProductSubscriptionItemRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscription.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/SubscriptionQuote.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/SubscriptionQuoteService.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionService.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/ProductSubscriptionResponses.java`

- [ ] Add the store billing item table with tenant/store FKs and active-store backfill.
- [ ] Extend quote calculation with `storeCount` and make duration billing default to per-store price times store count.
- [ ] Replace store billing items on purchase, renew, and legacy conversion.
- [ ] Update line item statuses on suspend and cancel.
- [ ] Include line items in list and mutation responses.

### Task 4: Frontend Onboarding And Billing UI

**Files:**
- Modify: `src/api/platformApi.ts`
- Modify: `src/components/platform/platformTenantUi.ts`
- Modify: `src/components/platform/PlatformTenantForm.vue`
- Modify: `src/pages/PlatformTenantFormPage.vue`
- Modify: `src/types/platformProductLineBilling.ts`
- Modify: `src/pages/PlatformTenantBillingPage.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiImplementationValidationTest.java`

- [ ] Add onboarding mode selector to tenant create form.
- [ ] Route group tenant creation to the edit page structure panel.
- [ ] Display store count, per-store amount, and store billing detail rows in tenant billing.
- [ ] Update i18n keys and static UI validation.

### Task 5: Verification And Release Note

**Files:**
- Add: `docs/release-notes/2026-07-07-group-onboarding-store-billing-line-items.md`

- [ ] Run targeted backend tests.
- [ ] Run frontend build.
- [ ] Run review skills: tdd-review, api-review, database-review, code-review, release-note.
