# PayNow Payment Product Line Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first RPB-native `payment` product-line foundation: App Gate visibility, PostgreSQL schema, PayNow profile, quick-pay intent/session creation, and basic API contracts.

**Architecture:** Implement a new `com.rpb.reservation.payment` module with `api`, `application`, `domain`, `persistence`, and `provider` packages. Keep platform commercial activation in `platformbilling`, runtime access in App Gate, and customer payment state in `payment`; Phase 1 supports `quick_pay` and `generic_merchant`, while POS and reservation adapters remain reserved.

**Tech Stack:** Java 21, Spring Boot 3.5.15, PostgreSQL/Flyway, Spring JDBC for focused persistence, existing App Gate interceptor, existing shared idempotency where practical, Vue 3/Vite API client types only if frontend work enters scope.

## Global Constraints

- Create an RPB-native payment product line. Use `D:\payment_runtime` as business-flow evidence for PayNow, quick payment, payment sessions, proof review, and verification.
- Do not run `D:\payment_runtime` as a sidecar service and do not call it from RPB at runtime.
- No Python, Flask, SQLAlchemy, SQLite, or Vue runtime migration from `D:\payment_runtime`.
- Restaurant customer payments remain separate from platform subscription billing.
- Store-scoped payment APIs use `@RequireAppGate(appKey = "payment", permission = "<payment permission>")`.
- All payment operational data includes `tenant_id`; Phase 1 store payment data includes `store_id`.
- Monetary values use PostgreSQL `numeric(12,2)` and Java `BigDecimal`, not floating point.
- Mutating commands require `idempotencyKey`.
- Controllers call application services, not repositories.
- Persistence entities do not leak into API responses.
- Before runtime or migration validation, use `target/local-postgres-current.txt` according to `AGENTS.md`.

---

## File Structure

Create and modify these files across the first implementation slice:

- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java` for payment permission constants and entry set.
- Modify: `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java` to resolve visible-app entry permissions for `payment`.
- Create: `src/main/resources/db/migration/V047__payment_product_line_foundation.sql` for product-line seed, prices, payment permissions, and payment tables.
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentConstants.java` for method, source, and status constants.
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentReference.java` for reference normalization.
- Create: `src/main/java/com/rpb/reservation/payment/provider/PayNowQrPayloadBuilder.java` for PayNow SGQR payload generation boundary.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentMethodProfile.java` and `PaymentMethodProfileCommand.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntent.java`, `PaymentSession.java`, and `PaymentIntentCreateCommand.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentMethodProfileService.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java`.
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentMethodProfileRepository.java` and JDBC implementation.
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentIntentRepository.java` and JDBC implementation.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProfileController.java`.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentController.java`.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentApiErrorCode.java`, `PaymentApiErrorResponse.java`, and `PaymentApiException.java`.
- Test: `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`.
- Test: `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`.
- Test: `src/test/java/com/rpb/reservation/payment/provider/PayNowQrPayloadBuilderTest.java`.
- Test: `src/test/java/com/rpb/reservation/payment/application/PaymentMethodProfileServiceTest.java`.
- Test: `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java`.
- Test: `src/test/java/com/rpb/reservation/payment/api/PaymentIntentControllerTest.java`.

## Task 1: Product-Line Seed And App Gate Entry

**Files:**
- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- Modify: `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java`
- Create: `src/main/resources/db/migration/V047__payment_product_line_foundation.sql`
- Modify: `src/test/java/com/rpb/reservation/appgate/application/AppGateServiceTest.java`
- Create: `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`

**Interfaces:**
- Consumes: `PlatformAppEntity.getConfigJson()`, `CurrentActor.hasPermission(String)`.
- Produces: `AppGateRequiredPermission.PAYMENT_ENTRY_PERMISSIONS`, visible app support for `app_key = "payment"`, and a Flyway seed for `payment`.

- [x] **Step 1: Write failing visible-app test**

Add this test to `AppGateServiceTest`:

```java
@Test
void visibleAppsRecognizesPaymentIntentCreateAsPaymentPermission() {
    String paymentAppKey = "payment";
    when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlementFor(paymentAppKey, "enabled", null)));
    when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSettingFor(paymentAppKey, true, true)));
    when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
        .thenReturn(List.of(platformAppFor(paymentAppKey, "PayNow 支付产线", "/stores/:storeId/payments", "active", 30)));

    List<AppGateAppEntry> apps = service.visibleApps(
        actor(Set.of(STORE_ID), Set.of("payment.intent.create")),
        STORE_ID
    );

    assertThat(apps).hasSize(1);
    assertThat(apps.get(0).appKey()).isEqualTo("payment");
    assertThat(apps.get(0).entryRoute()).isEqualTo("/stores/" + STORE_ID + "/payments");
    assertThat(apps.get(0).permissions()).containsExactly("payment.intent.create");
}
```

- [x] **Step 2: Run test and verify RED**

Run:

```powershell
.\mvnw -Dtest=AppGateServiceTest#visibleAppsRecognizesPaymentIntentCreateAsPaymentPermission test
```

Expected: FAIL because `AppGateService.entryPermissions` returns an empty set for non-`reservation_queue` apps.

- [x] **Step 3: Implement payment permission constants and entry resolution**

Add constants:

```java
public static final String PAYMENT_INTENT_VIEW = "payment.intent.view";
public static final String PAYMENT_INTENT_CREATE = "payment.intent.create";
public static final String PAYMENT_VERIFICATION_REVIEW = "payment.verification.review";

