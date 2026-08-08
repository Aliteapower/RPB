# PayNow Payment Proof Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add the third PayNow product-line submodule: a mobile-friendly payment proof review flow that reads a customer's PayNow bank receipt screenshot, extracts the RPB-generated Ref and amount, and auto-confirms the matching Quick Pay payment only when both values match exactly enough.

**Architecture:** Keep the feature inside the existing RPB-native `payment` module. Reuse the existing `payment_intents`, `payment_sessions`, `payment_proofs`, `payment_ocr_results`, and `payment_verifications` tables; add Java application services for OCR parsing, exact Ref/amount matching, proof persistence, and paid-state transition. Use `D:\payment_runtime` only as implementation evidence for OCR extraction and match flow, not as a runtime dependency or sidecar.

**Tech Stack:** Java 21, Spring Boot 3.5.15, PostgreSQL/Flyway, Spring JDBC, existing App Gate, Vue 3/Vite, mobile browser camera/file upload, optional Tesseract CLI adapter configured by environment.

## Global Constraints

- Documentation-only work for this planning task: do not change business code, migrations, API behavior, dependency files, or runtime configuration while writing this plan.
- Implementation must not run `D:\payment_runtime` as a sidecar service and must not call it from RPB at runtime.
- Bank receipt `Ref` means the complete RPB-generated payment reference stored in `payment_intents.payment_reference`.
- Ref format is prefix-driven and not fixed to `PIT`: valid examples include `PIT-202608-0021`, `QP-202608-0040-87D0`, and future `PREFIX-YYYYMM-SEQUENCE[-CHECK]` values.
- OCR matching must not treat bank `Transaction ID`, `交易编号`, or generic transaction number as the RPB Ref.
- Auto confirmation is allowed only when normalized extracted Ref equals exactly one active same-store `payment_intents.payment_reference` and extracted amount equals the intent amount within `0.01`.
- Commands require idempotency keys.
- Store-scoped payment APIs use `@RequireAppGate(appKey = "payment", permission = "<payment permission>")`.
- Controllers call application services, not repositories.
- Persistence entities do not leak into API responses.
- All payment queries and mutations must include tenant and store scope.
- Monetary values use PostgreSQL `numeric(12,2)` and Java `BigDecimal`, not floating point.
- OCR raw text and proof files may contain sensitive information and are exposed only to proof review permissions.
- Before runtime or migration validation, use `target/local-postgres-current.txt` according to `AGENTS.md`.

---

## File Structure

- Create: `docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md` for endpoint contract, permissions, DTOs, errors, and idempotency.
- Modify: `src/main/resources/db/migration/V047__payment_product_line_foundation.sql` only if implementation chooses to seed a new permission in a follow-up migration; this plan prefers a new V053 migration when permission backfill is required.
- Create: `src/main/resources/db/migration/V053__paynow_payment_proof_review_permissions.sql` for `payment.proof.review` permission and existing staff/admin backfill.
- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java` to include proof-review entry permission.
- Modify: `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java` if payment entry permission resolution is still explicit.
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferencePattern.java`.
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferenceGenerator.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java` to generate references through `PaymentReferenceGenerator`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofOcrFields.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofOcrAdapter.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofReviewService.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofScanCommand.java`.
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofScanResult.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentServiceErrorCode.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/PaymentIntentRepository.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java`.
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentProofReviewRepository.java`.
- Create: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofReviewRepository.java`.
- Create: `src/main/java/com/rpb/reservation/payment/provider/TesseractPaymentProofOcrAdapter.java`.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewController.java`.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewRequests.java`.
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewResponses.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentApiErrorCode.java`.
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentResponses.java` if candidate records need proof-review fields.
- Modify: `src/router/index.ts`.
- Modify: `src/components/tenant-admin/TenantAdminNav.vue`.
- Modify: `src/components/staff/staffBottomNavItems.ts` if proof review should be reachable from staff bottom navigation.
- Create: `src/pages/PaymentProofReviewPage.vue`.
- Modify: `src/api/paymentApi.ts`.
- Modify: `src/types/payment.ts`.
- Modify: `src/i18n/locales/zh-CN.ts`.
- Modify: `src/i18n/locales/en-SG.ts`.
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`.
- Create: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferencePatternTest.java`.
- Create: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferenceGeneratorTest.java`.
- Create: `src/test/java/com/rpb/reservation/payment/application/PaymentProofReviewServiceTest.java`.
- Create: `src/test/java/com/rpb/reservation/payment/api/PaymentProofReviewControllerTest.java`.
- Create: `src/test/java/com/rpb/reservation/payment/PaymentProofReviewMigrationTest.java`.
- Create: `docs/release-notes/2026-08-08-paynow-payment-proof-review.md` after implementation.

## Task 1: API Contract And Permission Boundary

**Files:**
- Create: `docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md`
- Create: `src/main/resources/db/migration/V053__paynow_payment_proof_review_permissions.sql`
- Modify: `src/main/java/com/rpb/reservation/appgate/domain/AppGateRequiredPermission.java`
- Modify: `src/test/java/com/rpb/reservation/payment/PaymentProofReviewMigrationTest.java`

**Interfaces:**
- Produces: permission `payment.proof.review`.
- Produces: API contract for `GET /api/v1/stores/{storeId}/payments/proof-review/candidates`.
- Produces: API contract for `POST /api/v1/stores/{storeId}/payments/proof-review/scan`.

- [x] **Step 1: Write the API contract**

Create `docs/api/PAYNOW_PAYMENT_PROOF_REVIEW_API_CONTRACT.md` with:

