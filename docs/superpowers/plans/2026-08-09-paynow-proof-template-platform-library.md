# PayNow Proof Template Platform Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a platform-governed PayNow receipt template library where platform admins maintain shared bank receipt rules, tenants use active platform templates by default, and tenant-only templates can be submitted to platform review.

**Architecture:** Extend the existing `payment_proof_templates` feature instead of replacing it. Keep `payment_proof_templates` as the effective matching source, add a contribution review table, add platform admin APIs for shared templates and rule suggestions, add tenant contribution APIs, then update platform and tenant UIs.

**Tech Stack:** Spring Boot Java 21, PostgreSQL/Flyway, JdbcTemplate repositories, App Gate permissions, Vue 3 + TypeScript + Vite, existing Tesseract-backed `PaymentProofOcrAdapter`.

## Global Constraints

- Automatic Payment Proof Review confirmation remains gated by one unique RPB Ref and exact amount match.
- Active tenant custom templates match before active platform templates.
- Tenant admins may create local templates and submit them to platform review, but must not directly publish platform templates.
- Platform templates are edited only by `platform_admin` actors with `platform.payment_proof_template.manage`.
- Tenant APIs use existing permission `payment.proof_template.manage`.
- Receipt sample image bytes are not persisted in this slice; store OCR raw text and digest only.
- New database objects must preserve tenant isolation and support rollback.
- Do not broaden local runtime or unauthenticated access.
- Run focused Maven tests, PayNow UI acceptance validation, and `npm run build` before final handoff.

---

## File Structure

Backend application:

- Create `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContribution.java`: immutable contribution record.
- Create `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionCommand.java`: tenant submit command.
- Create `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionReviewCommand.java`: platform accept/reject command.
- Create `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestion.java`: generated rule response model.
- Modify `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateService.java`: add platform CRUD, contribution lifecycle, and rule suggestion methods.

Backend persistence:

- Modify `src/main/java/com/rpb/reservation/payment/persistence/PaymentProofTemplateRepository.java`: add platform template CRUD, contribution CRUD, and review queue methods.
- Modify `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofTemplateRepository.java`: implement those repository methods.

Backend API:

- Create `src/main/java/com/rpb/reservation/payment/api/PlatformPaymentProofTemplateController.java`: platform template CRUD, rule suggestion, contribution review.
- Create `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionController.java`: tenant contribution list/submit/rule suggestion.
- Create `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionRequest.java`.
- Modify `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateResponses.java`: add contribution and rule suggestion responses.
- Modify `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`: add platform proof template manage permission constant.

Database:

- Create `src/main/resources/db/migration/V055__paynow_platform_proof_template_contributions.sql`.

Frontend:

- Modify `src/types/payment.ts`: add platform template, contribution, and rule suggestion response types.
- Modify `src/api/paymentApi.ts`: add platform and tenant contribution API client methods.
- Create `src/pages/PlatformPaymentProofTemplatesPage.vue`: platform library and review queue UI.
- Modify `src/pages/TenantAdminPaymentProofTemplatesPage.vue`: platform-use-first grouping, rule suggestions, and submit-to-platform action.
- Modify `src/components/platform/PlatformAdminNav.vue`: add PayNow proof template library link.
- Modify `src/router/index.ts`: add `/platform/payment/proof-templates`.
- Modify `src/i18n/locales/zh-CN.ts`, `src/i18n/locales/en-SG.ts`, `src/i18n/locales/generated-zh-CN.ts`, `src/i18n/locales/generated-en-SG.ts`: add navigation and page text.

Tests:

- Create `src/test/java/com/rpb/reservation/payment/PaymentProofTemplatePlatformMigrationTest.java`.
- Create `src/test/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestionTest.java`.
- Create `src/test/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionServiceTest.java`.
- Create `src/test/java/com/rpb/reservation/payment/api/PlatformPaymentProofTemplateControllerTest.java`.
- Create `src/test/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionControllerTest.java`.
- Modify `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`.

Docs:

- Modify `docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md`.
- Add `docs/release-notes/2026-08-09-paynow-proof-template-platform-library.md`.

---

### Task 1: Database Migration And Permission Seed