public static final Set<String> PAYMENT_ENTRY_PERMISSIONS = Set.of(
    PAYMENT_INTENT_VIEW,
    PAYMENT_INTENT_CREATE,
    PAYMENT_VERIFICATION_REVIEW
);
```

Update `AppGateService.entryPermissions`:

```java
if ("payment".equals(appKey)) {
    return AppGateRequiredPermission.PAYMENT_ENTRY_PERMISSIONS.stream()
        .filter(actor::hasPermission)
        .collect(Collectors.toUnmodifiableSet());
}
```

- [x] **Step 4: Run test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=AppGateServiceTest#visibleAppsRecognizesPaymentIntentCreateAsPaymentPermission test
```

Expected: PASS.

- [x] **Step 5: Write failing migration test**

Create `PaymentMigrationTest` based on `AppGateMigrationTest`. Apply migrations V001, V002, V008, V010, V036, V041, then V047. Assert:

```java
assertThat(countWhere("""
    select count(*) from platform_apps
    where app_key = 'payment'
      and app_name = 'PayNow 支付产线'
      and status = 'active'
      and default_entry_route = '/stores/:storeId/payments'
      and config_json -> 'entryPermissions' ? 'payment.intent.create'
    """)).isEqualTo(1);

assertThat(countWhere("""
    select count(*) from platform_product_line_prices
    where app_key = 'payment'
      and billing_cycle in ('monthly', 'yearly')
      and amount = 0.00
      and currency = 'SGD'
    """)).isEqualTo(2);
```

- [x] **Step 6: Run migration test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PaymentMigrationTest test
```

Expected: FAIL because `V047__payment_product_line_foundation.sql` does not exist.

- [x] **Step 7: Create V047 product-line seed**

Create migration with `platform_apps`, `platform_product_line_prices`, platform admin permission grants, and no payment operational tables yet:

```sql
insert into platform_apps (
    app_key, app_name, status, default_entry_route, description, sort_order, config_json
) values (
    'payment',
    'PayNow 支付产线',
    'active',
    '/stores/:storeId/payments',
    'PayNow QR collection, quick payment terminal, payment proof review, and reusable payment sessions.',
    30,
    '{"entryPermissions":["payment.intent.view","payment.intent.create","payment.verification.review"]}'::jsonb
) on conflict (app_key) do update
set app_name = excluded.app_name,
    status = excluded.status,
    default_entry_route = excluded.default_entry_route,
    description = excluded.description,
    sort_order = excluded.sort_order,
    config_json = excluded.config_json,
    updated_at = now();

insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
values
    ('payment', 'monthly', 0.00, 'SGD', 'active'),
    ('payment', 'yearly', 0.00, 'SGD', 'active')
on conflict (app_key, billing_cycle) do nothing;
```

- [x] **Step 8: Run migration test and App Gate test**

Run:

```powershell
.\mvnw -Dtest=AppGateServiceTest#visibleAppsRecognizesPaymentIntentCreateAsPaymentPermission,PaymentMigrationTest test
```

Expected: PASS.

- [x] **Step 9: Commit Task 1**

```powershell
git add src/main/java/com/rpb/reservation/appgate src/main/resources/db/migration/V047__payment_product_line_foundation.sql src/test/java/com/rpb/reservation/appgate src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java
git commit -m "feat: seed payment product line"
```

## Task 2: Payment Operational Schema

**Files:**
- Modify: `src/main/resources/db/migration/V047__payment_product_line_foundation.sql`
- Modify: `src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java`

**Interfaces:**
- Consumes: existing `tenants`, `stores`, `auth_accounts`.
- Produces: `payment_method_profiles`, `payment_intents`, `payment_sessions`, `payment_display_counters`, `payment_proofs`, `payment_ocr_results`, `payment_verifications`, `payment_events`.

- [x] **Step 1: Extend failing migration test for tables and constraints**

Add assertions:

```java
assertThat(tableExists("payment_method_profiles")).isTrue();
assertThat(tableExists("payment_intents")).isTrue();
assertThat(tableExists("payment_sessions")).isTrue();
assertThat(tableExists("payment_display_counters")).isTrue();
assertThat(tableExists("payment_events")).isTrue();
```

Add invalid amount check:

```java
assertThatThrownBy(() -> JDBC.update("""
    insert into payment_intents (
        tenant_id, store_id, intent_no, source_type, method, amount,
        currency, payment_reference, status
    ) values (?, ?, 'PIT-TEST', 'quick_pay', 'paynow', -1.00, 'SGD', 'QP-TEST', 'pending')
    """, TENANT_ID, STORE_ID)).hasMessageContaining("ck_payment_intents_amount");
```

- [x] **Step 2: Run migration test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PaymentMigrationTest test
```

Expected: FAIL because payment operational tables do not exist.

- [x] **Step 3: Add payment tables to V047**

Add DDL matching the design document, using `uuid primary key default gen_random_uuid()`, scoped FKs, enum check constraints, and indexes.

Minimum `payment_intents` DDL:

```sql
create table if not exists payment_intents (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid not null,
    intent_no text not null,
    source_type text not null,
    source_id uuid null,
    method text not null,
    amount numeric(12, 2) not null,
    currency text not null default 'SGD',
    payment_reference text not null,
    status text not null default 'pending',
    expires_at timestamptz null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint fk_payment_intents_store_scope foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint uq_payment_intents_tenant_intent_no unique (tenant_id, intent_no),
    constraint uq_payment_intents_tenant_reference unique (tenant_id, payment_reference),
    constraint ck_payment_intents_source check (source_type in ('quick_pay', 'generic_merchant', 'pos_order', 'reservation_deposit', 'platform_billing')),
    constraint ck_payment_intents_method check (method in ('paynow')),
    constraint ck_payment_intents_status check (status in ('pending', 'awaiting_verification', 'paid', 'expired', 'cancelled', 'failed')),
    constraint ck_payment_intents_amount check (amount > 0),
    constraint ck_payment_intents_currency check (currency = upper(currency) and length(currency) = 3)
);
```