```markdown
# PayNow Payment Proof Review API Contract

## Purpose

Payment Proof Review lets a payment-enabled store employee upload or capture a PayNow bank receipt screenshot. RPB extracts the RPB-generated Ref and amount, matches them to one active Quick Pay intent in the same tenant/store, and auto-confirms only when both values match.

## Ref Definition

`Ref` is `payment_intents.payment_reference`.

Accepted RPB reference patterns:

- `PIT-202608-0021`
- `QP-202608-0040-87D0`
- `AB12-202608-123456-Z9X7`

Ref extraction must ignore bank transaction identifiers such as `Transaction ID`, `Transaction Ref`, `交易编号`, and long non-hyphenated bank ids.

## Endpoints

### GET /api/v1/stores/{storeId}/payments/proof-review/candidates

Permission: `payment.proof.review`

Query:

- `businessDate`: optional ISO date. Defaults to current open payment business day.
- `terminalCode`: optional exact terminal code.
- `limit`: optional integer, default `80`, min `1`, max `200`.

Response:

```json
{
  "success": true,
  "businessDate": "2026-08-08",
  "candidates": [
    {
      "intentId": "50000000-0000-0000-0000-000000000001",
      "sessionId": "60000000-0000-0000-0000-000000000001",
      "intentNo": "PIT-202608-0021",
      "sessionNo": "PRS-ABCDEF1234567890",
      "displayNumber": 21,
      "paymentReference": "PIT-202608-0021",
      "amount": "0.10",
      "currency": "SGD",
      "intentStatus": "pending",
      "sessionStatus": "pending",
      "terminalCode": "T1",
      "cashierName": "Alice",
      "createdAt": "2026-08-08T04:10:00Z",
      "expiresAt": "2026-08-08T04:12:00Z"
    }
  ]
}
```

### POST /api/v1/stores/{storeId}/payments/proof-review/scan

Permission: `payment.proof.review`

Request: `multipart/form-data`

- `image`: required file, one of `.png`, `.jpg`, `.jpeg`, `.webp`.
- `idempotencyKey`: required string.
- `terminalCode`: optional string.
- `businessDate`: optional ISO date.

Auto-confirm response:

```json
{
  "success": true,
  "outcome": "auto_confirmed",
  "replayed": false,
  "intentId": "50000000-0000-0000-0000-000000000001",
  "sessionId": "60000000-0000-0000-0000-000000000001",
  "proofId": "70000000-0000-0000-0000-000000000001",
  "verificationId": "80000000-0000-0000-0000-000000000001",
  "paymentReference": "PIT-202608-0021",
  "expectedAmount": "0.10",
  "ocr": {
    "extractedReference": "PIT-202608-0021",
    "extractedAmount": "0.10",
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.9600"
  },
  "checks": {
    "reference": "match",
    "amount": "match"
  }
}
```

Review response:

```json
{
  "success": true,
  "outcome": "needs_review",
  "replayed": false,
  "intentId": "50000000-0000-0000-0000-000000000001",
  "sessionId": "60000000-0000-0000-0000-000000000001",
  "proofId": "70000000-0000-0000-0000-000000000001",
  "verificationId": "80000000-0000-0000-0000-000000000001",
  "paymentReference": "PIT-202608-0021",
  "expectedAmount": "0.10",
  "ocr": {
    "extractedReference": "PIT-202608-0021",
    "extractedAmount": null,
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.7100"
  },
  "checks": {
    "reference": "match",
    "amount": "missing"
  }
}
```

No-match response:

```json
{
  "success": true,
  "outcome": "no_match",
  "replayed": false,
  "ocr": {
    "extractedReference": null,
    "extractedAmount": "0.10",
    "bankCode": "ocbc",
    "successDetected": true,
    "confidence": "0.5200"
  },
  "checks": {
    "reference": "missing",
    "amount": "not_checked"
  }
}
```

## Error Codes

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `REQUEST_INVALID` | Missing idempotency key, invalid file type, invalid business date, or malformed request. |
| 400 | `PAYMENT_OCR_UNAVAILABLE` | OCR adapter is not configured or failed before text extraction. |
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Actor lacks store access or App Gate permission. |
| 409 | `PAYMENT_INTENT_STATE_CONFLICT` | Matched intent is paid, cancelled, failed, or no longer confirmable. |
| 409 | `IDEMPOTENCY_CONFLICT` | Same idempotency key was used with a different file digest or scope. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Idempotency

`idempotencyKey` is scoped by tenant. A replay with the same scope and file digest returns the original result with `replayed = true`. A replay with a different file digest returns `IDEMPOTENCY_CONFLICT`.
```

- [x] **Step 2: Write failing migration test for proof review permission**

Create `PaymentProofReviewMigrationTest`:

```java
package com.rpb.reservation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentProofReviewMigrationTest {

    @Test
    void proofReviewPermissionIsDocumentedForV053() throws Exception {
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/db/migration/V053__paynow_payment_proof_review_permissions.sql"
        ));

        assertThat(migration)
            .contains("payment.proof.review")
            .contains("payment")
            .contains("store_staff")
            .contains("store_manager")
            .contains("tenant_admin");
    }
}
```

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewMigrationTest" test
```

Expected: FAIL because V053 does not exist.

- [x] **Step 3: Add V053 permission migration**

Create `V053__paynow_payment_proof_review_permissions.sql` using the same `auth_account_permissions` pattern as V048, V049, and V050:

```sql
with required_permissions(permission_code) as (
    values
        ('payment.proof.review')
),
payment_proof_review_accounts as (
    select distinct account.id as account_id
    from auth_accounts account
    join auth_account_roles role
      on role.account_id = account.id
     and role.role_code in ('tenant_admin', 'store_manager', 'store_staff')
     and role.deleted_at is null
    where account.actor_type <> 'platform_admin'
      and account.status = 'active'
      and account.deleted_at is null
)
insert into auth_account_permissions (account_id, permission_code)
select account.account_id, permission.permission_code
from payment_proof_review_accounts account
cross join required_permissions permission
where not exists (
    select 1
    from auth_account_permissions existing
    where existing.account_id = account.account_id
      and existing.permission_code = permission.permission_code
      and existing.deleted_at is null
);
```

- [x] **Step 4: Add App Gate payment entry permission**

Add to `AppGateRequiredPermission`:

```java
public static final String PAYMENT_PROOF_REVIEW = "payment.proof.review";
```

Update `PAYMENT_ENTRY_PERMISSIONS`:

```java
public static final Set<String> PAYMENT_ENTRY_PERMISSIONS = Set.of(
    PAYMENT_INTENT_VIEW,
    PAYMENT_INTENT_CREATE,
    PAYMENT_VERIFICATION_REVIEW,
    PAYMENT_PROOF_REVIEW
);
```

- [x] **Step 5: Run permission tests**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewMigrationTest,AppGateServiceTest" test
```

Expected: PASS.

## Task 2: Canonical Payment Reference Pattern And Generator

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferencePattern.java`
- Create: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferenceGenerator.java`
- Create: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferencePatternTest.java`
- Create: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferenceGeneratorTest.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java`
- Modify: `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java`

**Interfaces:**
- Produces: `PaymentReferencePattern.extractSystemReference(String rawText)`.
- Produces: `PaymentReferencePattern.normalize(String value)`.
- Produces: `PaymentReferenceGenerator.generate(String prefix, YearMonth period, int sequence)`.