**Files:**
- Create: `src/main/resources/db/migration/V055__paynow_platform_proof_template_contributions.sql`
- Create: `src/test/java/com/rpb/reservation/payment/PaymentProofTemplatePlatformMigrationTest.java`
- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`

**Interfaces:**
- Produces table `payment_proof_template_contributions`.
- Produces permission string `platform.payment_proof_template.manage`.
- Later tasks rely on contribution statuses: `submitted`, `accepted`, `rejected`, `withdrawn`.

- [ ] **Step 1: Write failing migration test**

Add `PaymentProofTemplatePlatformMigrationTest` with this behavior:

```java
package com.rpb.reservation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentProofTemplatePlatformMigrationTest extends AbstractPaymentMigrationTest {
    @Test
    void v055CreatesContributionReviewTableAndPlatformPermission() {
        assertThat(columnNames("payment_proof_template_contributions"))
            .contains(
                "id",
                "tenant_id",
                "store_id",
                "source_template_id",
                "platform_template_id",
                "bank_code",
                "bank_name",
                "locale",
                "template_name",
                "layout_json",
                "sample_file_digest",
                "sample_raw_text",
                "sample_ocr_reference",
                "sample_ocr_amount",
                "status",
                "review_note",
                "submitted_by",
                "reviewed_by",
                "reviewed_at",
                "version"
            );
        assertThat(checkConstraintDefinitions("payment_proof_template_contributions"))
            .anyMatch(definition -> definition.contains("submitted") && definition.contains("accepted") && definition.contains("rejected"));
        assertThat(permissionCodes()).contains("platform.payment_proof_template.manage");
        assertThat(indexNames("payment_proof_template_contributions"))
            .contains(
                "ix_payment_proof_template_contributions_review",
                "ix_payment_proof_template_contributions_tenant"
            );
    }
}
```

If `AbstractPaymentMigrationTest` lacks `indexNames`, `checkConstraintDefinitions`, or `permissionCodes`, add private helper methods inside this test using the existing test `JdbcTemplate`.

- [ ] **Step 2: Run migration test and verify it fails**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplatePlatformMigrationTest" test
```

Expected: fail because `payment_proof_template_contributions` does not exist.

- [ ] **Step 3: Add permission constant**

Modify `AppGateRequiredPermission`:

```java
public static final String PLATFORM_PAYMENT_PROOF_TEMPLATE_MANAGE = "platform.payment_proof_template.manage";
```

Do not add this permission to tenant or staff App Gate sets. Platform controllers will check `platform_admin` role directly, matching existing platform seed controllers.

- [ ] **Step 4: Create V055 migration**

Create `V055__paynow_platform_proof_template_contributions.sql`:

```sql
create table if not exists payment_proof_template_contributions (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    store_id uuid null references stores(id),
    source_template_id uuid null references payment_proof_templates(id),
    platform_template_id uuid null references payment_proof_templates(id),
    bank_code text not null,
    bank_name text not null,
    locale text not null default 'zh-CN',
    template_name text not null,
    layout_json jsonb not null default '{}'::jsonb,
    sample_file_name text null,
    sample_content_type text null,
    sample_file_digest text null,
    sample_raw_text text null,
    sample_ocr_reference text null,
    sample_ocr_amount numeric(12, 2) null,
    status text not null default 'submitted',
    review_note text null,
    submitted_by uuid null,
    reviewed_by uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    reviewed_at timestamptz null,
    version integer not null default 0,
    constraint ck_payment_proof_template_contributions_bank_code check (
        bank_code = lower(bank_code) and length(btrim(bank_code)) between 2 and 32
    ),
    constraint ck_payment_proof_template_contributions_status check (
        status in ('submitted', 'accepted', 'rejected', 'withdrawn')
    ),
    constraint ck_payment_proof_template_contributions_content_type check (
        sample_content_type is null or sample_content_type in ('image/png', 'image/jpeg', 'image/webp')
    ),
    constraint ck_payment_proof_template_contributions_amount check (
        sample_ocr_amount is null or sample_ocr_amount > 0
    )
);

create index if not exists ix_payment_proof_template_contributions_review
    on payment_proof_template_contributions (status, created_at desc);

create index if not exists ix_payment_proof_template_contributions_tenant
    on payment_proof_template_contributions (tenant_id, created_at desc);

create index if not exists ix_payment_proof_template_contributions_bank
    on payment_proof_template_contributions (bank_code, locale, status);

with platform_admin_accounts as (
    select account.id as account_id
    from auth_accounts account
    where account.actor_type = 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account_id, 'platform.payment_proof_template.manage'
from platform_admin_accounts
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = platform_admin_accounts.account_id
      and existing.permission_code = 'platform.payment_proof_template.manage'
      and existing.deleted_at is null
);
```

- [ ] **Step 5: Run migration test and verify it passes**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplatePlatformMigrationTest" test
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V055__paynow_platform_proof_template_contributions.sql src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java src/test/java/com/rpb/reservation/payment/PaymentProofTemplatePlatformMigrationTest.java
git commit -m "feat: add paynow proof template contribution migration"
```

---

### Task 2: Rule Suggestion Service

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestion.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateService.java`
- Create: `src/test/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestionTest.java`

**Interfaces:**
- Produces method:

```java
public PaymentProofTemplateRuleSuggestion suggestRule(
    StoreScope scope,
    String fileName,
    String contentType,
    byte[] fileBytes,
    String bankCode,
    String bankName,
    String locale,
    CurrentActor actor
)
```

- Produces platform method:

```java
public PaymentProofTemplateRuleSuggestion suggestPlatformRule(
    String fileName,
    String contentType,
    byte[] fileBytes,
    String bankCode,
    String bankName,
    String locale
)
```

- Later controllers serialize `suggestedLayoutJson`, `ocr`, `bankCode`, `bankName`, and `templateName`.

- [ ] **Step 1: Write failing rule suggestion tests**

Add tests that use a fake `PaymentProofOcrAdapter` returning stable OCR text:

