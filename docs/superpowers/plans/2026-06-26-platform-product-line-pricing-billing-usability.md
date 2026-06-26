# Platform Product Line Pricing And Billing Usability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add monthly/yearly product-line pricing and make tenant billing easier by using product-line selection plus duration-count based period calculation.

**Architecture:** Extend the existing `platformbilling` module with product-line price catalog objects and backend-owned billing period calculation. Product-line prices provide defaults and quote snapshots; subscription commands still synchronize calculated entitlement dates into App Gate, which remains the runtime access boundary.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL, Flyway, JUnit, Spring MockMvc, Vue 3, TypeScript, Vue Router, Vite.

---

## Scope

Implement Phase 1.1 only:

- Add `platform_product_line_prices`.
- Maintain monthly and yearly prices from `基础设置 > 产品线`.
- Show tenant billing as product-line rows with checkbox-style open/renew/reactivate actions.
- Remove visible date picking from the normal tenant billing flow.
- Accept `durationCount` for monthly/yearly purchase, renew, and legacy conversion.
- Calculate `current_period_start`, `current_period_end`, amount defaults, and App Gate `valid_until` on the backend.
- Preserve manual amount override for platform admins.
- Store quote details in `tenant_product_subscription_events.event_payload`.

Do not implement payment gateway, invoices, webhook handling, automatic renewal, coupons, discounts, tax, tenant self-service payment, or exact-date manual adjustment UI.

## Current Baseline

Existing committed baseline:

- `V008__platform_product_line_billing.sql` added tenant subscriptions and events.
- `V009__call_screen_media_carousel.sql` is already present.
- Next migration number is `V010`.
- Existing frontend files:
  - `src/pages/PlatformProductLinesPage.vue`
  - `src/pages/PlatformTenantBillingPage.vue`
  - `src/components/platform/PlatformTenantTable.vue`
  - `src/components/platform/PlatformAdminNav.vue`
  - `src/api/platformProductLineBillingApi.ts`
  - `src/types/platformProductLineBilling.ts`
- Existing backend package:
  - `src/main/java/com/rpb/reservation/platformbilling/**`

## File Structure

### Documentation

- Modify: `docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md`
- Modify: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Modify: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Modify: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`
- Modify: `docs/backend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`
- Modify: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`

### Database

- Create: `src/main/resources/db/migration/V010__platform_product_line_prices.sql`
- Test: `src/test/java/com/rpb/reservation/platformbilling/PlatformProductLinePriceMigrationTest.java`

### Backend Domain And Application

- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLinePrice.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLinePriceUpdate.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/BillingDuration.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/BillingPeriodCalculator.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/BillingPeriodCalculation.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/SubscriptionQuote.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/SubscriptionQuoteService.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLine.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLineService.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionCommand.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionService.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/PlatformProductLineServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/BillingPeriodCalculatorTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/SubscriptionQuoteServiceTest.java`

### Backend Persistence

- Create: `src/main/java/com/rpb/reservation/platformbilling/persistence/PlatformProductLinePriceRepository.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/persistence/JdbcPlatformProductLinePriceRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/persistence/JdbcPlatformProductLineRepository.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/persistence/JdbcProductSubscriptionEventRepository.java`
- Test through service and controller tests.

### Backend API

- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineController.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineResponses.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLinePriceRequests.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/ProductSubscriptionRequests.java`
- Modify: `src/main/java/com/rpb/reservation/platformbilling/api/ProductSubscriptionResponses.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformProductLineControllerTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionControllerTest.java`

### Frontend

- Modify: `src/types/platformProductLineBilling.ts`
- Modify: `src/api/platformProductLineBillingApi.ts`
- Modify: `src/pages/PlatformProductLinesPage.vue`
- Modify: `src/pages/PlatformTenantBillingPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiImplementationValidationTest.java`

## Task 1: Update Contracts