- [x] **Step 1: Write failing pattern tests**

Create `PaymentReferencePatternTest`:

```java
package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentReferencePatternTest {

    @Test
    void extractsPitReferenceFromChineseBankReceiptText() {
        String raw = """
            您已支付 0.10 SGD
            讯息
            PIT-202608-0021
            交易编号：2605160110303261
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("PIT-202608-0021");
    }

    @Test
    void extractsQpReferenceWithChecksumFromCommentText() {
        String raw = """
            Payment successful
            You've sent S$0.10 to Stanley Teo
            Comment: "QP-202608-0040-87D0"
            Transaction ID 20260517TRBUSGSSGBRT7474840
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("QP-202608-0040-87D0");
    }

    @Test
    void ignoresLongBankTransactionIdWithoutSystemRefShape() {
        String raw = "Transaction ID 20260517TRBUSGSSGBRT7474840 amount SGD 0.10";

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).isEmpty();
    }

    @Test
    void normalizesCaseWhitespaceAndHyphenSpacing() {
        assertThat(PaymentReferencePattern.normalize(" qp - 202608 - 0040 - 87d0 "))
            .isEqualTo("QP-202608-0040-87D0");
    }
}
```

Run:

```powershell
mvn -q "-Dtest=PaymentReferencePatternTest" test
```

Expected: FAIL because `PaymentReferencePattern` does not exist.

- [x] **Step 2: Implement `PaymentReferencePattern`**

```java
package com.rpb.reservation.payment.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentReferencePattern {
    private static final Pattern SYSTEM_REFERENCE = Pattern.compile(
        "\\b([A-Z0-9]{2,8}-\\d{6}-\\d{3,6}(?:-[A-Z0-9]{3,8})?)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private PaymentReferencePattern() {
    }

    public static Optional<String> extractSystemReference(String rawText) {
        String normalizedText = normalizeForSearch(rawText);
        Matcher matcher = SYSTEM_REFERENCE.matcher(normalizedText);
        while (matcher.find()) {
            String candidate = normalize(matcher.group(1));
            if (!candidate.isBlank()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static String normalize(String value) {
        String text = value == null ? "" : value.trim().toUpperCase();
        text = text.replace('\u2010', '-')
            .replace('\u2011', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-');
        text = text.replaceAll("\\s*-\\s*", "-");
        text = text.replaceAll("\\s+", "");
        text = text.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
        return text;
    }

    private static String normalizeForSearch(String value) {
        String text = value == null ? "" : value.toUpperCase();
        text = text.replace('\u2010', '-')
            .replace('\u2011', '-')
            .replace('\u2012', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-');
        return text.replaceAll("\\s*-\\s*", "-");
    }
}
```

- [x] **Step 3: Write failing generator tests**

Create `PaymentReferenceGeneratorTest`:

```java
package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PaymentReferenceGeneratorTest {

    @Test
    void generatesPrefixPeriodSequenceAndCheckSegment() {
        String result = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 40);

        assertThat(result).matches("QP-202608-0040-[A-Z0-9]{4}");
    }

    @Test
    void supportsPitPrefixForCanonicalReference() {
        String result = PaymentReferenceGenerator.generate("PIT", YearMonth.of(2026, 8), 21);

        assertThat(result).matches("PIT-202608-0021-[A-Z0-9]{4}");
    }

    @Test
    void rejectsPrefixLongerThanEightCharacters() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("TOO-LONG", YearMonth.of(2026, 8), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_prefix_invalid");
    }
}
```

- [x] **Step 4: Implement `PaymentReferenceGenerator`**

```java
package com.rpb.reservation.payment.domain;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.zip.CRC32;

public final class PaymentReferenceGenerator {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private PaymentReferenceGenerator() {
    }

    public static String generate(String prefix, YearMonth period, int sequence) {
        String cleanPrefix = normalizePrefix(prefix);
        if (period == null || sequence <= 0 || sequence > 999999) {
            throw new IllegalArgumentException("payment_reference_sequence_invalid");
        }
        String base = cleanPrefix + "-" + period.format(PERIOD_FORMATTER) + "-" + "%04d".formatted(sequence);
        return base + "-" + checksum(base);
    }

    private static String normalizePrefix(String prefix) {
        String clean = prefix == null ? "" : prefix.trim().toUpperCase();
        if (!clean.matches("[A-Z0-9]{2,8}")) {
            throw new IllegalArgumentException("payment_reference_prefix_invalid");
        }
        return clean;
    }

    private static String checksum(String base) {
        CRC32 crc = new CRC32();
        crc.update(base.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue();
        char[] out = new char[4];
        for (int i = 3; i >= 0; i--) {
            out[i] = ALPHABET[(int) (value % ALPHABET.length)];
            value = value / ALPHABET.length;
        }
        return new String(out);
    }
}
```

- [x] **Step 5: Update Quick Pay reference generation**

In `PaymentIntentService.createNewAfterSequenceLock`, replace the inline reference construction:

```java
String paymentReference = quickPayConfig.referencePrefix() + "-" + periodText + "-" + "%04d".formatted(displayNumber) + "-" + shortToken();
```

with:

```java
String paymentReference = PaymentReferenceGenerator.generate(
    quickPayConfig.referencePrefix(),
    period,
    displayNumber
);
```

Keep historical `QP-*` rows compatible because matching reads `payment_intents.payment_reference`; it does not infer prefix from code.

- [x] **Step 6: Update service tests**

In `PaymentIntentServiceTest`, change assertions from:

```java
assertThat(result.intent().paymentReference()).startsWith("QP-202608-0001-");
```

to:

```java
assertThat(result.intent().paymentReference()).matches("QP-202608-0001-[A-Z0-9]{4}");
```

Run:

```powershell
mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,PaymentIntentServiceTest" test
```

Expected: PASS.

## Task 3: OCR Adapter Boundary And Field Parser

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofOcrFields.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofOcrAdapter.java`
- Create: `src/main/java/com/rpb/reservation/payment/provider/TesseractPaymentProofOcrAdapter.java`
- Create: `src/test/java/com/rpb/reservation/payment/application/PaymentProofReviewServiceTest.java`

**Interfaces:**
- Produces: `PaymentProofOcrAdapter.extract(Path file, PaymentProofOcrExpected expected)`.
- Produces: OCR field object with extracted reference, amount, paid time, bank code, success flag, confidence, raw text, and raw JSON.

- [x] **Step 1: Add OCR field records**

Create:

```java
package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentProofOcrFields(
    String extractedReference,
    BigDecimal extractedAmount,
    OffsetDateTime extractedPaidAt,
    String bankCode,
    boolean successDetected,
    BigDecimal confidence,
    String rawText,
    String rawJson
) {
}
```

Create:

```java
package com.rpb.reservation.payment.application;