- [x] **Step 4: Run migration test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=PaymentMigrationTest test
```

Expected: PASS.

- [x] **Step 5: Commit Task 2**

```powershell
git add src/main/resources/db/migration/V047__payment_product_line_foundation.sql src/test/java/com/rpb/reservation/payment/PaymentMigrationTest.java
git commit -m "feat: add payment schema foundation"
```

## Task 3: PayNow Profile Service And API

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentMethodProfile.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentMethodProfileCommand.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentMethodProfileService.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentMethodProfileRepository.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentMethodProfileRepository.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProfileController.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentApiErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentApiErrorResponse.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentApiException.java`
- Test: `src/test/java/com/rpb/reservation/payment/application/PaymentMethodProfileServiceTest.java`

**Interfaces:**
- Produces: `PaymentMethodProfileService.updateProfile(StoreScope scope, PaymentMethodProfileCommand command)`.
- Produces: `PaymentMethodProfileService.findEffectiveProfile(StoreScope scope)`.

- [x] **Step 1: Write failing service test**

```java
@Test
void rejectsActivePayNowUenProfileWithoutMerchantName() {
    PaymentMethodProfileService service = new PaymentMethodProfileService(repository);

    assertThatThrownBy(() -> service.updateProfile(scope, new PaymentMethodProfileCommand(
        "paynow", "active", "uen", null, "202012345A", "", "SGD", "{}", 0
    ))).isInstanceOf(PaymentServiceException.class)
      .hasMessageContaining("PAYMENT_PROFILE_INVALID");
}
```

- [x] **Step 2: Run test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PaymentMethodProfileServiceTest test
```

Expected: FAIL because service classes do not exist.

- [x] **Step 3: Implement minimal profile validation and repository port**

Validation rules:

```java
if (!"paynow".equals(method)) throw invalid();
if (!Set.of("active", "disabled").contains(status)) throw invalid();
if ("active".equals(status) && merchantName.isBlank()) throw invalid();
if ("uen".equals(paynowType) && paynowUen.isBlank()) throw invalid();
if ("mobile".equals(paynowType) && paynowMobile.isBlank()) throw invalid();
if (!"SGD".equals(currency)) throw invalid();
```

- [x] **Step 4: Run service test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=PaymentMethodProfileServiceTest test
```

Expected: PASS.

- [x] **Step 5: Add controller test for App Gate annotation**

Use a source validation test similar to existing UI implementation validation tests or a `@WebMvcTest` verifying:

```java
PaymentProfileController.class.getMethod("updateProfile", UUID.class, PaymentProfileRequest.class)
    .getAnnotation(RequireAppGate.class)
```

Expected app key `payment`, permission `payment.settings.manage`.

- [x] **Step 6: Implement controller**

Use:

```java
@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/payment/profile")
public class PaymentProfileController {
    @GetMapping
    @RequireAppGate(appKey = "payment", permission = "payment.settings.manage")
    public ResponseEntity<PaymentProfileResponse> getProfile(@PathVariable UUID storeId) { ... }

    @PatchMapping
    @RequireAppGate(appKey = "payment", permission = "payment.settings.manage")
    public ResponseEntity<PaymentProfileResponse> updateProfile(@PathVariable UUID storeId, @RequestBody PaymentProfileRequest request) { ... }
}
```

- [x] **Step 7: Commit Task 3**

```powershell
git add src/main/java/com/rpb/reservation/payment src/test/java/com/rpb/reservation/payment
git commit -m "feat: add PayNow payment profile service"
```

## Task 4: PayNow QR Payload Builder

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/provider/PayNowQrPayloadBuilder.java`
- Test: `src/test/java/com/rpb/reservation/payment/provider/PayNowQrPayloadBuilderTest.java`

**Interfaces:**
- Produces: `String build(PayNowQrPayloadRequest request)`.

- [x] **Step 1: Write failing payload test**

```java
@Test
void buildsFixedAmountPayNowPayloadWithReference() {
    PayNowQrPayloadBuilder builder = new PayNowQrPayloadBuilder();

    String payload = builder.build(new PayNowQrPayloadRequest(
        "uen", null, "202012345A", "RPB Demo Restaurant", new BigDecimal("18.80"), "QP-202608-0001-A1B2"
    ));

    assertThat(payload).contains("QP-202608-0001-A1B2");
    assertThat(payload).contains("18.80");
}
```

- [x] **Step 2: Run test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PayNowQrPayloadBuilderTest test
```