```java
@Test
void suggestRuleBuildsJsonFromOcrTextWithoutSavingTemplate() {
    PaymentProofTemplateService service = new PaymentProofTemplateService(repository, adapterReturning(
        new PaymentProofOcrFields(
            "QP202608090016MERK",
            new BigDecimal("0.50"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.7300"),
            "OCBC\n您已支付 0.50 SGD\n讯息\nQP202608090016MERK\n转账日期\n9 Aug 2026",
            "{}"
        )
    ));

    PaymentProofTemplateRuleSuggestion suggestion = service.suggestRule(
        scope(),
        "ocbc.jpg",
        "image/jpeg",
        "receipt".getBytes(StandardCharsets.UTF_8),
        "ocbc",
        "OCBC",
        "zh-CN",
        tenantActor()
    );

    assertThat(suggestion.bankCode()).isEqualTo("ocbc");
    assertThat(suggestion.suggestedLayoutJson()).contains("matchKeywords");
    assertThat(suggestion.suggestedLayoutJson()).contains("referencePatterns");
    assertThat(suggestion.suggestedLayoutJson()).contains("amountPatterns");
    assertThat(suggestion.ocr().extractedReference()).isEqualTo("QP202608090016MERK");
    verify(repository, never()).createTenantTemplate(any(), any(), any());
}

@Test
void suggestRuleRejectsUnsupportedImageTypes() {
    assertThatThrownBy(() -> service.suggestRule(
        scope(),
        "receipt.gif",
        "image/gif",
        "receipt".getBytes(StandardCharsets.UTF_8),
        "ocbc",
        "OCBC",
        "zh-CN",
        tenantActor()
    )).isInstanceOf(PaymentServiceException.class);
}
```

Use Mockito for repository verification if the existing test style already uses it; otherwise write a small in-memory fake repository that records calls.

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplateRuleSuggestionTest" test
```

Expected: fail because `PaymentProofTemplateRuleSuggestion` and `suggestRule` do not exist.

- [ ] **Step 3: Add `PaymentProofTemplateRuleSuggestion` record**

```java
package com.rpb.reservation.payment.application;

public record PaymentProofTemplateRuleSuggestion(
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String suggestedLayoutJson,
    PaymentProofOcrFields ocr
) {
}
```

- [ ] **Step 4: Implement deterministic JSON suggestion**

Add private helper methods in `PaymentProofTemplateService`:

```java
private static String suggestedLayoutJson(String rawText, String bankCode, PaymentProofOcrFields fields) {
    ObjectNode root = OBJECT_MAPPER.createObjectNode();
    ArrayNode matchKeywords = root.putArray("matchKeywords");
    stableKeyword(rawText, bankCode).forEach(matchKeywords::add);
    ArrayNode successKeywords = root.putArray("successKeywords");
    stableSuccessKeywords(rawText).forEach(successKeywords::add);
    ArrayNode referencePatterns = root.putArray("referencePatterns");
    referencePatterns.add("(?:讯息|信息|Message|Comment|Ref|Reference)\\\\s*[:：]?\\\\s*\\\"?([A-Z0-9.-]{10,32})\\\"?");
    ArrayNode amountPatterns = root.putArray("amountPatterns");
    amountPatterns.add("(?:您已支付|You(?:'ve)? sent|Payment successful|paid|sent)\\\\s*(?:S\\\\$|SGD|\\\\$)?\\\\s*([0-9OoIl,.]+)");
    amountPatterns.add("(?:SGD|S\\\\$|\\\\$)\\\\s*([0-9OoIl,.]+)");
    root.putArray("referenceRoi").add(0.0).add(0.30).add(1.0).add(0.66);
    root.putArray("amountRoi").add(0.0).add(0.15).add(1.0).add(0.42);
    return OBJECT_MAPPER.writeValueAsString(root);
}
```

The implementation should use existing `extract(...)`, `allowedContentType(...)`, `validateActor(...)`, `trimLower(...)`, and `trim(...)` helpers where possible.

- [ ] **Step 5: Run rule suggestion tests and verify pass**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplateRuleSuggestionTest" test
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestion.java src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateService.java src/test/java/com/rpb/reservation/payment/application/PaymentProofTemplateRuleSuggestionTest.java
git commit -m "feat: suggest paynow proof template rules"
```

---

### Task 3: Backend Platform Template And Contribution APIs

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContribution.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionCommand.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionReviewCommand.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentProofTemplateService.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/PaymentProofTemplateRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofTemplateRepository.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PlatformPaymentProofTemplateController.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionController.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionRequest.java`
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentProofTemplateResponses.java`
- Create: `src/test/java/com/rpb/reservation/payment/application/PaymentProofTemplateContributionServiceTest.java`
- Create: `src/test/java/com/rpb/reservation/payment/api/PlatformPaymentProofTemplateControllerTest.java`
- Create: `src/test/java/com/rpb/reservation/payment/api/PaymentProofTemplateContributionControllerTest.java`

**Interfaces:**
- Repository methods:

```java
List<PaymentProofTemplate> findPlatformTemplates();
PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, UUID actorId);
PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command);
PaymentProofTemplateContribution createContribution(StoreScope scope, PaymentProofTemplateContributionCommand command, UUID actorId);
List<PaymentProofTemplateContribution> findTenantContributions(StoreScope scope);
List<PaymentProofTemplateContribution> findPlatformContributions(String status);
PaymentProofTemplateContribution acceptContribution(UUID contributionId, UUID platformTemplateId, UUID actorId, String reviewNote, int version);
PaymentProofTemplateContribution rejectContribution(UUID contributionId, UUID actorId, String reviewNote, int version);
```

- API paths:
  - `/api/v1/platform/payment/proof-templates`
  - `/api/v1/platform/payment/proof-templates/rule-suggestions`
  - `/api/v1/platform/payment/proof-template-contributions`
  - `/api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
  - `/api/v1/stores/{storeId}/tenant-admin/payment/proof-template-rule-suggestions`

- [ ] **Step 1: Write failing service tests for contribution lifecycle**

Add tests:

```java
@Test
void tenantContributionCanBeAcceptedIntoPlatformTemplate() {
    PaymentProofTemplateContribution submitted = service.submitContribution(
        scope(),
        new PaymentProofTemplateContributionCommand(
            tenantTemplateId,
            "ocbc",
            "OCBC",
            "zh-CN",
            "OCBC new receipt",
            validLayoutJson(),
            "receipt.jpg",
            "image/jpeg",
            "digest",
            "QP202608090016MERK",
            new BigDecimal("0.50"),
            "OCBC raw text"
        ),
        tenantActor()
    );

    PaymentProofTemplateContribution accepted = service.acceptContribution(
        submitted.id(),
        new PaymentProofTemplateContributionReviewCommand(null, "accepted", submitted.version()),
        platformActor()
    );

    assertThat(accepted.status()).isEqualTo("accepted");
    assertThat(accepted.platformTemplateId()).isNotNull();
}

@Test
void tenantActorCannotAcceptContribution() {
    assertThatThrownBy(() -> service.acceptContribution(
        contributionId,
        new PaymentProofTemplateContributionReviewCommand(null, "bad actor", 0),
        tenantActor()
    )).isInstanceOf(PaymentServiceException.class);
}
```

Use an in-memory fake repository for service tests so behavior is clear without database setup.

- [ ] **Step 2: Write failing API permission tests**

For platform controller:

```java
@Test
void platformTemplateListRequiresPlatformAdminPermission() throws Exception {
    currentActorProvider.setCurrentActor(tenantActorWith("payment.proof_template.manage"));

    mockMvc.perform(get("/api/v1/platform/payment/proof-templates"))
        .andExpect(status().isForbidden());
}