import java.math.BigDecimal;

public record PaymentProofOcrExpected(
    String expectedReference,
    BigDecimal expectedAmount
) {
}
```

Create:

```java
package com.rpb.reservation.payment.application;

import java.nio.file.Path;

public interface PaymentProofOcrAdapter {
    PaymentProofOcrFields extract(Path file, PaymentProofOcrExpected expected);
}
```

- [x] **Step 2: Add parser helpers in `TesseractPaymentProofOcrAdapter`**

Implement the Java version of the `D:\payment_runtime\backend\app\services\payment_ocr_service.py` essentials:

```java
static Optional<BigDecimal> extractAmount(String rawText, BigDecimal expectedAmount) {
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "(?:SGD|S\\$|\\$)\\s*([0-9OoIl]{1,3}(?:,[0-9OoIl]{3})*(?:[.,][0-9OoIl]{1,2})?)|"
            + "([0-9OoIl]{1,3}(?:,[0-9OoIl]{3})*(?:[.,][0-9OoIl]{1,2})?)\\s*(?:SGD|S6D|SG)",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    java.util.List<BigDecimal> candidates = new java.util.ArrayList<>();
    java.util.regex.Matcher matcher = pattern.matcher(rawText == null ? "" : rawText);
    while (matcher.find()) {
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        parseAmount(value).ifPresent(candidates::add);
    }
    if (candidates.isEmpty()) {
        return Optional.empty();
    }
    if (expectedAmount != null) {
        candidates.sort(java.util.Comparator.comparing(v -> v.subtract(expectedAmount).abs()));
    }
    return Optional.of(candidates.get(0).setScale(2, java.math.RoundingMode.HALF_UP));
}

static Optional<BigDecimal> parseAmount(String value) {
    String clean = value == null ? "" : value.trim()
        .replace('O', '0')
        .replace('o', '0')
        .replace('I', '1')
        .replace('l', '1')
        .replace(",", "");
    clean = clean.replaceAll("[^0-9.]", "");
    if (clean.isBlank()) {
        return Optional.empty();
    }
    return Optional.of(new BigDecimal(clean).setScale(2, java.math.RoundingMode.HALF_UP));
}
```

Reference extraction must call:

```java
PaymentReferencePattern.extractSystemReference(rawText)
```

Success detection keywords must include:

```java
List.of(
    "payment successful",
    "transfer successful",
    "sent successfully",
    "successfully sent",
    "you have paid",
    "you've sent",
    "您已支付",
    "已支付",
    "支付成功",
    "转账成功",
    "交易成功"
)
```

- [x] **Step 3: Add parser tests using receipt-like OCR text**

Add tests to `PaymentProofReviewServiceTest`:

```java
@Test
void parserExtractsOcbcChineseReceiptReferenceAndAmount() {
    String raw = """
        您已支付 0.10 SGD
        至
        ZHANG XIANLI
        讯息
        RCP-202605-0023
        转账日期
        16 May 2026
        交易编号：2605160110303261
        """;

    assertThat(PaymentReferencePattern.extractSystemReference(raw)).contains("RCP-202605-0023");
    assertThat(TesseractPaymentProofOcrAdapter.extractAmount(raw, new BigDecimal("0.10"))).contains(new BigDecimal("0.10"));
}

@Test
void parserExtractsEnglishCommentReferenceAndAmount() {
    String raw = """
        Payment successful
        You've sent S$0.10 to Stanley Teo
        17 May 2026 at 19:50
        Comment: "HT-202605-0012"
        Transaction ID 20260517TRBUSGSSGBRT7474840
        """;

    assertThat(PaymentReferencePattern.extractSystemReference(raw)).contains("HT-202605-0012");
    assertThat(TesseractPaymentProofOcrAdapter.extractAmount(raw, new BigDecimal("0.10"))).contains(new BigDecimal("0.10"));
}
```

- [x] **Step 4: Implement optional Tesseract CLI extraction**

`TesseractPaymentProofOcrAdapter.extract(...)`:

```java
String command = System.getenv("PAYMENT_OCR_TESSERACT_CMD");
if (command == null || command.isBlank()) {
    command = "tesseract";
}
ProcessBuilder builder = new ProcessBuilder(
    command,
    file.toAbsolutePath().toString(),
    "stdout",
    "-l",
    "eng+chi_sim",
    "--oem",
    "1",
    "--psm",
    "6"
);
```

If the process exits non-zero or command is unavailable, throw:

```java
new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE)
```

Use parser helpers to build `PaymentProofOcrFields`.

- [x] **Step 5: Run OCR parser tests**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewServiceTest#parserExtractsOcbcChineseReceiptReferenceAndAmount,PaymentProofReviewServiceTest#parserExtractsEnglishCommentReferenceAndAmount" test
```

Expected: PASS.

## Task 4: Proof Review Matching Service

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofReviewService.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofScanCommand.java`
- Create: `src/main/java/com/rpb/reservation/payment/application/PaymentProofScanResult.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentServiceErrorCode.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentProofReviewRepository.java`
- Create: `src/test/java/com/rpb/reservation/payment/application/PaymentProofReviewServiceTest.java`

**Interfaces:**
- Consumes: `PaymentProofOcrAdapter`.
- Consumes: `PaymentProofReviewRepository.findActiveCandidateByReference(StoreScope scope, String reference, LocalDate businessDate, String terminalCode)`.
- Produces: `PaymentProofReviewService.scanAndMatch(StoreScope scope, PaymentProofScanCommand command, CurrentActor actor)`.

- [x] **Step 1: Write failing exact-match auto-confirm test**

```java
@Test
void autoConfirmsWhenExtractedReferenceAndAmountMatchOnePendingIntent() {
    PaymentProofCandidate candidate = candidate("PIT-202608-0021", new BigDecimal("0.10"));
    repository.candidate = Optional.of(candidate);
    ocr.fields = new PaymentProofOcrFields(
        "PIT-202608-0021",
        new BigDecimal("0.10"),
        null,
        "ocbc",
        true,
        new BigDecimal("0.9600"),
        "讯息 PIT-202608-0021 您已支付 0.10 SGD",
        "{}"
    );

    PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-001"), actor);

    assertThat(result.outcome()).isEqualTo("auto_confirmed");
    assertThat(result.paymentReference()).isEqualTo("PIT-202608-0021");
    assertThat(repository.confirmedIntentId).isEqualTo(candidate.intentId());
    assertThat(repository.createdProofStatus).isEqualTo("confirmed");
    assertThat(repository.createdVerificationStatus).isEqualTo("confirmed");
}
```

- [x] **Step 2: Write failing no-match and amount-mismatch tests**

```java
@Test
void noMatchWhenReferenceIsMissing() {
    ocr.fields = new PaymentProofOcrFields(
        null,
        new BigDecimal("0.10"),
        null,
        "ocbc",
        true,
        new BigDecimal("0.5200"),
        "Transaction ID 2605160110303261 SGD 0.10",
        "{}"
    );

    PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-002"), actor);

    assertThat(result.outcome()).isEqualTo("no_match");
    assertThat(repository.confirmedIntentId).isNull();
}