Expected: FAIL because builder does not exist.

- [x] **Step 3: Implement minimal SGQR-compatible payload boundary**

Implement a deterministic EMV-like payload builder sufficient for Phase 1 tests and future replacement behind the same interface. It must include proxy value, merchant name, amount formatted as `0.00`, and reference.

- [x] **Step 4: Run test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=PayNowQrPayloadBuilderTest test
```

Expected: PASS.

- [x] **Step 5: Commit Task 4**

```powershell
git add src/main/java/com/rpb/reservation/payment/provider src/test/java/com/rpb/reservation/payment/provider
git commit -m "feat: add PayNow QR payload builder"
```

## Task 5: Quick Pay Intent And Session Service

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntent.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentSession.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentCreateCommand.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentCreateResult.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentIntentRepository.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java`
- Test: `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java`

**Interfaces:**
- Consumes: `PaymentMethodProfileService.findEffectiveProfile(StoreScope scope)`.
- Consumes: `PayNowQrPayloadBuilder.build(PayNowQrPayloadRequest request)`.
- Produces: `PaymentIntentCreateResult createQuickPay(StoreScope scope, PaymentIntentCreateCommand command, CurrentActor actor)`.

- [x] **Step 1: Write failing intent creation test**

```java
@Test
void createsQuickPayIntentSessionReferenceAndQrPayload() {
    PaymentIntentCreateResult result = service.createQuickPay(scope, new PaymentIntentCreateCommand(
        "quick-pay-20260805-001", "quick_pay", null, "paynow",
        new BigDecimal("18.80"), "SGD", "COUNTER-1", "Alice", null, "{}"
    ), actor);

    assertThat(result.intent().sourceType()).isEqualTo("quick_pay");
    assertThat(result.intent().paymentReference()).startsWith("QP-");
    assertThat(result.session().displayNumber()).isEqualTo(1);
    assertThat(result.session().qrPayloadsJson()).contains(result.intent().paymentReference());
}
```

- [x] **Step 2: Run test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PaymentIntentServiceTest test
```

Expected: FAIL because service classes do not exist.

- [x] **Step 3: Implement minimal service**

Rules:

- Accept only `quick_pay` and `generic_merchant` in Phase 1.
- Accept only method `paynow`.
- Reject amount `<= 0`.
- Reject currency not `SGD`.
- Require active PayNow profile.
- Generate `intentNo` as `PIT-yyyyMM-0001` through repository sequence method.
- Generate `paymentReference` as `QP-yyyyMM-0001-XXXX`.
- Session TTL defaults to 120 seconds.
- Allocate display number through repository counter method.
- Store QR payloads JSON with one `paynow` payload.

- [x] **Step 4: Run service test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=PaymentIntentServiceTest test
```

Expected: PASS.

- [x] **Step 5: Commit Task 5**

```powershell
git add src/main/java/com/rpb/reservation/payment src/test/java/com/rpb/reservation/payment
git commit -m "feat: create quick pay intents"
```

