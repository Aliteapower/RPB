# Platform Product Line Billing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add platform product-line management and lightweight manual tenant billing that activates App Gate entitlements without disrupting existing legacy grants.

**Architecture:** Reuse `platform_apps` as the product-line catalog and add tenant-scoped subscription/current-state plus append-only event history. Billing commands synchronize `tenant_app_entitlements`, while existing App Gate guards remain the final runtime enforcement point for business APIs.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, PostgreSQL, Flyway, JUnit, Spring MockMvc, Vue 3, TypeScript, Pinia, Vue Router, Vite.

---

## Scope

Implement Phase 1 only:

- Product line catalog over `platform_apps`.
- `reservation_queue` displayed as `预约排队叫号产线`.
- Tenant subscription current-state table.
- Tenant subscription event table with idempotency key.
- Legacy grant backfill for existing enabled entitlements with `valid_until = null`.
- Manual purchase, renewal, suspension, cancellation, and legacy conversion.
- App Gate entitlement synchronization.
- Platform product-line and billing pages.
- Dedicated permissions `platform.product_line.manage` and `platform.billing.manage` with `platform.tenant.manage` compatibility.

Do not implement payment gateways, automatic renewal, invoices, webhooks, billing scheduler, tenant self-service checkout, or `platform_product_line_prices` in Phase 1.

## File Structure

### Documentation And Contracts

- Create: `docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md`
- Create: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Create: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Create: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`
- Create: `docs/backend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`
- Create: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`

### Database

- Create: `src/main/resources/db/migration/V009__platform_product_line_billing.sql`
- Test: `src/test/java/com/rpb/reservation/platformbilling/PlatformProductLineBillingMigrationTest.java`

### Backend Product Line Module

- Create package: `src/main/java/com/rpb/reservation/platformbilling`
- Create: `api/PlatformProductLineController.java`
- Create: `api/PlatformProductLineRequest.java`
- Create: `api/PlatformProductLineResponse.java`
- Create: `api/PlatformProductLineListResponse.java`
- Create: `api/PlatformBillingApiErrorCode.java`
- Create: `api/PlatformBillingApiErrorResponse.java`
- Create: `api/PlatformBillingApiException.java`
- Create: `application/PlatformProductLineService.java`
- Create: `application/PlatformProductLine.java`
- Create: `application/PlatformProductLineCommand.java`
- Create: `application/PlatformBillingOperator.java`
- Create: `application/PlatformBillingServiceErrorCode.java`
- Create: `application/PlatformBillingServiceException.java`
- Create: `domain/PlatformProductLineId.java`
- Create: `persistence/PlatformProductLineRepository.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/PlatformProductLineServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformProductLineControllerTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformProductLineLocalRuntimeSecurityTest.java`

### Backend Subscription Module

- Create: `api/PlatformTenantProductSubscriptionController.java`
- Create: `api/ProductSubscriptionRequests.java`
- Create: `api/ProductSubscriptionResponses.java`
- Create: `application/ProductSubscriptionService.java`
- Create: `application/ProductSubscriptionCommand.java`
- Create: `application/ProductSubscriptionSyncResult.java`
- Create: `domain/ProductSubscription.java`
- Create: `domain/ProductSubscriptionEvent.java`
- Create: `domain/BillingCycle.java`
- Create: `domain/SubscriptionStatus.java`
- Create: `domain/BillingPeriod.java`
- Create: `domain/BillingPeriodRule.java`
- Create: `domain/EntitlementSyncPolicy.java`
- Create: `domain/EntitlementSyncDecision.java`
- Create: `domain/SubscriptionIdempotencyKey.java`
- Create: `domain/SubscriptionTransitionRule.java`
- Create: `persistence/ProductSubscriptionRepository.java`
- Create: `persistence/ProductSubscriptionEventRepository.java`
- Create: `persistence/TenantProductEntitlementSyncRepository.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/ProductSubscriptionServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionControllerTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/integration/ProductSubscriptionAppGateIntegrationTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformTenantProductSubscriptionLocalRuntimeSecurityTest.java`

### App Gate And Auth Seeds

- Modify: `src/main/resources/db/migration/V009__platform_product_line_billing.sql`
- Modify only if needed: `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java`
- Modify only if local runtime needs explicit allowlist: `src/main/java/com/rpb/reservation/walkin/auth/LocalRuntimeSecurityConfiguration.java`
- Test: `src/test/java/com/rpb/reservation/auth/integration/AuthMigrationTest.java`
- Test: `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`

### Frontend

- Create: `src/types/platformProductLineBilling.ts`
- Create: `src/api/platformProductLineBillingApi.ts`
- Create: `src/pages/PlatformProductLinesPage.vue`
- Create: `src/pages/PlatformTenantBillingPage.vue`
- Modify: `src/components/platform/PlatformAdminNav.vue`
- Modify: `src/router/index.ts`
- Modify: `src/api/platformApi.ts` only if tenant list needs a billing route helper.
- Modify: `src/pages/PlatformTenantFormPage.vue` only if adding a link to tenant billing from tenant detail.
- Test: `src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiValidationTest.java`

## Task 1: Write API, Schema, And UI Contracts