@Test
void needsReviewWhenReferenceMatchesButAmountDoesNotMatch() {
    PaymentProofCandidate candidate = candidate("QP-202608-0040-87D0", new BigDecimal("1.00"));
    repository.candidate = Optional.of(candidate);
    ocr.fields = new PaymentProofOcrFields(
        "QP-202608-0040-87D0",
        new BigDecimal("0.10"),
        null,
        "ocbc",
        true,
        new BigDecimal("0.8600"),
        "QP-202608-0040-87D0 SGD 0.10",
        "{}"
    );

    PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-003"), actor);

    assertThat(result.outcome()).isEqualTo("needs_review");
    assertThat(result.checks().amount()).isEqualTo("mismatch");
    assertThat(repository.createdVerificationStatus).isEqualTo("pending");
    assertThat(repository.confirmedIntentId).isNull();
}
```

- [x] **Step 3: Implement command/result records**

```java
public record PaymentProofScanCommand(
    String idempotencyKey,
    String originalFileName,
    String contentType,
    byte[] fileBytes,
    java.time.LocalDate businessDate,
    String terminalCode
) {
}
```

```java
public record PaymentProofScanResult(
    boolean success,
    boolean replayed,
    String outcome,
    java.util.UUID intentId,
    java.util.UUID sessionId,
    java.util.UUID proofId,
    java.util.UUID verificationId,
    String paymentReference,
    java.math.BigDecimal expectedAmount,
    PaymentProofOcrFields ocr,
    PaymentProofChecks checks
) {
}
```

```java
public record PaymentProofChecks(String reference, String amount) {
}
```

- [x] **Step 4: Implement matching rules**

Inside `PaymentProofReviewService`:

```java
String extractedRef = PaymentReferencePattern.normalize(fields.extractedReference());
if (extractedRef.isBlank()) {
    return noMatch(fields, "missing", "not_checked");
}

Optional<PaymentProofCandidate> candidate = repository.findActiveCandidateByReference(
    scope,
    extractedRef,
    command.businessDate(),
    command.terminalCode()
);
if (candidate.isEmpty()) {
    return noMatch(fields, "not_found", "not_checked");
}

PaymentProofCandidate target = candidate.get();
String referenceCheck = extractedRef.equals(PaymentReferencePattern.normalize(target.paymentReference()))
    ? "match"
    : "mismatch";
String amountCheck = amountMatches(fields.extractedAmount(), target.amount())
    ? "match"
    : (fields.extractedAmount() == null ? "missing" : "mismatch");
```

Auto-confirm only when:

```java
boolean canAutoConfirm = "match".equals(referenceCheck)
    && "match".equals(amountCheck)
    && target.intentStatus().equals("pending")
    && target.sessionStatus().equals("pending");
```

Amount comparison:

```java
private static boolean amountMatches(BigDecimal extracted, BigDecimal expected) {
    if (extracted == null || expected == null) {
        return false;
    }
    return extracted.subtract(expected).abs().compareTo(new BigDecimal("0.01")) <= 0;
}
```

- [x] **Step 5: Implement persistence call contract**

Repository methods:

```java
Optional<PaymentProofScanResult> findScanResultByIdempotencyKey(StoreScope scope, String idempotencyKey, String fileDigest);

Optional<PaymentProofCandidate> findActiveCandidateByReference(
    StoreScope scope,
    String paymentReference,
    LocalDate businessDate,
    String terminalCode
);

PaymentProofScanResult createMatchedProofAndMaybeConfirm(
    StoreScope scope,
    PaymentProofCandidate candidate,
    PaymentProofScanCommand command,
    PaymentProofOcrFields fields,
    PaymentProofChecks checks,
    boolean autoConfirm,
    UUID actorId,
    String fileDigest
);
```

If `autoConfirm = true`, the repository must atomically:

- Insert `payment_proofs` with `status = 'confirmed'`.
- Insert `payment_ocr_results`.
- Insert `payment_verifications` with `status = 'confirmed'`, `matched_reference = true`, `matched_amount = true`, `reviewed_by = actorId`, `reviewed_at = now()`.
- Update `payment_intents.status = 'paid'`.
- Update all same-intent `payment_sessions.status = 'paid'`.
- Insert `payment_events` for `proof_submitted` and `verification_confirmed`.

If `autoConfirm = false` but Ref found, atomically:

- Insert `payment_proofs` with `status = 'matched'`.
- Insert `payment_ocr_results`.
- Insert `payment_verifications` with `status = 'pending'`, matched booleans from checks.
- Update `payment_intents.status = 'awaiting_verification'` only when current status is `pending`.
- Update matched session status to `awaiting_verification` only when current status is `pending`.
- Insert `payment_events` for `proof_submitted` and `verification_pending`.

- [x] **Step 6: Run service tests**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewServiceTest" test
```

Expected: PASS.

## Task 5: JDBC Proof Review Repository

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/persistence/PaymentProofReviewRepository.java`
- Create: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofReviewRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/PaymentIntentRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java`
- Create: `src/test/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofReviewRepositoryTest.java`

**Interfaces:**
- Produces tenant/store-safe candidate lookup.
- Produces atomic proof/OCR/verification creation and state transition.

- [x] **Step 1: Write repository integration test for scoped lookup**