@Test
void platformTemplateListAllowsPlatformAdminManager() throws Exception {
    currentActorProvider.setCurrentActor(platformActorWith("platform.payment_proof_template.manage"));

    mockMvc.perform(get("/api/v1/platform/payment/proof-templates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
}
```

For tenant contribution controller:

```java
@Test
void tenantContributionSubmitRequiresTenantStoreScope() throws Exception {
    currentActorProvider.setCurrentActor(tenantActorWithStore(otherStoreId, "payment.proof_template.manage"));

    mockMvc.perform(post("/api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions", storeId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(validContributionJson()))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplateContributionServiceTest,PlatformPaymentProofTemplateControllerTest,PaymentProofTemplateContributionControllerTest" test
```

Expected: fail because classes and endpoints do not exist.

- [ ] **Step 4: Add contribution records and request DTO**

Create records with exact names from the interface block. Use `UUID`, `BigDecimal`, and `OffsetDateTime` fields matching the V055 table. `PaymentProofTemplateContributionReviewCommand` should be:

```java
public record PaymentProofTemplateContributionReviewCommand(
    UUID platformTemplateId,
    String reviewNote,
    int version
) {
}
```

- [ ] **Step 5: Implement repository methods**

In `JdbcPaymentProofTemplateRepository`:

- `findPlatformTemplates` selects `tenant_id is null`.
- `createPlatformTemplate` inserts into `payment_proof_templates` with `tenant_id null`, `source 'platform_seed'`, and supplied status.
- `updatePlatformTemplate` updates only `tenant_id is null` rows with optimistic version.
- `createContribution` inserts tenant/store scoped contribution rows.
- `findTenantContributions` filters by `tenant_id = ?`.
- `findPlatformContributions` filters by status if provided.
- `acceptContribution` updates contribution status to `accepted`, sets `platform_template_id`, `reviewed_by`, `reviewed_at`, `review_note`, and bumps version.
- `rejectContribution` updates status to `rejected` and review fields.

- [ ] **Step 6: Implement service methods**

Add methods:

```java
@Transactional(readOnly = true)
public List<PaymentProofTemplate> listPlatformTemplates(CurrentActor actor)

@Transactional
public PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, CurrentActor actor)

@Transactional
public PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command, CurrentActor actor)

@Transactional(readOnly = true)
public List<PaymentProofTemplateContribution> listTenantContributions(StoreScope scope, CurrentActor actor)

@Transactional
public PaymentProofTemplateContribution submitContribution(StoreScope scope, PaymentProofTemplateContributionCommand command, CurrentActor actor)

@Transactional(readOnly = true)
public List<PaymentProofTemplateContribution> listPlatformContributions(String status, CurrentActor actor)

@Transactional
public PaymentProofTemplateContribution acceptContribution(UUID contributionId, PaymentProofTemplateContributionReviewCommand command, CurrentActor actor)

@Transactional
public PaymentProofTemplateContribution rejectContribution(UUID contributionId, PaymentProofTemplateContributionReviewCommand command, CurrentActor actor)
```

Add private guard:

```java
private static void validatePlatformActor(CurrentActor actor) {
    if (actor == null
        || !actor.roles().contains("platform_admin")
        || !actor.hasPermission("platform.payment_proof_template.manage")) {
        throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
    }
}
```

Controllers should map service `REQUEST_INVALID` caused by platform guard to 403 before returning; if cleaner, use controller-level platform guard and leave service guard as defense.

- [ ] **Step 7: Implement controllers and responses**

`PlatformPaymentProofTemplateController` uses existing `PaymentApiErrorResponse` and `PaymentApiException` mapping pattern.

Endpoint mapping:

```java
@RestController
@RequestMapping("/api/v1/platform/payment")
public class PlatformPaymentProofTemplateController {
    @GetMapping("/proof-templates")
    public ResponseEntity<PaymentProofTemplateResponses.ListResponse> listTemplates()

    @PostMapping("/proof-templates")
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> createTemplate(...)

    @PatchMapping("/proof-templates/{templateId}")
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> updateTemplate(...)

    @PostMapping(value = "/proof-templates/rule-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentProofTemplateResponses.RuleSuggestionResponse> suggestRule(...)

    @GetMapping("/proof-template-contributions")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionListResponse> listContributions(...)

    @PostMapping("/proof-template-contributions/{contributionId}/accept")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionResponse> acceptContribution(...)

    @PostMapping("/proof-template-contributions/{contributionId}/reject")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionResponse> rejectContribution(...)
}
```

Tenant contribution controller:

```java
@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/payment")
public class PaymentProofTemplateContributionController {
    @GetMapping("/proof-template-contributions")
    @RequireAppGate(appKey = "payment", permission = "payment.proof_template.manage")
    public ResponseEntity<ContributionListResponse> list(...)

    @PostMapping("/proof-template-contributions")
    @RequireAppGate(appKey = "payment", permission = "payment.proof_template.manage")
    public ResponseEntity<ContributionResponse> submit(...)

    @PostMapping(value = "/proof-template-rule-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAppGate(appKey = "payment", permission = "payment.proof_template.manage")
    public ResponseEntity<RuleSuggestionResponse> suggest(...)
}
```

- [ ] **Step 8: Run backend API/service tests and verify pass**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplateContributionServiceTest,PlatformPaymentProofTemplateControllerTest,PaymentProofTemplateContributionControllerTest" test
```

Expected: pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/rpb/reservation/payment/application src/main/java/com/rpb/reservation/payment/persistence src/main/java/com/rpb/reservation/payment/api src/test/java/com/rpb/reservation/payment/application src/test/java/com/rpb/reservation/payment/api
git commit -m "feat: add paynow proof template platform APIs"
```

---

### Task 4: Frontend API Types And Source Validation

**Files:**
- Modify: `src/types/payment.ts`
- Modify: `src/api/paymentApi.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

**Interfaces:**
- Produces TypeScript functions:
  - `getPlatformPaymentProofTemplates`
  - `createPlatformPaymentProofTemplate`
  - `updatePlatformPaymentProofTemplate`
  - `suggestPlatformPaymentProofTemplateRule`
  - `getPlatformPaymentProofTemplateContributions`
  - `acceptPlatformPaymentProofTemplateContribution`
  - `rejectPlatformPaymentProofTemplateContribution`
  - `getPaymentProofTemplateContributions`
  - `submitPaymentProofTemplateContribution`
  - `suggestPaymentProofTemplateRule`

- [ ] **Step 1: Write failing source validation test**

Extend `PayNowPaymentUiAcceptanceValidationTest`:

```java
@Test
void payNowProofTemplatePlatformLibrarySourceIsWired() throws IOException {
    assertThat(source("src/router/index.ts")).contains("PlatformPaymentProofTemplatesPage");
    assertThat(source("src/components/platform/PlatformAdminNav.vue")).contains("/platform/payment/proof-templates");
    assertThat(source("src/api/paymentApi.ts"))
        .contains("getPlatformPaymentProofTemplates")
        .contains("suggestPlatformPaymentProofTemplateRule")
        .contains("submitPaymentProofTemplateContribution");
    assertThat(source("src/types/payment.ts"))
        .contains("PaymentProofTemplateContribution")
        .contains("PaymentProofTemplateRuleSuggestionResponse");
}
```

- [ ] **Step 2: Run validation test and verify failure**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: fail because frontend symbols do not exist.

- [ ] **Step 3: Add TypeScript types**

Add to `src/types/payment.ts`:

```ts
export interface PaymentProofTemplateContribution {
  id: string
  tenantId: string
  storeId: string | null
  sourceTemplateId: string | null
  platformTemplateId: string | null
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  layoutJson: string
  sampleFileName: string | null
  sampleContentType: string | null
  sampleFileDigest: string | null
  sampleRawText: string | null
  sampleOcrReference: string | null
  sampleOcrAmount: string | null
  status: 'submitted' | 'accepted' | 'rejected' | 'withdrawn'
  reviewNote: string | null
  version: number
  createdAt: string
  updatedAt: string
  reviewedAt: string | null
}

export interface PaymentProofTemplateContributionMutation {
  sourceTemplateId?: string | null
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  layoutJson: string
  sampleFileName?: string | null
  sampleContentType?: string | null
  sampleFileDigest?: string | null
  sampleRawText?: string | null
  sampleOcrReference?: string | null
  sampleOcrAmount?: string | null
}

export interface PaymentProofTemplateContributionReviewRequest {
  platformTemplateId?: string | null
  reviewNote?: string | null
  version: number
}

export interface PaymentProofTemplateContributionsResponse {
  success: true
  contributions: PaymentProofTemplateContribution[]
}

export interface PaymentProofTemplateContributionResponse {
  success: true
  contribution: PaymentProofTemplateContribution
}

export interface PaymentProofTemplateRuleSuggestionResponse {
  success: true
  suggestion: {
    bankCode: string
    bankName: string
    locale: string
    templateName: string
    suggestedLayoutJson: string
    ocr: PaymentProofTemplateTestScanResponse['ocr']
  }
}
```

- [ ] **Step 4: Add API client functions**

Add endpoint helpers:

```ts
function platformProofTemplatesEndpoint(): string {
  return '/api/v1/platform/payment/proof-templates'
}

function platformProofTemplateContributionsEndpoint(): string {
  return '/api/v1/platform/payment/proof-template-contributions'
}

function proofTemplateContributionsEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/payment/proof-template-contributions`
}
```

Add exported functions using existing `requestJson` and `requestMultipart`.

For rule suggestion multipart:

```ts
export async function suggestPaymentProofTemplateRule(
  storeId: string,
  image: File,
  metadata: { bankCode?: string; bankName?: string; locale?: string } = {},
  fetcher?: PaymentFetcher
): Promise<PaymentProofTemplateRuleSuggestionResponse> {
  const form = new FormData()
  form.set('image', image)
  Object.entries(metadata).forEach(([key, value]) => {
    if (value?.trim()) {
      form.set(key, value.trim())
    }
  })
  return requestMultipart(`/api/v1/stores/${encodeURIComponent(storeId)}/tenant-admin/payment/proof-template-rule-suggestions`, form, fetcher)
}
```

Implement platform variant with `/api/v1/platform/payment/proof-templates/rule-suggestions`.

- [ ] **Step 5: Run validation test and verify pass**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: pass after route/page stubs are added in Task 5. If this task is executed before UI files, keep the new assertions limited to API/types, then expand in Task 5.

- [ ] **Step 6: Commit**

```bash
git add src/types/payment.ts src/api/paymentApi.ts src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java
git commit -m "feat: add paynow proof template frontend APIs"
```

---

### Task 5: Platform Admin Page

**Files:**
- Create: `src/pages/PlatformPaymentProofTemplatesPage.vue`
- Modify: `src/components/platform/PlatformAdminNav.vue`
- Modify: `src/router/index.ts`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Modify: `src/i18n/locales/generated-zh-CN.ts`
- Modify: `src/i18n/locales/generated-en-SG.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

**Interfaces:**
- Consumes frontend API functions from Task 4.
- Produces route `platform-payment-proof-templates`.
- Produces nav link `/platform/payment/proof-templates`.

- [ ] **Step 1: Expand failing UI source test**

Add assertions:

```java
assertThat(source("src/pages/PlatformPaymentProofTemplatesPage.vue"))
    .contains("PlatformAdminNav")
    .contains("getPlatformPaymentProofTemplates")
    .contains("suggestPlatformPaymentProofTemplateRule")
    .contains("acceptPlatformPaymentProofTemplateContribution")
    .contains("rejectPlatformPaymentProofTemplateContribution")
    .contains("PayNow 回单样式库");
assertThat(source("src/router/index.ts"))
    .contains("PlatformPaymentProofTemplatesPage")
    .contains("platform-payment-proof-templates");
```

- [ ] **Step 2: Run UI validation and verify failure**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: fail because page and route do not exist.

- [ ] **Step 3: Add route and nav link**

In `src/router/index.ts`:

```ts
const PlatformPaymentProofTemplatesPage = () => import('../pages/PlatformPaymentProofTemplatesPage.vue')
```

Add route:

```ts
{
  path: '/platform/payment/proof-templates',
  name: 'platform-payment-proof-templates',
  component: PlatformPaymentProofTemplatesPage,
  meta: { requiresPlatformAdmin: true }
}
```

In `PlatformAdminNav.vue` add:

```ts
{ to: '/platform/payment/proof-templates', labelKey: 'nav.platform.paymentProofTemplates' }
```

- [ ] **Step 4: Add platform page**

Create a dense admin tool page with:

- `PlatformAdminNav`.
- Left template list with status filter.
- Main editor for platform template fields.
- Sample upload button to generate JSON rule suggestion.
- Contribution review section listing submitted tenant templates.
- Accept/reject controls.

Minimum script state:

```ts
const templates = ref<PaymentProofTemplate[]>([])
const contributions = ref<PaymentProofTemplateContribution[]>([])
const selected = ref<PaymentProofTemplate | null>(null)
const form = reactive<PaymentProofTemplateMutation>({
  bankCode: 'ocbc',
  bankName: 'OCBC',
  locale: 'zh-CN',
  templateName: 'OCBC PayNow',
  status: 'draft',
  priority: 20,
  layoutJson: '{}',
  version: null
})
```

Minimum actions:

```ts
async function loadAll(): Promise<void> {
  const [templateResponse, contributionResponse] = await Promise.all([
    getPlatformPaymentProofTemplates(),
    getPlatformPaymentProofTemplateContributions('submitted')
  ])
  templates.value = templateResponse.templates
  contributions.value = contributionResponse.contributions
}
```

Keep styling consistent with existing platform pages: sidebar + workspace, no nested cards, compact panels.

- [ ] **Step 5: Add i18n keys**

Add:

```ts
nav: {
  platform: {
    paymentProofTemplates: 'PayNow 回单样式库'
  }
}
```

and English:

```ts
paymentProofTemplates: 'PayNow Proof Templates'
```

Add generated keys for page text in both generated locale files. Use stable keys like `generated.platform-payment-proof-templates.001`.

- [ ] **Step 6: Run source validation**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/pages/PlatformPaymentProofTemplatesPage.vue src/components/platform/PlatformAdminNav.vue src/router/index.ts src/i18n/locales src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java
git commit -m "feat: add platform paynow proof template page"
```

---

### Task 6: Tenant Page Contribution And Rule Suggestion UI

**Files:**
- Modify: `src/pages/TenantAdminPaymentProofTemplatesPage.vue`
- Modify: `src/i18n/locales/generated-zh-CN.ts`
- Modify: `src/i18n/locales/generated-en-SG.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

**Interfaces:**
- Consumes:
  - `getPaymentProofTemplateContributions`
  - `submitPaymentProofTemplateContribution`
  - `suggestPaymentProofTemplateRule`
- Produces tenant UX text:
  - `已引用平台模板`
  - `租户自定义`
  - `提交给平台`
  - `平台审核中`

- [ ] **Step 1: Write failing tenant UI source test**

Add assertions:

```java
assertThat(source("src/pages/TenantAdminPaymentProofTemplatesPage.vue"))
    .contains("getPaymentProofTemplateContributions")
    .contains("submitPaymentProofTemplateContribution")
    .contains("suggestPaymentProofTemplateRule")
    .contains("已引用平台模板")
    .contains("提交给平台");
```

- [ ] **Step 2: Run UI validation and verify failure**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: fail because tenant page lacks contribution actions.

- [ ] **Step 3: Load contributions with templates**

In `loadTemplates`, fetch both:

```ts
const [templateResponse, contributionResponse] = await Promise.all([
  getPaymentProofTemplates(storeId.value),
  getPaymentProofTemplateContributions(storeId.value)
])
templates.value = templateResponse.templates
contributions.value = contributionResponse.contributions
```

- [ ] **Step 4: Group templates by source**

Add computed lists:

```ts
const platformTemplates = computed(() => sortedTemplates.value.filter(template => template.source === 'platform_seed'))
const tenantTemplates = computed(() => sortedTemplates.value.filter(template => template.source !== 'platform_seed'))
```

Render platform templates with read-only label `已引用平台模板` and tenant templates with `提交给平台` button.

- [ ] **Step 5: Add tenant rule suggestion action**

Replace the test-only upload wording with a clearer action:

```ts
async function suggestFromSelectedFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || testing.value) {
    return
  }
  testing.value = true
  try {
    const response = await suggestPaymentProofTemplateRule(storeId.value, file, {
      bankCode: form.bankCode,
      bankName: form.bankName,
      locale: form.locale
    })
    form.layoutJson = formatJson(response.suggestion.suggestedLayoutJson)
    testResult.value = { success: true, template: selected.value, ocr: response.suggestion.ocr }
  } finally {
    testing.value = false
  }
}
```

- [ ] **Step 6: Add submit-to-platform action**

For selected tenant template:

```ts
async function submitSelectedToPlatform(): Promise<void> {
  if (!selected.value || selected.value.source === 'platform_seed') {
    return
  }
  await submitPaymentProofTemplateContribution(storeId.value, {
    sourceTemplateId: selected.value.id,
    bankCode: selected.value.bankCode,
    bankName: selected.value.bankName,
    locale: selected.value.locale,
    templateName: selected.value.templateName,
    layoutJson: selected.value.layoutJson
  })
  savedText.value = gt('generated.tenant-admin-payment-proof-templates.032')
  await loadTemplates()
}
```

- [ ] **Step 7: Add generated text keys**

Add zh-CN generated values:

- `generated.tenant-admin-payment-proof-templates.032`: `已提交给平台审核`
- `033`: `已引用平台模板`
- `034`: `租户自定义`
- `035`: `提交给平台`
- `036`: `生成规则建议`
- `037`: `平台审核中`

Add equivalent English strings.

- [ ] **Step 8: Run UI validation and build**

Run:

```powershell
mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
npm run build
```

Expected: both pass.

- [ ] **Step 9: Commit**

```bash
git add src/pages/TenantAdminPaymentProofTemplatesPage.vue src/i18n/locales/generated-zh-CN.ts src/i18n/locales/generated-en-SG.ts src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java
git commit -m "feat: let tenants contribute paynow proof templates"
```

---

### Task 7: API Contract, Release Notes, Final Verification

**Files:**
- Modify: `docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md`
- Create: `docs/release-notes/2026-08-09-paynow-proof-template-platform-library.md`

**Interfaces:**
- Documents all new API paths from Tasks 3 and 4.
- Records migration `V055`, permissions, rollback, risk, and validation commands.

- [ ] **Step 1: Update API contract**

Add a section:

```markdown
## Platform Proof Template Library

Platform admins manage shared PayNow bank receipt OCR templates under:

- `GET /api/v1/platform/payment/proof-templates`
- `POST /api/v1/platform/payment/proof-templates`
- `PATCH /api/v1/platform/payment/proof-templates/{templateId}`
- `POST /api/v1/platform/payment/proof-templates/rule-suggestions`
- `GET /api/v1/platform/payment/proof-template-contributions`
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/accept`
- `POST /api/v1/platform/payment/proof-template-contributions/{contributionId}/reject`

Tenant admins contribute missing templates under:

- `GET /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions`
- `POST /api/v1/stores/{storeId}/tenant-admin/payment/proof-template-rule-suggestions`

Rule suggestions never mutate payment intent/session/proof verification state.
```

- [ ] **Step 2: Add release note**

Create release note with:

```markdown
# PayNow Proof Template Platform Library

## New

- Platform admins can maintain shared PayNow receipt templates.
- Tenants can use platform templates by default and submit missing bank layouts for review.
- Uploaded/captured receipt samples can generate draft JSON rule suggestions.

## Migration

- Adds `V055__paynow_platform_proof_template_contributions.sql`.
- Adds platform permission `platform.payment_proof_template.manage`.

## Safety

- Auto-confirmation still requires unique Ref and exact amount match.
- Uploaded sample image bytes are not stored.

## Rollback Notes

- Restore backend jar and frontend bundle.
- If schema rollback is required, delete contribution rows and drop `payment_proof_template_contributions`.
```

- [ ] **Step 3: Run focused backend tests**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplatePlatformMigrationTest,PaymentProofTemplateRuleSuggestionTest,PaymentProofTemplateContributionServiceTest,PlatformPaymentProofTemplateControllerTest,PaymentProofTemplateContributionControllerTest,PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: all pass.

- [ ] **Step 4: Run existing PayNow proof review regression tests**

Run:

```powershell
mvn "-Dtest=PaymentProofTemplateServiceTest,PaymentProofReviewServiceTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewControllerTest" test
```

Expected: all pass.

- [ ] **Step 5: Run migration regression tests**

Run:

```powershell
mvn "-Dtest=PaymentMigrationTest,PaymentProofTemplateMigrationTest,PaymentProofTemplatePlatformMigrationTest" test
```

Expected: all pass.

- [ ] **Step 6: Run frontend build**

Run:

```powershell
npm run build
```

Expected: `vue-tsc --noEmit && vite build` succeeds.

- [ ] **Step 7: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. Windows LF-to-CRLF warnings are acceptable if no whitespace errors are reported.

- [ ] **Step 8: Commit docs and final adjustments**

```bash
git add docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md docs/release-notes/2026-08-09-paynow-proof-template-platform-library.md
git commit -m "docs: document paynow proof template platform library"
```

---

## Plan Self-Review

Spec coverage:

- Platform-maintained shared template library: Task 3 and Task 5.
- Upload/capture sample rule suggestion: Task 2, Task 3, Task 5, Task 6.
- Tenant default platform usage and missing-template contribution: Task 6.
- Platform review queue and accept/reject: Task 3 and Task 5.
- Tenant isolation and platform permission: Task 1 and Task 3.
- Final confirmation safety gate unchanged: Global Constraints and Task 7 regression tests.
- No sample image byte persistence: Global Constraints, Task 1 schema, Task 7 docs.

Placeholder scan:

- The plan contains concrete paths, method names, status values, commands, and expected outcomes throughout.

Type consistency:

- Java records, API names, and TypeScript function names match across tasks.
- Contribution statuses match migration, service, and frontend types.