## Task 6: Payment Intent API

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentController.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentRequests.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentResponses.java`
- Test: `src/test/java/com/rpb/reservation/payment/api/PaymentIntentControllerTest.java`

**Interfaces:**
- Consumes: `PaymentIntentService.createQuickPay(StoreScope, PaymentIntentCreateCommand, CurrentActor)`.
- Produces: `POST /api/v1/stores/{storeId}/payments/intents`.

- [ ] **Step 1: Write failing controller test**

```java
@Test
void createQuickPayIntentRequiresPaymentAppGatePermission() throws Exception {
    Method method = PaymentIntentController.class.getMethod("createIntent", UUID.class, PaymentIntentRequests.CreateIntentRequest.class);
    RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

    assertThat(gate.appKey()).isEqualTo("payment");
    assertThat(gate.permission()).isEqualTo("payment.intent.create");
}
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
.\mvnw -Dtest=PaymentIntentControllerTest test
```

Expected: FAIL because controller does not exist.

- [ ] **Step 3: Implement controller and DTOs**

Endpoint:

```java
@PostMapping("/api/v1/stores/{storeId}/payments/intents")
@RequireAppGate(appKey = "payment", permission = "payment.intent.create")
public ResponseEntity<PaymentIntentResponses.CreateIntentResponse> createIntent(
    @PathVariable UUID storeId,
    @RequestBody PaymentIntentRequests.CreateIntentRequest request
)
```

Response fields:

- `success`
- `replayed`
- `intent`
- `session`
- `nextDisplayNumber`

- [ ] **Step 4: Run controller test and verify GREEN**

Run:

```powershell
.\mvnw -Dtest=PaymentIntentControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

```powershell
git add src/main/java/com/rpb/reservation/payment/api src/test/java/com/rpb/reservation/payment/api
git commit -m "feat: expose payment intent API"
```

## Task 7: Review And Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-08-05-paynow-payment-product-line-foundation.md`
- Create: `docs/release-notes/2026-08-05-paynow-payment-product-line-foundation.md`

**Interfaces:**
- Consumes: all tasks above.
- Produces: verification evidence and release note.

- [ ] **Step 1: Run targeted backend tests**

```powershell
.\mvnw -Dtest=AppGateServiceTest,PaymentMigrationTest,PayNowQrPayloadBuilderTest,PaymentMethodProfileServiceTest,PaymentIntentServiceTest,PaymentIntentControllerTest test
```

Expected: PASS.

- [ ] **Step 2: Run full backend test suite if local runtime permits**

```powershell
.\mvnw test
```

Expected: PASS or document unrelated pre-existing failure with exact test names.

- [ ] **Step 3: Apply RPB review skills**

Review against:

- `docs/skills/api-review/SKILL.md`
- `docs/skills/database-review/SKILL.md`
- `docs/skills/tdd-review/SKILL.md`
- `docs/skills/code-review/SKILL.md`

Required result: no P0/P1 blockers before final response.

- [ ] **Step 4: Write release note**

Create `docs/release-notes/2026-08-05-paynow-payment-product-line-foundation.md` with:

```markdown
# PayNow Payment Product Line Foundation

## Summary

Adds the RPB-native `payment` product-line foundation for PayNow quick payment.

## Included

- Payment product-line seed and entry permissions.
- Payment schema foundation.
- PayNow profile service.
- Quick pay intent and session service.
- Payment intent API foundation.

## Not Included

- OCR.
- POS adapter.
- Reservation deposit adapter.
- Bank reconciliation.
- Sidecar runtime integration.

## Verification

- `.\mvnw -Dtest=... test`
```

- [ ] **Step 5: Commit review docs**

```powershell
git add docs/release-notes/2026-08-05-paynow-payment-product-line-foundation.md docs/superpowers/plans/2026-08-05-paynow-payment-product-line-foundation.md
git commit -m "docs: record payment product line foundation release"
```

## Self-Review

Spec coverage:

- Product-line boundary: Task 1.
- Module OOD: Tasks 3-6.
- API contract: Tasks 3 and 6.
- Data model: Task 2.
- Permissions: Task 1 and controller annotation checks.
- Phase plan: This plan implements Phase 0 and backend portion of Phase 1.
- Risks and tests: Task 7 review and targeted tests.

Known deferred scope:

- OCR and proof review workbench.
- POS and reservation source adapters.
- Tenant admin Vue pages beyond backend DTO contracts.
- Bank reconciliation.
- Platform billing self-payment.

Placeholder scan:

- The plan contains no unresolved placeholders or unspecified implementation slots.

Type consistency:

- `PaymentMethodProfileService`, `PaymentIntentService`, `PaymentIntentCreateCommand`, `PaymentIntentCreateResult`, and `PayNowQrPayloadBuilder` names are used consistently across tasks.