```java
@Test
void findsOnlySameStorePendingCandidateByPaymentReference() {
    StoreScope scope = fixture.storeScope();
    UUID intentId = fixture.insertQuickPayIntent(scope, "PIT-202608-0021", new BigDecimal("0.10"), "pending");
    UUID sessionId = fixture.insertPaymentSession(scope, intentId, LocalDate.parse("2026-08-08"), "T1", "pending");
    fixture.insertQuickPayIntent(fixture.otherStoreScope(), "PIT-202608-0021", new BigDecimal("0.10"), "pending");

    Optional<PaymentProofCandidate> result = repository.findActiveCandidateByReference(
        scope,
        "PIT-202608-0021",
        LocalDate.parse("2026-08-08"),
        "T1"
    );

    assertThat(result).isPresent();
    assertThat(result.get().intentId()).isEqualTo(intentId);
    assertThat(result.get().sessionId()).isEqualTo(sessionId);
}
```

- [x] **Step 2: Implement lookup SQL**

```sql
select
    i.id as intent_id,
    s.id as session_id,
    i.intent_no,
    s.session_no,
    s.display_number,
    s.business_date,
    i.payment_reference,
    i.amount,
    i.currency,
    i.status as intent_status,
    s.status as session_status,
    s.terminal_code,
    s.cashier_name
from payment_intents i
join payment_sessions s
  on s.tenant_id = i.tenant_id
 and s.store_id = i.store_id
 and s.intent_id = i.id
where i.tenant_id = ?
  and i.store_id = ?
  and upper(i.payment_reference) = upper(?)
  and i.source_type = 'quick_pay'
  and i.status in ('pending', 'awaiting_verification')
  and s.status in ('pending', 'awaiting_verification')
  and (?::date is null or s.business_date = ?)
  and (? is null or s.terminal_code = ?)
order by s.created_at desc
limit 2
```

If two rows are returned, service returns `needs_review` with `reference = "ambiguous"` and does not auto-confirm.

- [x] **Step 3: Write repository integration test for auto-confirm transaction**

```java
@Test
void autoConfirmCreatesProofOcrVerificationAndMarksIntentPaid() {
    PaymentProofCandidate candidate = fixture.pendingCandidate("QP-202608-0040-87D0", new BigDecimal("1.00"));

    PaymentProofScanResult result = repository.createMatchedProofAndMaybeConfirm(
        fixture.storeScope(),
        candidate,
        fixture.scanCommand("proof-auto"),
        fixture.ocrFields("QP-202608-0040-87D0", new BigDecimal("1.00")),
        new PaymentProofChecks("match", "match"),
        true,
        fixture.actorId(),
        fixture.fileDigest()
    );

    assertThat(result.outcome()).isEqualTo("auto_confirmed");
    assertThat(fixture.intentStatus(candidate.intentId())).isEqualTo("paid");
    assertThat(fixture.sessionStatus(candidate.sessionId())).isEqualTo("paid");
    assertThat(fixture.proofStatus(result.proofId())).isEqualTo("confirmed");
    assertThat(fixture.verificationStatus(result.verificationId())).isEqualTo("confirmed");
}
```

- [x] **Step 4: Implement inserts and updates**

Use a single `@Transactional` repository method. Insert proof:

```sql
insert into payment_proofs (
    tenant_id, store_id, intent_id, session_id, status,
    storage_key, file_name, content_type, expected_reference,
    expected_amount, submitted_by, idempotency_key, metadata_json
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
returning id
```

Insert OCR:

```sql
insert into payment_ocr_results (
    tenant_id, store_id, proof_id, extracted_reference, extracted_amount,
    extracted_paid_at, bank_code, confidence, raw_text, raw_json
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
```

Insert verification:

```sql
insert into payment_verifications (
    tenant_id, store_id, intent_id, proof_id, status,
    matched_reference, matched_amount, reviewed_by, reviewed_at,
    idempotency_key, metadata_json
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
returning id
```

Auto-confirm updates:

```sql
update payment_intents
set status = 'paid',
    updated_at = now(),
    version = version + 1
where tenant_id = ?
  and store_id = ?
  and id = ?
  and status = 'pending'
```

```sql
update payment_sessions
set status = 'paid',
    updated_at = now(),
    version = version + 1
where tenant_id = ?
  and store_id = ?
  and intent_id = ?
  and status = 'pending'
```

If either update affects zero rows during auto-confirm, throw `PaymentServiceException(PAYMENT_INTENT_STATE_CONFLICT)`.

- [x] **Step 5: Run repository tests**

Run:

```powershell
mvn -q "-Dtest=JdbcPaymentProofReviewRepositoryTest" test
```

Expected: PASS against the local test database setup used by existing payment persistence tests.

## Task 6: Proof Review API Controller

**Files:**
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewController.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewRequests.java`
- Create: `src/main/java/com/rpb/reservation/payment/api/PaymentProofReviewResponses.java`
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentApiErrorCode.java`
- Create: `src/test/java/com/rpb/reservation/payment/api/PaymentProofReviewControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/stores/{storeId}/payments/proof-review/candidates`.
- Produces: `POST /api/v1/stores/{storeId}/payments/proof-review/scan`.

- [x] **Step 1: Write annotation and mapping tests**

```java
@Test
void scanRequiresProofReviewPermission() throws NoSuchMethodException {
    Method method = PaymentProofReviewController.class.getMethod(
        "scanProof",
        UUID.class,
        PaymentProofReviewRequests.ScanProofForm.class
    );

    PostMapping mapping = method.getAnnotation(PostMapping.class);
    RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/scan");
    assertThat(gate).isNotNull();
    assertThat(gate.appKey()).isEqualTo("payment");
    assertThat(gate.permission()).isEqualTo("payment.proof.review");
}

@Test
void candidatesRequiresProofReviewPermission() throws NoSuchMethodException {
    Method method = PaymentProofReviewController.class.getMethod(
        "candidates",
        UUID.class,
        LocalDate.class,
        String.class,
        int.class
    );

    GetMapping mapping = method.getAnnotation(GetMapping.class);
    RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/candidates");
    assertThat(gate).isNotNull();
    assertThat(gate.permission()).isEqualTo("payment.proof.review");
}
```

- [x] **Step 2: Implement controller**