**Files:**
- Create: `docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md`
- Create: `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md`
- Create: `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md`
- Create: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md`

- [ ] **Step 1: Write product-line API contract**

Create `docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md` with endpoints:

```http
GET /api/v1/platform/product-lines
PATCH /api/v1/platform/product-lines/{appKey}
```

Document permission:

```text
role = platform_admin
permission = platform.product_line.manage
compatibility permission = platform.tenant.manage
```

Document response:

```json
{
  "success": true,
  "productLines": [
    {
      "appKey": "reservation_queue",
      "displayName": "预约排队叫号产线",
      "status": "active",
      "defaultEntryRoute": "/stores/:storeId/staff",
      "description": "预约、排队、叫号、入座、清台完整产线",
      "sortOrder": 10,
      "createdAt": "2026-06-19T03:00:00Z",
      "updatedAt": "2026-06-26T03:00:00Z",
      "version": 0
    }
  ]
}
```

Document request:

```json
{
  "displayName": "预约排队叫号产线",
  "status": "active",
  "description": "预约、排队、叫号、入座、清台完整产线",
  "sortOrder": 10,
  "version": 0
}
```

- [ ] **Step 2: Write tenant subscription API contract**

Create `docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md` with endpoints:

```http
GET /api/v1/platform/tenants/{tenantId}/product-subscriptions
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/suspend
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/cancel
POST /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy
```

Document permission:

```text
role = platform_admin
permission = platform.billing.manage
compatibility permission = platform.tenant.manage
```

Document idempotency requirement:

```json
{
  "idempotencyKey": "renew-20260626-tenant-20000000-reservation-queue"
}
```

- [ ] **Step 3: Write schema design**

Create `docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md` and include:

```text
tenant_product_subscriptions
tenant_product_subscription_events
```

State that `platform_product_line_prices` is a Phase 2 table and not part of Phase 1 migration.

Document that the migration must not modify existing `tenant_app_entitlements.valid_until`.

- [ ] **Step 4: Write UI contract**

Create `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md` with routes:

```text
/platform/settings/product-lines
/platform/tenants/:tenantId/billing
```

Document UI states:

```text
loading
empty
legacy grant
active subscription
suspended subscription
cancelled subscription
expired effective status
saving
error
permission denied
```

- [ ] **Step 5: Add module and OOD contract**

Add a section to the schema or backend contract that defines:

```text
platformbilling owns product-line catalog and tenant commercial state.
appgate owns runtime access decisions.
platform tenant management owns tenant lifecycle only.
reservation, queue, walk-in, seating, cleaning, and queue display do not depend on platformbilling.
```

Add the core domain objects:

```text
PlatformProductLine
ProductSubscription
ProductSubscriptionEvent
BillingCycle
SubscriptionStatus
BillingPeriod
EntitlementSyncPolicy
SubscriptionTransitionRule
SubscriptionIdempotencyKey
```

The contract must state that controllers call services, services use domain rules, and repositories persist state.

- [ ] **Step 6: Verify contract content**

Run:

```powershell
rg -n "reservation_queue|预约排队叫号产线|tenant_product_subscriptions|tenant_product_subscription_events|platform.billing.manage|platform.product_line.manage|convert-from-legacy|EntitlementSyncPolicy|SubscriptionTransitionRule|platformbilling" docs/api docs/database docs/frontend
```

Expected: every term appears in the new contract documents.

- [ ] **Step 7: Commit contracts**

Run:

```powershell
git add docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md
git commit -m "docs: add platform product line billing contracts"
```

## Task 2: Add Billing Migration With Legacy Grant Backfill

**Files:**
- Create: `src/main/resources/db/migration/V009__platform_product_line_billing.sql`
- Create: `src/test/java/com/rpb/reservation/platformbilling/PlatformProductLineBillingMigrationTest.java`
- Modify: `src/test/java/com/rpb/reservation/auth/integration/AuthMigrationTest.java`

- [ ] **Step 1: Write migration test first**

Create `PlatformProductLineBillingMigrationTest` using the existing local PostgreSQL migration test pattern. The test must:

```java
@Test
void createsBillingTablesSeedsPermissionsUpdatesProductNameAndBackfillsLegacyGrantWithoutChangingEntitlement() {
    DATABASE.applyMigration("src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql");
    DATABASE.applyMigration("src/main/resources/db/migration/V002__app_gate_foundation.sql");
    DATABASE.applyMigration("src/main/resources/db/migration/V003__auth_minimal_login.sql");
    insertLegacyEntitlementTenant();

    DATABASE.applyMigration("src/main/resources/db/migration/V009__platform_product_line_billing.sql");

    assertThat(tableExists("tenant_product_subscriptions")).isTrue();
    assertThat(tableExists("tenant_product_subscription_events")).isTrue();
    assertThat(productLineName("reservation_queue")).isEqualTo("预约排队叫号产线");
    assertThat(permissionExists("platform.product_line.manage")).isTrue();
    assertThat(permissionExists("platform.billing.manage")).isTrue();
    assertThat(legacySubscriptionCount()).isEqualTo(1);
    assertThat(legacyEntitlementValidUntil()).isNull();
}
```

Use helper queries:

```sql
select app_name from platform_apps where app_key = 'reservation_queue';

select count(*) from tenant_product_subscriptions
where tenant_id = ?
  and app_key = 'reservation_queue'
  and billing_cycle = 'legacy_grant'
  and status = 'active'
  and current_period_end is null;

select valid_until from tenant_app_entitlements
where tenant_id = ?
  and app_key = 'reservation_queue';
```

- [ ] **Step 2: Run migration test and verify failure**

Run:

```powershell
mvn -Dtest=PlatformProductLineBillingMigrationTest test
```

Expected: FAIL because the migration and tables do not exist.

- [ ] **Step 3: Create migration table DDL**

Create `src/main/resources/db/migration/V009__platform_product_line_billing.sql` with:

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

create index ix_tenant_product_subscriptions_tenant
    on tenant_product_subscriptions (tenant_id, status, app_key);

create index ix_tenant_product_subscriptions_period_end
    on tenant_product_subscriptions (status, current_period_end);

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
        event_type in ('purchase', 'renew', 'suspend', 'cancel', 'convert_from_legacy', 'manual_adjust')
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

- [ ] **Step 4: Add product line display seed update**

Add:

```sql
update platform_apps
set app_name = '预约排队叫号产线',
    description = '预约、排队、叫号、入座、清台完整产线',
    updated_at = now()