**Files:**
- Modify: `docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md`
- Modify: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Modify: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Modify: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`

- [ ] **Step 1: Document product-line prices**

Add `prices` to product-line response:

```json
{
  "billingCycle": "monthly",
  "amount": 128.00,
  "currency": "SGD",
  "status": "active",
  "version": 0
}
```

Add endpoint:

```http
PATCH /api/v1/platform/product-lines/{appKey}/prices
```

- [ ] **Step 2: Document duration-based subscription commands**

Replace normal UI request examples that use `currentPeriodStart` and `currentPeriodEnd` with:

```json
{
  "idempotencyKey": "renew-reservation-queue-20260626-001",
  "appKey": "reservation_queue",
  "billingCycle": "monthly",
  "durationCount": 3,
  "amount": 384.00,
  "currency": "SGD",
  "paymentNote": "Manual transfer"
}
```

Document that backend calculates start and end dates.

- [ ] **Step 3: Document migration**

Add `platform_product_line_prices` schema with:

```text
app_key
billing_cycle
amount
currency
status
version
created_at
updated_at
```

State that seed rows are created for `reservation_queue` monthly and yearly with `0.00 SGD`.

- [ ] **Step 4: Document frontend behavior**

Add:

```text
Product line page shows monthly/yearly price inputs.
Tenant billing page uses product-line rows and duration count.
Normal purchase/renew flow does not show date inputs.
Backend response decides final currentPeriodStart/currentPeriodEnd.
```

- [ ] **Step 5: Verify docs**

Run:

```powershell
rg -n "platform_product_line_prices|durationCount|月付价格|年付价格|产品线勾选|currentPeriodStart|currentPeriodEnd" docs/api docs/database docs/frontend
```

Expected: new pricing and duration terms appear; `currentPeriodStart/currentPeriodEnd` remain only as response fields or backward-compatibility notes, not normal UI inputs.

## Task 2: Add Price Migration

**Files:**
- Create: `src/main/resources/db/migration/V010__platform_product_line_prices.sql`
- Create: `src/test/java/com/rpb/reservation/platformbilling/PlatformProductLinePriceMigrationTest.java`

- [ ] **Step 1: Write failing migration test**

Create assertions:

```java
assertThat(tableExists("platform_product_line_prices")).isTrue();
assertThat(priceRowCount("reservation_queue")).isEqualTo(2);
assertThat(priceExists("reservation_queue", "monthly")).isTrue();
assertThat(priceExists("reservation_queue", "yearly")).isTrue();
assertThat(uniqueIndexExists("ux_platform_product_line_prices_scope")).isTrue();
```

- [ ] **Step 2: Run migration test and verify failure**

Run:

```powershell
mvn -q "-Dtest=PlatformProductLinePriceMigrationTest" test
```

Expected: FAIL because `V010__platform_product_line_prices.sql` does not exist.

- [ ] **Step 3: Create migration**

Create:

```sql
create table if not exists platform_product_line_prices (
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

insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
values
    ('reservation_queue', 'monthly', 0.00, 'SGD', 'active'),
    ('reservation_queue', 'yearly', 0.00, 'SGD', 'active')
on conflict (app_key, billing_cycle) do nothing;
```

- [ ] **Step 4: Run migration test**

Run:

```powershell
mvn -q "-Dtest=PlatformProductLinePriceMigrationTest" test
```

Expected: PASS.

## Task 3: Add Price Catalog Backend

**Files:**
- Create: `PlatformProductLinePrice.java`
- Create: `PlatformProductLinePriceUpdate.java`
- Create: `PlatformProductLinePriceRepository.java`
- Create: `JdbcPlatformProductLinePriceRepository.java`
- Modify: `PlatformProductLine.java`
- Modify: `PlatformProductLineService.java`
- Modify: `JdbcPlatformProductLineRepository.java`
- Test: `PlatformProductLineServiceTest.java`

- [ ] **Step 1: Write service tests**

Add tests:

```java
@Test
void listsProductLineWithMonthlyAndYearlyPrices()

@Test
void updatesMonthlyAndYearlyPricesWithOptimisticVersion()

@Test
void rejectsNegativePriceAndInvalidCurrency()
```

- [ ] **Step 2: Add price model**

Create:

```java
public record PlatformProductLinePrice(
    String appKey,
    String billingCycle,
    BigDecimal amount,
    String currency,
    String status,
    int version
) {
}
```

- [ ] **Step 3: Add repository methods**

Define:

```java
List<PlatformProductLinePrice> findByAppKeys(Collection<String> appKeys);
List<PlatformProductLinePrice> replacePrices(String appKey, List<PlatformProductLinePriceUpdate> prices);
Optional<PlatformProductLinePrice> findActivePrice(String appKey, String billingCycle);
```

- [ ] **Step 4: Attach prices to product-line list**

Extend `PlatformProductLine` with:

```java
List<PlatformProductLinePrice> prices
```

Product-line list must return prices grouped by `appKey`.

- [ ] **Step 5: Run service tests**

Run:

```powershell
mvn -q "-Dtest=PlatformProductLineServiceTest" test
```

Expected: PASS.

## Task 4: Add Duration And Quote Domain Rules

**Files:**
- Create: `BillingDuration.java`
- Create: `BillingPeriodCalculator.java`
- Create: `BillingPeriodCalculation.java`
- Create: `SubscriptionQuote.java`
- Create: `SubscriptionQuoteService.java`
- Modify: `ProductSubscriptionCommand.java`
- Modify: `ProductSubscriptionService.java`
- Test: `BillingPeriodCalculatorTest.java`
- Test: `SubscriptionQuoteServiceTest.java`
- Test: `ProductSubscriptionServiceTest.java`

- [ ] **Step 1: Write period calculation tests**

Add tests:

```java
@Test
void purchaseStartsNowAndAddsMonthlyDuration()

@Test
void renewalExtendsFromFutureCurrentEnd()

@Test
void expiredRenewalStartsNow()

@Test
void legacyConversionStartsNow()

@Test
void yearlyDurationAddsCalendarYears()
```

- [ ] **Step 2: Implement `BillingDuration`**

Rules:

```text
monthly: durationCount 1..120
yearly: durationCount 1..10
legacy_grant: not allowed in duration purchase/renew
manual: not part of normal duration flow
```

- [ ] **Step 3: Implement `BillingPeriodCalculator`**

Implement:

```java
BillingPeriodCalculation calculate(
    String operation,
    ProductSubscription current,
    String billingCycle,
    int durationCount,
    OffsetDateTime now
)
```

Use:

```java
start.plusMonths(durationCount)
start.plusYears(durationCount)
```

- [ ] **Step 4: Implement quote tests**

Add tests:

```java
@Test
void defaultsAmountFromMonthlyPriceTimesDuration()

@Test
void defaultsAmountFromYearlyPriceTimesDuration()

@Test
void manualAmountOverrideIsFinalAmountButKeepsDefaultSnapshot()

@Test
void missingActivePriceIsRejected()
```

- [ ] **Step 5: Store quote in event payload**

Update event append behavior so purchase, renew, and convert-from-legacy write:

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

- [ ] **Step 6: Run domain and service tests**

Run:

```powershell
mvn -q "-Dtest=BillingPeriodCalculatorTest,SubscriptionQuoteServiceTest,ProductSubscriptionServiceTest" test
```

Expected: PASS.

## Task 5: Update Backend APIs

**Files:**
- Modify: `PlatformProductLineController.java`
- Modify: `PlatformProductLineResponses.java`
- Create: `PlatformProductLinePriceRequests.java`
- Modify: `ProductSubscriptionRequests.java`
- Modify: `ProductSubscriptionResponses.java`
- Modify: `PlatformTenantProductSubscriptionController.java`
- Test: `PlatformProductLineControllerTest.java`
- Test: `PlatformTenantProductSubscriptionControllerTest.java`

- [ ] **Step 1: Add price update endpoint test**

Assert:

```java
mockMvc.perform(patch("/api/v1/platform/product-lines/reservation_queue/prices"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.productLine.prices[0].billingCycle").value("monthly"));
```

- [ ] **Step 2: Add duration request controller tests**

Assert:

```java
mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase", tenantId)
    .content("""
    {
      "idempotencyKey":"purchase-001",
      "appKey":"reservation_queue",
      "billingCycle":"monthly",
      "durationCount":3,
      "amount":384.00,
      "currency":"SGD",
      "paymentNote":"manual transfer"
    }
    """))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.quote.durationCount").value(3));
```

- [ ] **Step 3: Implement product-line price request records**

Create:

```java
public final class PlatformProductLinePriceRequests {
    public record UpdatePricesRequest(List<UpdatePriceItem> prices) {}
    public record UpdatePriceItem(String billingCycle, BigDecimal amount, String currency, String status, Integer version) {}
}
```

- [ ] **Step 4: Implement subscription duration request fields**

Add to `ProductSubscriptionMutationRequest`:

```java
Integer durationCount
```

Keep `currentPeriodStart/currentPeriodEnd` parsing only for backward-compatible service tests if needed. The normal frontend must use `durationCount`.

- [ ] **Step 5: Include quote response**

Extend mutation response with:

```java
ProductSubscriptionQuoteResponse quote
```

- [ ] **Step 6: Run controller tests**

Run:

```powershell
mvn -q "-Dtest=PlatformProductLineControllerTest,PlatformTenantProductSubscriptionControllerTest" test
```

Expected: PASS.

## Task 6: Update Frontend Types And API Client

**Files:**
- Modify: `src/types/platformProductLineBilling.ts`
- Modify: `src/api/platformProductLineBillingApi.ts`

- [ ] **Step 1: Add price types**

Add:

```ts
export interface PlatformProductLinePrice {
  billingCycle: 'monthly' | 'yearly'
  amount: number
  currency: string
  status: 'active' | 'disabled'
  version: number
}
```

Add `prices: PlatformProductLinePrice[]` to `PlatformProductLine`.

- [ ] **Step 2: Add duration request fields**

Change mutation request to include:

```ts
durationCount?: number
amount?: number
currency?: string
```

Do not require `currentPeriodStart` or `currentPeriodEnd` for normal calls.

- [ ] **Step 3: Add price update API function**

Add:

```ts
export async function updateProductLinePrices(
  appKey: string,
  request: PlatformProductLinePriceMutation,
  fetcher?: PlatformBillingFetcher
): Promise<PlatformProductLineResponse>
```

Endpoint:

```ts
`/api/v1/platform/product-lines/${encodeURIComponent(appKey)}/prices`
```

- [ ] **Step 4: Run build**

Run:

```powershell
npm run build
```

Expected: PASS.

## Task 7: Update Product Line Page Pricing UI

**Files:**
- Modify: `src/pages/PlatformProductLinesPage.vue`
- Test: `PlatformProductLineBillingUiImplementationValidationTest.java`

- [ ] **Step 1: Add UI validation assertions**

Assert the page contains:

```text
月付价格
年付价格
保存定价
updateProductLinePrices
```

- [ ] **Step 2: Add price form state**

Add state:

```ts
const priceForm = reactive({
  monthlyAmount: 0,
  yearlyAmount: 0,
  currency: 'SGD',
  monthlyStatus: 'active',
  yearlyStatus: 'active',
  monthlyVersion: 0,
  yearlyVersion: 0
})
```

- [ ] **Step 3: Load prices from selected product line**

When selecting a product line, map:

```ts
const monthly = productLine.prices.find(price => price.billingCycle === 'monthly')
const yearly = productLine.prices.find(price => price.billingCycle === 'yearly')
```

- [ ] **Step 4: Add price save action**

Call:

```ts
await updateProductLinePrices(form.appKey, {
  prices: [
    { billingCycle: 'monthly', amount: Number(priceForm.monthlyAmount) || 0, currency: priceForm.currency, status: priceForm.monthlyStatus, version: priceForm.monthlyVersion },
    { billingCycle: 'yearly', amount: Number(priceForm.yearlyAmount) || 0, currency: priceForm.currency, status: priceForm.yearlyStatus, version: priceForm.yearlyVersion }
  ]
})
```

- [ ] **Step 5: Run build and UI validation**

Run:

```powershell
npm run build
mvn -q "-Dtest=PlatformProductLineBillingUiImplementationValidationTest" test
```

Expected: PASS.

## Task 8: Update Tenant Billing Checklist And Duration UI

**Files:**
- Modify: `src/pages/PlatformTenantBillingPage.vue`
- Test: `PlatformProductLineBillingUiImplementationValidationTest.java`

- [ ] **Step 1: Add UI validation assertions**

Assert:

```text
购买数量
个月
年
标准单价
默认金额
本次金额
```

Assert the page does not contain:

```text
type="datetime-local"
```

- [ ] **Step 2: Replace date form fields**

Remove visible `currentPeriodStart` and `currentPeriodEnd` inputs from the normal purchase/renew form.

Add:

```ts
durationCount: 1
```

- [ ] **Step 3: Calculate preview amount**

Use selected product-line price:

```ts
const selectedUnitPrice = computed(() => {
  const productLine = productLines.value.find(item => item.appKey === form.appKey)
  const price = productLine?.prices.find(item => item.billingCycle === form.billingCycle)
  return price?.amount ?? 0
})

const defaultAmount = computed(() => selectedUnitPrice.value * Math.max(1, Number(form.durationCount) || 1))
```

- [ ] **Step 4: Send duration payload**

Build payload:

```ts
return {
  idempotencyKey: newIdempotencyKey(),
  appKey,
  billingCycle: form.billingCycle,
  durationCount: Math.max(1, Number(form.durationCount) || 1),
  amount: Number(form.amount) || defaultAmount.value,
  currency: selectedCurrency.value,
  paymentNote: form.paymentNote.trim() || null,
  version
}
```

- [ ] **Step 5: Render checklist states**

Rows should show:

```text
未开通
历史赠送 / 永久有效
生效中
已暂停
已取消
已到期
```

Actions:

```text
开通
续费
转付费
恢复并续费
暂停
取消
重新开通
```

- [ ] **Step 6: Run build and UI validation**

Run:

```powershell
npm run build
mvn -q "-Dtest=PlatformProductLineBillingUiImplementationValidationTest" test
```

Expected: PASS.

## Task 9: Run Focused Integration Verification

**Files:**
- No new production files.

- [ ] **Step 1: Run backend focused tests**

Run:

```powershell
mvn -q "-Dtest=*PlatformBilling*,*ProductLine*,*PlatformTenant*" test
```

Expected: PASS.

- [ ] **Step 2: Run App Gate focused tests**

Run:

```powershell
mvn -q "-Dtest=*AppGate*" test
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 4: Confirm scope**

Run:

```powershell
git diff --name-only
```

Expected changed areas:

```text
docs/api
docs/database
docs/frontend
src/main/resources/db/migration/V010__platform_product_line_prices.sql
src/main/java/com/rpb/reservation/platformbilling
src/test/java/com/rpb/reservation/platformbilling
src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiImplementationValidationTest.java
src/api/platformProductLineBillingApi.ts
src/types/platformProductLineBilling.ts
src/pages/PlatformProductLinesPage.vue
src/pages/PlatformTenantBillingPage.vue
```

Unexpected changed areas:

```text
reservation business workflow
queue business workflow
queuedisplay media/call screen behavior
cleaning state machine
walkin state machine
production configuration
```

## Task 10: Final Review And Commit

**Files:**
- All intended Phase 1.1 files.

- [ ] **Step 1: Run code review checklist**

Verify:

```text
platformbilling owns pricing and billing period calculation.
App Gate remains runtime authorization source.
Business modules do not depend on billing.
Product-line prices are platform-scoped, not tenant-scoped.
Tenant subscription commands are tenant-scoped.
Duration count replaces visible date inputs in normal UI.
Quote snapshot is recorded in event payload.
Idempotency replay does not extend a subscription twice.
```

- [ ] **Step 2: Inspect git status**

Run:

```powershell
git status --short
```

Expected: only intended files are modified.

- [ ] **Step 3: Commit after approval**

Use:

```powershell
git add docs/api docs/database docs/frontend src/main/resources/db/migration/V010__platform_product_line_prices.sql src/main/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiImplementationValidationTest.java src/api/platformProductLineBillingApi.ts src/types/platformProductLineBilling.ts src/pages/PlatformProductLinesPage.vue src/pages/PlatformTenantBillingPage.vue
git commit -m "feat: add product line pricing billing duration flow"
```

Do not push unless explicitly requested.

## Self-Review Checklist

- Spec coverage: this plan covers product-line monthly/yearly pricing, tenant product-line checklist billing, duration-count period calculation, price defaulting, manual amount override, event price snapshots, permissions, and App Gate synchronization.
- Placeholder scan: no task uses unspecified file paths, undefined endpoints, or vague "handle edge cases" instructions.
- Type consistency: `durationCount`, `PlatformProductLinePrice`, `SubscriptionQuote`, `currentPeriodStart`, and `currentPeriodEnd` are used consistently.
- Scope control: payment gateway, invoices, webhooks, automatic renewal, discounts, tax, and exact-date manual adjustment UI are excluded.
- Existing dirty worktree note: before implementation, separate or commit the current uncommitted platform billing UI completion changes so Phase 1.1 pricing work remains reviewable.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-26-platform-product-line-pricing-billing-usability.md`.

Recommended execution approach:

1. First finish or commit the existing uncommitted platform billing UI completion changes.
2. Then implement this plan as a separate Phase 1.1 slice.
3. Do not push until explicitly requested.