```java
@RestController
@RequestMapping("/api/v1/stores/{storeId}/payments/proof-review")
public class PaymentProofReviewController {
    private static final String PROOF_REVIEW_PERMISSION = "payment.proof.review";

    private final PaymentProofReviewService service;
    private final CurrentActorProvider currentActorProvider;

    @GetMapping("/candidates")
    @RequireAppGate(appKey = "payment", permission = PROOF_REVIEW_PERMISSION)
    public ResponseEntity<PaymentProofReviewResponses.CandidatesResponse> candidates(
        @PathVariable UUID storeId,
        @RequestParam(required = false) LocalDate businessDate,
        @RequestParam(required = false) String terminalCode,
        @RequestParam(defaultValue = "80") int limit
    ) {
        CurrentActor actor = requireActor(storeId);
        StoreScope scope = scope(actor, storeId);
        return ResponseEntity.ok(PaymentProofReviewResponses.CandidatesResponse.from(
            service.findCandidates(scope, businessDate, terminalCode, limit, actor)
        ));
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAppGate(appKey = "payment", permission = PROOF_REVIEW_PERMISSION)
    public ResponseEntity<PaymentProofReviewResponses.ScanResponse> scanProof(
        @PathVariable UUID storeId,
        @ModelAttribute PaymentProofReviewRequests.ScanProofForm form
    ) {
        CurrentActor actor = requireActor(storeId);
        StoreScope scope = scope(actor, storeId);
        return ResponseEntity.ok(PaymentProofReviewResponses.ScanResponse.from(
            service.scanAndMatch(scope, form.toCommand(), actor)
        ));
    }
}
```

- [x] **Step 3: Add error mapping**

Add `PAYMENT_OCR_UNAVAILABLE` to `PaymentServiceErrorCode` and `PaymentApiErrorCode`, mapping to HTTP `400`.

Map service errors:

```java
case PAYMENT_OCR_UNAVAILABLE -> PaymentApiErrorCode.PAYMENT_OCR_UNAVAILABLE;
case PAYMENT_INTENT_STATE_CONFLICT -> PaymentApiErrorCode.PAYMENT_INTENT_STATE_CONFLICT;
```

- [x] **Step 4: Run controller tests**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewControllerTest" test
```

Expected: PASS.

## Task 7: Mobile Proof Review UI

**Files:**
- Create: `src/pages/PaymentProofReviewPage.vue`
- Modify: `src/api/paymentApi.ts`
- Modify: `src/types/payment.ts`
- Modify: `src/router/index.ts`
- Modify: `src/components/tenant-admin/TenantAdminNav.vue`
- Modify: `src/components/staff/staffBottomNavItems.ts`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`

**Interfaces:**
- Consumes: proof review candidate and scan APIs.
- Produces: mobile-first route `/stores/:storeId/payments/proof-review`.
- Produces: tenant admin product-line child link to the same operational review page.

- [x] **Step 1: Extend frontend API types**

Add to `src/types/payment.ts`:

```ts
export interface PaymentProofReviewCandidate {
  intentId: string
  sessionId: string
  intentNo: string
  sessionNo: string
  displayNumber: number
  businessDate: string
  paymentReference: string
  amount: string
  currency: string
  intentStatus: string
  sessionStatus: string
  terminalCode: string | null
  cashierName: string | null
  createdAt: string
  expiresAt: string
}

export interface PaymentProofReviewOcr {
  extractedReference: string | null
  extractedAmount: string | null
  bankCode: string | null
  successDetected: boolean
  confidence: string | null
}

export interface PaymentProofReviewScanResponse {
  success: true
  outcome: 'auto_confirmed' | 'needs_review' | 'no_match'
  replayed: boolean
  intentId: string | null
  sessionId: string | null
  proofId: string | null
  verificationId: string | null
  paymentReference: string | null
  expectedAmount: string | null
  ocr: PaymentProofReviewOcr
  checks: {
    reference: string
    amount: string
  }
}

export interface PaymentProofReviewCandidatesResponse {
  success: true
  businessDate: string
  candidates: PaymentProofReviewCandidate[]
}
```

- [x] **Step 2: Extend `paymentApi.ts`**

```ts
export async function getPaymentProofReviewCandidates(
  storeId: string,
  query: { businessDate?: string; terminalCode?: string; limit?: number } = {},
  fetcher?: PaymentFetcher
): Promise<PaymentProofReviewCandidatesResponse> {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    const text = String(value ?? '').trim()
    if (text) params.set(key, text)
  })
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return requestJson(`${proofReviewEndpoint(storeId)}/candidates${suffix}`, { method: 'GET', fetcher })
}

export async function scanPaymentProofReview(
  storeId: string,
  form: {
    image: File
    idempotencyKey: string
    businessDate?: string
    terminalCode?: string
  },
  fetcher?: PaymentFetcher
): Promise<PaymentProofReviewScanResponse> {
  const body = new FormData()
  body.append('image', form.image)
  body.append('idempotencyKey', form.idempotencyKey)
  if (form.businessDate) body.append('businessDate', form.businessDate)
  if (form.terminalCode) body.append('terminalCode', form.terminalCode)
  return requestMultipart(`${proofReviewEndpoint(storeId)}/scan`, { method: 'POST', body, fetcher })
}

function proofReviewEndpoint(storeId: string): string {
  return `/api/v1/stores/${encodeURIComponent(storeId)}/payments/proof-review`
}
```

Add `requestMultipart` that omits `Content-Type` so the browser sets the boundary.

- [x] **Step 3: Create mobile-first page**

`PaymentProofReviewPage.vue` must include:

- business date filter.
- terminal code filter.
- candidate cards showing display number, Ref, amount, and status.
- camera/file input with `accept="image/png,image/jpeg,image/webp"` and `capture="environment"`.
- result states: `auto_confirmed`, `needs_review`, `no_match`.
- refresh after `auto_confirmed`.
- App Gate error handling with `formatAppGateErrorMessage`.
- no visible instructional paragraphs that explain UI features beyond necessary labels and statuses.

Core script:

```ts
const candidates = ref<PaymentProofReviewCandidate[]>([])
const scanResult = ref<PaymentProofReviewScanResponse | null>(null)
const scanning = ref(false)

async function onProofSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || scanning.value) return
  scanning.value = true
  scanResult.value = null
  try {
    const response = await scanPaymentProofReview(storeId.value, {
      image: file,
      idempotencyKey: `proof-review-${Date.now()}-${crypto.randomUUID()}`,
      businessDate: filters.businessDate,
      terminalCode: filters.terminalCode
    })
    scanResult.value = response
    if (response.outcome === 'auto_confirmed') {
      await loadCandidates()
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    scanning.value = false
    input.value = ''
  }
}
```