where app_key = 'reservation_queue';
```

Do not change `app_key`.

- [ ] **Step 5: Add permission seed**

Add dedicated permission rows for existing platform admins:

```sql
with platform_accounts as (
    select account.id as account_id
    from auth_accounts account
    join auth_account_roles role on role.account_id = account.id
    where role.role_code = 'platform_admin'
      and account.deleted_at is null
      and role.deleted_at is null
),
seed_permissions(permission_code) as (
    values ('platform.product_line.manage'), ('platform.billing.manage')
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from platform_accounts account
cross join seed_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);
```

- [ ] **Step 6: Add legacy grant backfill**

Add:

```sql
insert into tenant_product_subscriptions (
    tenant_id,
    app_key,
    billing_cycle,
    status,
    current_period_start,
    current_period_end,
    amount,
    currency,
    payment_note,
    operator_user_id
)
select
    entitlement.tenant_id,
    entitlement.app_key,
    'legacy_grant',
    'active',
    coalesce(entitlement.valid_from, entitlement.enabled_at, entitlement.created_at),
    null,
    0,
    coalesce(store_currency.currency, 'SGD'),
    'Historical permanent grant migrated from App Gate entitlement.',
    entitlement.enabled_by
from tenant_app_entitlements entitlement
left join lateral (
    select store.currency
    from stores store
    where store.tenant_id = entitlement.tenant_id
      and store.deleted_at is null
    order by store.created_at asc
    limit 1
) store_currency on true
where entitlement.app_key = 'reservation_queue'
  and entitlement.status in ('enabled', 'trial')
  and entitlement.valid_until is null
on conflict (tenant_id, app_key) do nothing;
```

Do not run any `update tenant_app_entitlements set valid_until = ...`.

- [ ] **Step 7: Add legacy grant event backfill**

Add:

```sql
insert into tenant_product_subscription_events (
    subscription_id,
    tenant_id,
    app_key,
    event_type,
    idempotency_key,
    previous_status,
    new_status,
    previous_billing_cycle,
    new_billing_cycle,
    previous_period_start,
    previous_period_end,
    new_period_start,
    new_period_end,
    amount,
    currency,
    payment_note,
    operator_user_id
)
select
    subscription.id,
    subscription.tenant_id,
    subscription.app_key,
    'manual_adjust',
    'legacy-grant-backfill-' || subscription.tenant_id || '-' || subscription.app_key,
    null,
    subscription.status,
    null,
    subscription.billing_cycle,
    null,
    null,
    subscription.current_period_start,
    subscription.current_period_end,
    subscription.amount,
    subscription.currency,
    subscription.payment_note,
    subscription.operator_user_id
from tenant_product_subscriptions subscription
where subscription.billing_cycle = 'legacy_grant'
on conflict (tenant_id, app_key, event_type, idempotency_key) do nothing;
```

- [ ] **Step 8: Run migration test**

Run:

```powershell
mvn -Dtest=PlatformProductLineBillingMigrationTest,AuthMigrationTest test
```

Expected: PASS.

- [ ] **Step 9: Commit migration**

Run:

```powershell
git add src/main/resources/db/migration/V009__platform_product_line_billing.sql src/test/java/com/rpb/reservation/platformbilling/PlatformProductLineBillingMigrationTest.java src/test/java/com/rpb/reservation/auth/integration/AuthMigrationTest.java docs/database/PLATFORM_PRODUCT_LINE_BILLING_SCHEMA_DESIGN.md
git commit -m "feat: add platform product line billing schema"
```

## Task 3: Build Product Line Backend API

**Files:**
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineController.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineRequest.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineResponse.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformProductLineListResponse.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformBillingApiErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformBillingApiErrorResponse.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/api/PlatformBillingApiException.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLineService.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLine.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformProductLineCommand.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformBillingOperator.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformBillingServiceErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/application/PlatformBillingServiceException.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/domain/PlatformProductLineId.java`
- Create: `src/main/java/com/rpb/reservation/platformbilling/persistence/PlatformProductLineRepository.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/application/PlatformProductLineServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/platformbilling/api/PlatformProductLineControllerTest.java`

- [ ] **Step 1: Write product-line service tests**

Create tests:

```java
@Test
void listsReservationQueueAsProductLine()

@Test
void updateProductLineRejectsBlankDisplayName()

@Test
void updateProductLineRejectsInvalidStatus()

@Test
void updateProductLineUpdatesPlatformAppWithoutChangingAppKey()
```

The first test asserts:

```java
assertThat(result).extracting(PlatformProductLine::appKey).contains("reservation_queue");
assertThat(result).extracting(PlatformProductLine::displayName).contains("预约排队叫号产线");
```

- [ ] **Step 2: Run service tests and verify failure**

Run:

```powershell
mvn -Dtest=PlatformProductLineServiceTest test
```

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement product-line records**

Create:

```java
public record PlatformProductLine(
    UUID id,
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
}
```

Create command:

```java
public record PlatformProductLineCommand(
    String displayName,
    String status,
    String description,
    Integer sortOrder,
    Integer version
) {
}
```

- [ ] **Step 4: Implement repository**

Use `JdbcTemplate` and keep SQL scoped to `platform_apps`:

```java
public List<PlatformProductLine> list() {
    return jdbc.query("""
        select id, app_key, app_name, status, default_entry_route,
               description, sort_order, created_at, updated_at, 0 as version
        from platform_apps
        order by sort_order asc, app_key asc
        """, (rs, rowNum) -> productLine(rs));
}
```

Update:

```java
public Optional<PlatformProductLine> update(String appKey, String displayName, String status, String description, int sortOrder) {
    return jdbc.query("""
        update platform_apps
        set app_name = ?,
            status = ?,
            description = ?,
            sort_order = ?,
            updated_at = now()
        where app_key = ?
        returning id, app_key, app_name, status, default_entry_route,
                  description, sort_order, created_at, updated_at, 0 as version
        """, mapper, displayName, status, description, sortOrder, appKey)
        .stream()
        .findFirst();
}
```

- [ ] **Step 5: Implement service validation**

Validation rules:

```java
private static String normalizeStatus(String status) {
    if (!"active".equals(status) && !"disabled".equals(status)) {
        throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
    }
    return status;
}
```

Display name must be nonblank and at most 60 characters. Description may be null or at most 240 characters. Sort order must be positive.

- [ ] **Step 6: Write controller tests**

Create tests:

```java
@Test
void listProductLinesRequiresPlatformAdmin()

@Test
void listProductLinesAllowsProductLinePermission()

@Test
void listProductLinesAllowsTenantManageCompatibilityPermission()

@Test
void updateProductLineReturnsForbiddenWithoutPermission()
```