- [x] **Step 4: Add routes and navigation**

In `src/router/index.ts`:

```ts
const PaymentProofReviewPage = () => import('../pages/PaymentProofReviewPage.vue')
```

Route:

```ts
{
  path: '/stores/:storeId/payments/proof-review',
  name: 'payment-proof-review',
  component: PaymentProofReviewPage
}
```

In `TenantAdminNav.vue`, add child:

```ts
{ to: `/stores/${storeId.value}/payments/proof-review`, labelKey: 'nav.tenant.paymentProofReview' }
```

In staff bottom navigation, add proof review only when `payment.proof.review` is available:

```ts
{
  key: 'payment-proof-review',
  appKey: 'payment',
  permission: 'payment.proof.review',
  routeName: 'payment-proof-review',
  labelKey: 'nav.staff.paymentProofReview'
}
```

- [x] **Step 5: Add UI acceptance assertions**

Extend `PayNowPaymentUiAcceptanceValidationTest`:

```java
assertThat(router)
    .contains("PaymentProofReviewPage")
    .contains("path: '/stores/:storeId/payments/proof-review'")
    .contains("name: 'payment-proof-review'");

assertThat(tenantNav)
    .contains("/payments/proof-review")
    .contains("nav.tenant.paymentProofReview");

assertThat(api)
    .contains("getPaymentProofReviewCandidates")
    .contains("scanPaymentProofReview")
    .contains("/payments/proof-review")
    .contains("FormData");

assertThat(types)
    .contains("PaymentProofReviewCandidate")
    .contains("PaymentProofReviewScanResponse");
```

- [x] **Step 6: Run frontend checks**

Run:

```powershell
mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
npm run build
```

Expected: PASS.

## Task 8: End-To-End Verification And Release Note

**Files:**
- Create: `docs/release-notes/2026-08-08-paynow-payment-proof-review.md`
- Modify: `docs/superpowers/plans/2026-08-08-paynow-payment-proof-review.md` to check off completed items during execution.

**Interfaces:**
- Produces: verification evidence and release note.

- [x] **Step 1: Run targeted backend tests**

```powershell
mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,PaymentProofReviewServiceTest,PaymentProofReviewControllerTest,PaymentProofReviewMigrationTest,PaymentIntentServiceTest,PaymentIntentControllerTest,PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: PASS.

- [x] **Step 2: Run migration validation with local PostgreSQL**

Read:

```powershell
Get-Content -Raw target/local-postgres-current.txt
```

Use the pointer port with:

```text
jdbc:postgresql://127.0.0.1:<port>/postgres?stringtype=unspecified
username postgres
blank password
```

Run the existing migration test class that applies all Flyway migrations, including V053:

```powershell
mvn -q "-Dtest=PaymentMigrationTest,PaymentProofReviewMigrationTest" test
```

Expected: PASS.

- [x] **Step 3: Run frontend production build**

```powershell
npm run build
```

Expected: PASS.

- [x] **Step 4: Manual smoke path**

Use a payment-enabled store and staff account with `payment.proof.review`:

1. Open `/stores/{storeId}/payments`.
2. Create a Quick Pay amount `SGD 0.10`.
3. Complete PayNow from a bank app with the QR-generated Ref.
4. Open `/stores/{storeId}/payments/proof-review` on employee mobile.
5. Upload or capture the bank success screenshot.
6. Confirm response is `auto_confirmed`.
7. Confirm the candidate card disappears from pending list or shows paid after refresh.
8. Open Quick Payment Records and confirm the record status is `paid`.
9. Upload a screenshot where Ref matches but amount differs; confirm response is `needs_review`, not paid.
10. Upload a screenshot containing only bank transaction id; confirm response is `no_match`, not paid.

- [x] **Step 5: Apply RPB review skills**

Review against:

- `docs/skills/api-review/SKILL.md`
- `docs/skills/database-review/SKILL.md`
- `docs/skills/tdd-review/SKILL.md`
- `docs/skills/code-review/SKILL.md`
- `docs/skills/release-note/SKILL.md`
- `docs/skills/production-readiness/SKILL.md` if deployment is requested.

Required result: no P0/P1 blockers before final delivery.

- [x] **Step 6: Write release note**

Create `docs/release-notes/2026-08-08-paynow-payment-proof-review.md`:

```markdown
# PayNow Payment Proof Review

## Summary

Adds a mobile-friendly PayNow proof review flow that reads bank receipt screenshots and auto-confirms a Quick Pay payment only when the RPB-generated Ref and amount match the pending payment intent.

## Included

- Proof Review route under the PayNow product line.
- `payment.proof.review` permission.
- OCR adapter boundary and Tesseract CLI implementation.
- Exact Ref extraction for `PREFIX-YYYYMM-SEQUENCE[-CHECK]` references.
- Amount matching with `BigDecimal` and `0.01` tolerance.
- Automatic paid transition for exact Ref plus amount matches.
- Review state for Ref match with missing or mismatched amount.

## Not Included

- Bank statement CSV reconciliation.
- Refunds.
- Non-PayNow providers.
- Automatic matching from bank APIs.

## Verification

- `mvn -q "-Dtest=PaymentReferencePatternTest,PaymentReferenceGeneratorTest,PaymentProofReviewServiceTest,PaymentProofReviewControllerTest,PaymentProofReviewMigrationTest,PaymentIntentServiceTest,PaymentIntentControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`
- `npm run build`
```

## Self-Review

Spec coverage:

- Third PayNow submodule: Tasks 1, 6, and 7.
- Mobile employee flow: Task 7.
- RPB-generated complete Ref, not fixed PIT: Task 2.
- `PIT-*`, `QP-*`, and future prefixes: Task 2.
- Ref plus amount exact quick validation: Task 4.
- Avoid opening proofs one by one: Tasks 4, 6, and 7.
- Reuse `D:\payment_runtime` methods without runtime dependency: Tasks 3 and 4.
- OCR precision: Tasks 2, 3, and 4.
- API, DB, TDD, code review: Task 8.

Completeness scan:

- This plan contains no unresolved markers.

Type consistency:

- `PaymentReferencePattern`, `PaymentReferenceGenerator`, `PaymentProofOcrAdapter`, `PaymentProofReviewService`, `PaymentProofScanCommand`, `PaymentProofScanResult`, and `PaymentProofReviewController` are used consistently across tasks.

Execution handoff:

Plan complete and saved to `docs/superpowers/plans/2026-08-08-paynow-payment-proof-review.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.