- [ ] **Step 7: Implement controller**

Create:

```java
@RestController
@RequestMapping("/api/v1/platform/product-lines")
public class PlatformProductLineController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PRODUCT_LINE_MANAGE = "platform.product_line.manage";
    private static final String TENANT_MANAGE = "platform.tenant.manage";

    @GetMapping
    public ResponseEntity<PlatformProductLineListResponse> listProductLines() {
        requirePlatformProductLineManager();
        return ResponseEntity.ok(PlatformProductLineListResponse.from(service.listProductLines()));
    }

    @PatchMapping("/{appKey}")
    public ResponseEntity<PlatformProductLineResponse> updateProductLine(
        @PathVariable String appKey,
        @RequestBody(required = false) PlatformProductLineRequest request
    ) {
        requirePlatformProductLineManager();
        return ResponseEntity.ok(PlatformProductLineResponse.from(service.updateProductLine(appKey, toCommand(request))));
    }
}
```

The permission helper returns 403 unless actor has `platform_admin` and either dedicated permission or `platform.tenant.manage`.

- [ ] **Step 8: Run product-line tests**

Run:

```powershell
mvn -Dtest=PlatformProductLineServiceTest,PlatformProductLineControllerTest test
```

Expected: PASS.

- [ ] **Step 9: Commit product-line backend**

Run:

```powershell
git add src/main/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/platformbilling docs/api/PLATFORM_PRODUCT_LINE_API_CONTRACT.md
git commit -m "feat: add platform product line API"
```

## Task 4: Build Subscription Service And App Gate Sync

**Files:**
- Create subscription backend files listed in File Structure.
- Test: `ProductSubscriptionServiceTest`

- [ ] **Step 1: Write domain rule tests**

Create `ProductSubscriptionDomainRuleTest` with:

```java
@Test
void monthlyAndYearlyRequireFinitePeriodEnd()

@Test
void legacyGrantRequiresActiveStatusOpenEndedPeriodAndZeroAmount()

@Test
void convertFromLegacyRequiresExistingLegacyGrant()

@Test
void purchaseMapsToEnabledEntitlementAndStoreSettingUpsert()

@Test
void suspendMapsToSuspendedEntitlementWithoutStoreSettingChange()

@Test
void cancelMapsToDisabledEntitlementWithoutStoreSettingChange()
```

Use `EntitlementSyncPolicy` directly:

```java
EntitlementSyncDecision decision = EntitlementSyncPolicy.forOperation("purchase", subscription);

assertThat(decision.entitlementStatus()).isEqualTo("enabled");
assertThat(decision.validUntil()).isEqualTo(subscription.currentPeriodEnd());
assertThat(decision.upsertMissingStoreSettings()).isTrue();
```

- [ ] **Step 2: Run domain rule tests and verify failure**

Run:

```powershell
mvn -Dtest=ProductSubscriptionDomainRuleTest test
```

Expected: FAIL because domain objects do not exist.

- [ ] **Step 3: Implement domain objects and rules**

Create `BillingCycle`:

```java
public enum BillingCycle {
    MONTHLY("monthly"),
    YEARLY("yearly"),
    LEGACY_GRANT("legacy_grant"),
    MANUAL("manual");

    private final String value;

    BillingCycle(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

Create `SubscriptionStatus`:

```java
public enum SubscriptionStatus {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

Create `BillingPeriod`:

```java
public record BillingPeriod(OffsetDateTime start, OffsetDateTime end) {
    public boolean finiteAndValid() {
        return start != null && end != null && end.isAfter(start);
    }

    public boolean openEnded() {
        return end == null;
    }
}
```

Create `EntitlementSyncDecision`:

```java
public record EntitlementSyncDecision(
    String entitlementStatus,
    OffsetDateTime validFrom,
    OffsetDateTime validUntil,
    boolean upsertMissingStoreSettings
) {
}
```

Create `EntitlementSyncPolicy`:

```java
public final class EntitlementSyncPolicy {
    public EntitlementSyncDecision decide(String operation, ProductSubscription subscription) {
        return switch (operation) {
            case "purchase", "renew", "convert_from_legacy", "manual_adjust" ->
                new EntitlementSyncDecision("enabled", subscription.currentPeriodStart(), subscription.currentPeriodEnd(), "purchase".equals(operation));
            case "suspend" ->
                new EntitlementSyncDecision("suspended", subscription.currentPeriodStart(), subscription.currentPeriodEnd(), false);
            case "cancel" ->
                new EntitlementSyncDecision("disabled", subscription.currentPeriodStart(), subscription.currentPeriodEnd(), false);
            default -> throw new IllegalArgumentException("unsupported_subscription_operation");
        };
    }
}
```

- [ ] **Step 4: Run domain rule tests**

Run:

```powershell
mvn -Dtest=ProductSubscriptionDomainRuleTest test
```

Expected: PASS.

- [ ] **Step 5: Write service tests for legacy grant**

Create:

```java
@Test
void listSubscriptionsShowsLegacyGrantWithoutExpiringEntitlement() {
    ProductSubscription subscription = service.listSubscriptions(TENANT_ID).subscriptions().getFirst();

    assertThat(subscription.billingCycle()).isEqualTo("legacy_grant");
    assertThat(subscription.status()).isEqualTo("active");
    assertThat(subscription.currentPeriodEnd()).isNull();
    assertThat(subscription.entitlementValidUntil()).isNull();
}
```

- [ ] **Step 6: Write purchase sync test**

Create:

```java
@Test
void purchaseCreatesSubscriptionEventAndEnablesEntitlement() {
    ProductSubscriptionResult result = service.purchase(TENANT_ID, purchaseCommand());

    assertThat(result.subscription().status()).isEqualTo("active");
    assertThat(result.subscription().billingCycle()).isEqualTo("monthly");
    assertThat(entitlementStatus(TENANT_ID, "reservation_queue")).isEqualTo("enabled");
    assertThat(entitlementValidUntil(TENANT_ID, "reservation_queue")).isEqualTo(END);
    assertThat(eventCount("purchase", "purchase-key-1")).isEqualTo(1);
}
```

- [ ] **Step 7: Write renewal idempotency test**

Create:

```java
@Test
void duplicateRenewWithSameIdempotencyKeyDoesNotExtendTwice() {
    ProductSubscriptionResult first = service.renew(TENANT_ID, SUBSCRIPTION_ID, renewCommand("renew-key-1"));
    ProductSubscriptionResult second = service.renew(TENANT_ID, SUBSCRIPTION_ID, renewCommand("renew-key-1"));

    assertThat(first.replayed()).isFalse();
    assertThat(second.replayed()).isTrue();
    assertThat(eventCount("renew", "renew-key-1")).isEqualTo(1);
    assertThat(entitlementValidUntil(TENANT_ID, "reservation_queue")).isEqualTo(first.subscription().currentPeriodEnd());
}
```

- [ ] **Step 8: Write suspend and cancel tests**

Create:

```java
@Test
void suspendUpdatesEntitlementToSuspended()

@Test
void cancelUpdatesEntitlementToDisabled()
```

Assertions:

```java
assertThat(entitlementStatus(TENANT_ID, "reservation_queue")).isEqualTo("suspended");
assertThat(entitlementStatus(TENANT_ID, "reservation_queue")).isEqualTo("disabled");
```

- [ ] **Step 9: Run service tests and verify failure**

Run:

```powershell
mvn -Dtest=ProductSubscriptionServiceTest test
```

Expected: FAIL because service and repositories do not exist.

- [ ] **Step 10: Implement subscription records**

Create:

```java
public record ProductSubscription(
    UUID id,
    UUID tenantId,
    String appKey,
    String productLineName,
    String billingCycle,
    String status,
    String effectiveStatus,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    String entitlementStatus,
    OffsetDateTime entitlementValidUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
}
```

Create command:

```java
public record ProductSubscriptionCommand(
    String idempotencyKey,
    String appKey,
    String billingCycle,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    Integer version
) {
}
```

- [ ] **Step 11: Implement repositories**

`ProductSubscriptionRepository` methods:

```java
List<ProductSubscription> listByTenantId(UUID tenantId);
Optional<ProductSubscription> findByTenantIdAndId(UUID tenantId, UUID subscriptionId);
Optional<ProductSubscription> findByTenantIdAndAppKey(UUID tenantId, String appKey);
ProductSubscription insert(...);
ProductSubscription update(...);
```

`ProductSubscriptionEventRepository` methods:

```java
Optional<ProductSubscriptionEvent> findByIdempotency(UUID tenantId, String appKey, String eventType, String idempotencyKey);
ProductSubscriptionEvent append(...);
```

`TenantProductEntitlementSyncRepository` methods:

```java
void upsertEnabled(UUID tenantId, String appKey, OffsetDateTime validFrom, OffsetDateTime validUntil, UUID operatorUserId);
void updateStatus(UUID tenantId, String appKey, String status, UUID operatorUserId);
void upsertMissingStoreSettings(UUID tenantId, String appKey, UUID operatorUserId);
```

- [ ] **Step 12: Implement purchase**

Service flow:

```java
public ProductSubscriptionMutationResult purchase(UUID tenantId, ProductSubscriptionCommand command, PlatformBillingOperator operator) {
    NormalizedSubscriptionInput input = normalizePurchase(command);
    if (events.findByIdempotency(tenantId, input.appKey(), "purchase", input.idempotencyKey()).isPresent()) {
        return replayedResult(tenantId, input.appKey(), "purchase", input.idempotencyKey());
    }
    ensureTenantExists(tenantId);
    ensureProductLineExists(input.appKey());
    ensureNoCurrentSubscription(tenantId, input.appKey());
    ProductSubscription saved = subscriptions.insert(...);
    events.append(...);
    entitlementSync.upsertEnabled(tenantId, input.appKey(), input.currentPeriodStart(), input.currentPeriodEnd(), operator.operatorId());
    entitlementSync.upsertMissingStoreSettings(tenantId, input.appKey(), operator.operatorId());
    audit.recordPurchase(...);
    return ProductSubscriptionMutationResult.created(saved);
}
```

- [ ] **Step 13: Implement renew, suspend, cancel, convert**

Required state transitions:

```text
renew: active/suspended/expired/cancelled -> active
suspend: active/expired -> suspended
cancel: active/suspended/expired -> cancelled
convert_from_legacy: active legacy_grant -> active monthly/yearly/manual
```

`convert_from_legacy` must reject rows whose `billing_cycle` is not `legacy_grant`.

- [ ] **Step 14: Run service tests**

Run:

```powershell
mvn -Dtest=ProductSubscriptionDomainRuleTest,ProductSubscriptionServiceTest test
```

Expected: PASS.

- [ ] **Step 15: Commit subscription service**

Run:

```powershell
git add src/main/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/platformbilling/application src/test/java/com/rpb/reservation/platformbilling/domain
git commit -m "feat: add tenant product subscription service"
```

## Task 5: Add Subscription Controller And Security Tests

**Files:**
- Create: `PlatformTenantProductSubscriptionController.java`
- Create: `ProductSubscriptionRequests.java`
- Create: `ProductSubscriptionResponses.java`
- Test: `PlatformTenantProductSubscriptionControllerTest.java`
- Test: `PlatformTenantProductSubscriptionLocalRuntimeSecurityTest.java`

- [ ] **Step 1: Write controller tests**

Create tests:

```java
@Test
void getSubscriptionsRequiresPlatformAdmin()

@Test
void getSubscriptionsAllowsBillingManagePermission()

@Test
void getSubscriptionsAllowsTenantManageCompatibilityPermission()

@Test
void purchaseReturnsForbiddenWithoutBillingPermission()

@Test
void purchaseMapsRequestInvalidTo400()

@Test
void renewMapsSubscriptionConflictTo409()
```

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```powershell
mvn -Dtest=PlatformTenantProductSubscriptionControllerTest test
```

Expected: FAIL because controller does not exist.

- [ ] **Step 3: Implement request records**

Create:

```java
public final class ProductSubscriptionRequests {
    public record PurchaseRequest(
        String idempotencyKey,
        String appKey,
        String billingCycle,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        BigDecimal amount,
        String currency,
        String paymentNote
    ) {
    }

    public record MutationRequest(
        String idempotencyKey,
        String billingCycle,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        BigDecimal amount,
        String currency,
        String paymentNote,
        Integer version
    ) {
    }

    public record StatusRequest(
        String idempotencyKey,
        String paymentNote,
        Integer version
    ) {
    }
}
```

- [ ] **Step 4: Implement response records**

Create:

```java
public final class ProductSubscriptionResponses {
    public record ListResponse(boolean success, UUID tenantId, List<ItemResponse> subscriptions) {
        public static ListResponse from(UUID tenantId, List<ProductSubscription> subscriptions) { ... }
    }

    public record MutationResponse(boolean success, boolean replayed, ItemResponse subscription) {
        public static MutationResponse from(ProductSubscriptionMutationResult result) { ... }
    }
}
```

- [ ] **Step 5: Implement controller**

Create:

```java
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/product-subscriptions")
public class PlatformTenantProductSubscriptionController {
    @GetMapping
    public ResponseEntity<ProductSubscriptionResponses.ListResponse> list(@PathVariable UUID tenantId) { ... }

    @PostMapping("/purchase")
    public ResponseEntity<ProductSubscriptionResponses.MutationResponse> purchase(...) { ... }

    @PostMapping("/{subscriptionId}/renew")
    public ResponseEntity<ProductSubscriptionResponses.MutationResponse> renew(...) { ... }

    @PostMapping("/{subscriptionId}/suspend")
    public ResponseEntity<ProductSubscriptionResponses.MutationResponse> suspend(...) { ... }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<ProductSubscriptionResponses.MutationResponse> cancel(...) { ... }

    @PostMapping("/{subscriptionId}/convert-from-legacy")
    public ResponseEntity<ProductSubscriptionResponses.MutationResponse> convertFromLegacy(...) { ... }
}
```

Permission helper:

```java
private CurrentActor requirePlatformBillingManager() {
    CurrentActor actor = currentActorProvider.currentActor()
        .orElseThrow(() -> new PlatformBillingApiException(PlatformBillingApiErrorCode.UNAUTHENTICATED));
    boolean allowed = actor.roles().contains("platform_admin")
        && (actor.hasPermission("platform.billing.manage") || actor.hasPermission("platform.tenant.manage"));
    if (!allowed) {
        throw new PlatformBillingApiException(PlatformBillingApiErrorCode.FORBIDDEN);
    }
    return actor;
}
```

- [ ] **Step 6: Run controller and local runtime security tests**

Run:

```powershell
mvn -Dtest=PlatformTenantProductSubscriptionControllerTest,PlatformTenantProductSubscriptionLocalRuntimeSecurityTest test
```

Expected: PASS.

- [ ] **Step 7: Commit subscription controller**

Run:

```powershell
git add src/main/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/platformbilling docs/api/PLATFORM_TENANT_PRODUCT_SUBSCRIPTION_API_CONTRACT.md
git commit -m "feat: add platform tenant subscription API"
```

## Task 6: Verify App Gate Runtime Integration

**Files:**
- Test: `ProductSubscriptionAppGateIntegrationTest.java`
- Modify only if needed: `AppGateServiceTest.java`

- [ ] **Step 1: Write App Gate integration tests**

Create tests:

```java
@Test
void legacyGrantTenantStillAllowedAfterBillingMigration()

@Test
void purchaseEnablesEntitlementAndAppGateAllowsBusinessEndpoint()

@Test
void expiredFiniteEntitlementIsRejectedByAppGate()

@Test
void suspendedSubscriptionMakesAppGateRejectBusinessEndpoint()

@Test
void cancelledSubscriptionMakesAppGateRejectBusinessEndpoint()
```

Assertions:

```java
assertThat(decision.allowed()).isTrue();
assertThat(decision.denyReason()).isEqualTo(AppGateDenyReason.TENANT_APP_EXPIRED);
assertThat(decision.denyReason()).isEqualTo(AppGateDenyReason.TENANT_APP_NOT_ENABLED);
```

- [ ] **Step 2: Run App Gate integration tests and verify failure**

Run:

```powershell
mvn -Dtest=ProductSubscriptionAppGateIntegrationTest test
```

Expected: FAIL until service synchronization is wired into the test fixture.

- [ ] **Step 3: Add fixture helpers**

Create helper methods in the test:

```java
private void seedTenantStoreAndActor()
private ProductSubscription purchaseMonthly()
private void forceEntitlementExpired()
private AppGateDecision evaluateReservationQueue()
```

Use `AppGateAccessRequest`:

```java
new AppGateAccessRequest(
    "reservation_queue",
    TENANT_ID,
    STORE_ID,
    "reservation.create",
    actor
)
```

- [ ] **Step 4: Run App Gate integration tests**

Run:

```powershell
mvn -Dtest=ProductSubscriptionAppGateIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Run wider App Gate regression**

Run:

```powershell
mvn -Dtest=AppGateServiceTest,AppGateGuardIntegrationTest,ProductSubscriptionAppGateIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit App Gate verification**

Run:

```powershell
git add src/test/java/com/rpb/reservation/platformbilling src/test/java/com/rpb/reservation/appgate
git commit -m "test: verify billing app gate synchronization"
```

## Task 7: Add Frontend API Client And Types

**Files:**
- Create: `src/types/platformProductLineBilling.ts`
- Create: `src/api/platformProductLineBillingApi.ts`

- [ ] **Step 1: Create TypeScript types**

Create:

```ts
export type ProductLineStatus = 'active' | 'disabled'
export type BillingCycle = 'monthly' | 'yearly' | 'legacy_grant' | 'manual'
export type ProductSubscriptionStatus = 'active' | 'suspended' | 'cancelled' | 'expired'
```

Create:

```ts
export interface PlatformProductLine {
  appKey: string
  displayName: string
  status: ProductLineStatus
  defaultEntryRoute: string
  description: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
  version: number
}
```

Create:

```ts
export interface ProductSubscription {
  id: string
  tenantId: string
  appKey: string
  productLineName: string
  billingCycle: BillingCycle
  status: ProductSubscriptionStatus
  effectiveStatus: ProductSubscriptionStatus
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  amount: string
  currency: string
  paymentNote: string | null
  entitlementStatus: string
  entitlementValidUntil: string | null
  createdAt: string
  updatedAt: string
  version: number
}
```

- [ ] **Step 2: Create product-line API client**

Create functions:

```ts
export async function listProductLines(fetcher?: PlatformBillingFetcher): Promise<ProductLineListResponse>

export async function updateProductLine(
  appKey: string,
  request: ProductLineMutation,
  fetcher?: PlatformBillingFetcher
): Promise<ProductLineResponse>
```

Endpoints:

```ts
'/api/v1/platform/product-lines'
`/api/v1/platform/product-lines/${encodeURIComponent(appKey)}`
```

- [ ] **Step 3: Create subscription API client**

Create functions:

```ts
export async function listTenantProductSubscriptions(tenantId: string, fetcher?: PlatformBillingFetcher)
export async function purchaseTenantProductSubscription(tenantId: string, request: PurchaseSubscriptionMutation, fetcher?: PlatformBillingFetcher)
export async function renewTenantProductSubscription(tenantId: string, subscriptionId: string, request: SubscriptionMutation, fetcher?: PlatformBillingFetcher)
export async function suspendTenantProductSubscription(tenantId: string, subscriptionId: string, request: SubscriptionStatusMutation, fetcher?: PlatformBillingFetcher)
export async function cancelTenantProductSubscription(tenantId: string, subscriptionId: string, request: SubscriptionStatusMutation, fetcher?: PlatformBillingFetcher)
export async function convertLegacyProductSubscription(tenantId: string, subscriptionId: string, request: SubscriptionMutation, fetcher?: PlatformBillingFetcher)
```

- [ ] **Step 4: Add response guards**

Follow existing API client style. Add guards:

```ts
function isProductLineListResponse(payload: unknown): payload is ProductLineListResponse
function isProductSubscriptionListResponse(payload: unknown): payload is ProductSubscriptionListResponse
function isPlatformBillingApiErrorResponse(payload: unknown): payload is PlatformBillingApiErrorResponse
```

- [ ] **Step 5: Run frontend build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit frontend API client**

Run:

```powershell
git add src/types/platformProductLineBilling.ts src/api/platformProductLineBillingApi.ts
git commit -m "feat: add product line billing frontend client"
```

## Task 8: Build Product Line Platform Page

**Files:**
- Create: `src/pages/PlatformProductLinesPage.vue`
- Modify: `src/components/platform/PlatformAdminNav.vue`
- Modify: `src/router/index.ts`

- [ ] **Step 1: Add route**

Modify router:

```ts
{
  path: '/platform/settings/product-lines',
  name: 'platform-product-lines',
  component: PlatformProductLinesPage,
  meta: { requiresPlatformAdmin: true }
}
```

- [ ] **Step 2: Add navigation links**

Replace disabled basis setting item in `PlatformAdminNav.vue` with links:

```vue
<RouterLink class="nav-item" to="/platform/settings/product-lines">产品线</RouterLink>
<RouterLink class="nav-item" to="/platform/tenants">租户计费</RouterLink>
```

If the visual design needs grouping, keep the existing compact nav style and avoid nested cards.

- [ ] **Step 3: Build page loading state**

In `PlatformProductLinesPage.vue`:

```ts
const productLines = ref<PlatformProductLine[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
```

Load:

```ts
async function loadProductLines(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await listProductLines()
    productLines.value = response.productLines
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 4: Render list and edit form**

Use a table with columns:

```text
产品线
App Key
状态
默认入口
排序
操作
```

For `reservation_queue`, display:

```text
预约排队叫号产线
```

- [ ] **Step 5: Add save behavior**

Save:

```ts
await updateProductLine(selected.appKey, {
  displayName: selected.displayName.trim(),
  status: selected.status,
  description: selected.description?.trim() || null,
  sortOrder: Number(selected.sortOrder),
  version: selected.version
})
```

When `status` is `disabled`, show:

```text
停用产品线会影响所有已购买该产品线的租户。
```

- [ ] **Step 6: Run build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 7: Commit product-line page**

Run:

```powershell
git add src/pages/PlatformProductLinesPage.vue src/components/platform/PlatformAdminNav.vue src/router/index.ts docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md
git commit -m "feat: add platform product line page"
```

## Task 9: Build Tenant Billing Platform Page

**Files:**
- Create: `src/pages/PlatformTenantBillingPage.vue`
- Modify: `src/router/index.ts`
- Modify: `src/pages/PlatformTenantsPage.vue` or `src/components/platform/PlatformTenantTable.vue`

- [ ] **Step 1: Add route**

Add:

```ts
{
  path: '/platform/tenants/:tenantId/billing',
  name: 'platform-tenant-billing',
  component: PlatformTenantBillingPage,
  meta: { requiresPlatformAdmin: true }
}
```

- [ ] **Step 2: Add tenant list action**

Add a `计费` action next to tenant edit/delete actions. The click route:

```ts
router.push({ name: 'platform-tenant-billing', params: { tenantId: tenant.id } })
```

- [ ] **Step 3: Build load state**

In `PlatformTenantBillingPage.vue`:

```ts
const subscriptions = ref<ProductSubscription[]>([])
const productLines = ref<PlatformProductLine[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
```

Load:

```ts
const [subscriptionResponse, productLineResponse] = await Promise.all([
  listTenantProductSubscriptions(tenantId.value),
  listProductLines()
])
```

- [ ] **Step 4: Render subscription rows**

Columns:

```text
产品线
计费周期
状态
有效期开始
有效期结束
金额
币种
授权状态
操作
```

Legacy grant label:

```ts
function billingCycleLabel(value: BillingCycle): string {
  if (value === 'legacy_grant') return '历史赠送 / 永久有效'
  if (value === 'monthly') return '月付'
  if (value === 'yearly') return '年付'
  return '手动'
}
```

- [ ] **Step 5: Add purchase dialog**

Fields:

```text
product line
billing cycle
period start
period end
amount
currency
payment note
```

Generate an idempotency key in the client:

```ts
function newIdempotencyKey(action: string, appKey: string): string {
  return `${action}-${appKey}-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
```

- [ ] **Step 6: Add renew dialog**

Reuse the purchase fields, but the product line is fixed to the selected subscription.

Call:

```ts
await renewTenantProductSubscription(tenantId.value, subscription.id, request)
```

- [ ] **Step 7: Add suspend, cancel, and convert actions**

Suspend:

```ts
await suspendTenantProductSubscription(tenantId.value, subscription.id, {
  idempotencyKey: newIdempotencyKey('suspend', subscription.appKey),
  paymentNote: actionNote.value,
  version: subscription.version
})
```

Cancel:

```ts
await cancelTenantProductSubscription(tenantId.value, subscription.id, {
  idempotencyKey: newIdempotencyKey('cancel', subscription.appKey),
  paymentNote: actionNote.value,
  version: subscription.version
})
```

Convert legacy:

```ts
await convertLegacyProductSubscription(tenantId.value, subscription.id, request)
```

- [ ] **Step 8: Show frontend states**

Required UI behavior:

```text
loading line while fetching
success banner after mutation
error banner on API failure
buttons disabled while saving
legacy conversion visible only for billingCycle = legacy_grant
renew hidden for cancelled subscriptions unless product allows reactivation by renewal
```

- [ ] **Step 9: Run build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 10: Commit billing page**

Run:

```powershell
git add src/pages/PlatformTenantBillingPage.vue src/router/index.ts src/pages/PlatformTenantsPage.vue src/components/platform docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_UI_CONTRACT.md
git commit -m "feat: add platform tenant billing page"
```

## Task 10: Add UI Validation And Reports

**Files:**
- Create: `src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiValidationTest.java`
- Create: `docs/backend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`
- Create: `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md`

- [ ] **Step 1: Write UI validation test**

Create a source-level validation test that asserts:

```java
assertThat(file("src/router/index.ts"))
    .contains("/platform/settings/product-lines")
    .contains("/platform/tenants/:tenantId/billing");

assertThat(file("src/components/platform/PlatformAdminNav.vue"))
    .contains("产品线")
    .contains("租户计费");

assertThat(file("src/pages/PlatformProductLinesPage.vue"))
    .contains("预约排队叫号产线")
    .contains("停用产品线会影响所有已购买该产品线的租户");

assertThat(file("src/pages/PlatformTenantBillingPage.vue"))
    .contains("历史赠送 / 永久有效")
    .contains("convertLegacyProductSubscription")
    .contains("newIdempotencyKey");
```

- [ ] **Step 2: Run UI validation test**

Run:

```powershell
mvn -Dtest=PlatformProductLineBillingUiValidationTest test
```

Expected: PASS.

- [ ] **Step 3: Write backend implementation report**

Create `docs/backend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md` with:

```text
Changed backend files
Migration name
Tables added
Permissions added
Legacy grant behavior
App Gate synchronization behavior
API endpoints
Test commands and outputs
Known limitations
Rollback notes
```

- [ ] **Step 4: Write frontend implementation report**

Create `docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md` with:

```text
Changed frontend files
Routes added
Pages added
Loading, error, saving states
Legacy grant display
Manual billing workflows
Test commands and outputs
Known limitations
```

- [ ] **Step 5: Run focused verification**

Run:

```powershell
mvn -Dtest=PlatformProductLineBillingMigrationTest,PlatformProductLineServiceTest,PlatformProductLineControllerTest,ProductSubscriptionServiceTest,PlatformTenantProductSubscriptionControllerTest,ProductSubscriptionAppGateIntegrationTest,PlatformProductLineBillingUiValidationTest test
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit validation and reports**

Run:

```powershell
git add src/test/java/com/rpb/reservation/appgate/ui/PlatformProductLineBillingUiValidationTest.java docs/backend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md docs/frontend/PLATFORM_PRODUCT_LINE_BILLING_IMPLEMENTATION_REPORT.md
git commit -m "docs: report platform product line billing implementation"
```

## Task 11: Full Regression Before Handoff

**Files:**
- No new files.

- [ ] **Step 1: Run backend regression**

Run:

```powershell
mvn test
```

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 3: Inspect git status**

Run:

```powershell
git status --short
```

Expected: only intended files are changed or the branch is clean after commits.

- [ ] **Step 4: Prepare release note**

Create a release note entry that states:

```text
Added platform product-line catalog and manual tenant subscription management.
Existing App Gate legacy grants with open-ended validity remain valid.
Billing operations synchronize tenant App Gate entitlements.
No payment gateway, invoices, webhooks, or automatic renewal are included.
```

- [ ] **Step 5: Final code review**

Use the RPB code-review skill and verify:

```text
Controllers do not directly access repositories.
Platform endpoints require platform_admin and the correct permissions.
Tenant subscriptions do not carry store_id.
Billing service is the only writer that synchronizes billing operations into tenant_app_entitlements.
Business modules do not call billing services.
Tests cover migration, legacy grant, purchase, renewal, expiration, suspend, cancel, permission denial, idempotency, and frontend states.
```

## Self-Review Checklist

- Spec coverage: every confirmed requirement maps to a task in this plan.
- Placeholder scan: this plan contains concrete file paths, commands, endpoint names, table names, and validation checks.
- Type consistency: `ProductSubscription`, `PlatformProductLine`, `billingCycle`, `currentPeriodStart`, `currentPeriodEnd`, and `idempotencyKey` are used consistently.
- TDD coverage: migration, service, controller, integration, App Gate, security, and frontend validation tests are required before implementation completion.
- Scope control: no payment gateway, automatic renewal, invoice, webhook, scheduler, or `platform_product_line_prices` implementation is included in Phase 1.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-26-platform-product-line-billing.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.

Do not begin implementation until the design and this plan are reviewed and approved.
