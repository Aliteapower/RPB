# PayNow OCR-Safe Reference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate new PayNow payment references in an OCR-safe compact format while keeping old separated references matchable in Payment Proof Review.

**Architecture:** Keep the change inside the existing payment module. `PaymentReferenceGenerator` owns new compact reference creation, `PaymentReferencePattern` owns extraction and normalization for old and new shapes, and existing payment intent / proof review services continue comparing stored candidate references after normalization. No database, API, permission, or PayNow QR payload schema changes are required.

**Tech Stack:** Java 21, Spring Boot 3.5.15, existing payment module, JUnit 5, AssertJ, Vue/Vite build validation, existing PayNow Proof Review OCR flow.

## Global Constraints

- New references use compact separator-free shape: `<PREFIX><YYYYMM><SEQUENCE4><CHECK4>`.
- Prefix remains existing uppercase 2 to 8 character product/channel prefix.
- Period remains `yyyyMM`.
- Sequence is fixed-width 4 digits for the current Quick Pay display-number workflow.
- Check segment uses only `ACDEFGHJKMNPQRTVWXY`.
- Check segment excludes `O`, `I`, `L`, `B`, `S`, `Z`, and all digits.
- Existing old references such as `QP-202608-0013-2KZ6`, `QP-202608.0013.2KZ6`, `QP 202608 0013 2KZ6`, and `PIT-202608-0021` remain accepted.
- Do not rewrite existing payment references.
- Do not add database migrations.
- Do not add tenant settings.
- Do not change PayNow QR EMV payload structure; only the reference value changes.
- Do not apply broad OCR character correction such as global `O -> 0` or `I -> 1`.
- Keep tenant and store isolation unchanged.
- Controllers must continue calling application services, not repositories.
- Monetary matching remains exact to the existing proof-review amount rules.
- Before runtime or migration validation, use `target/local-postgres-current.txt` according to `AGENTS.md`.

---

## File Structure

- Modify: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferenceGenerator.java`
  - Owns compact reference creation and OCR-safe checksum alphabet.
- Modify: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferenceGeneratorTest.java`
  - Verifies compact shape, safe alphabet, determinism, and existing prefix validation.
- Modify: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferencePattern.java`
  - Extracts and normalizes compact references and legacy separated references.
- Modify: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferencePatternTest.java`
  - Verifies compact extraction, OCR separator insertion, legacy compatibility, and bank transaction ID rejection.
- Modify: `src/test/java/com/rpb/reservation/payment/provider/TesseractPaymentProofOcrAdapterTest.java`
  - Verifies OCR parser can extract compact references from receipt-like text and separator-noisy OCR text.
- Modify: `src/test/java/com/rpb/reservation/payment/application/PaymentProofReviewServiceTest.java`
  - Verifies proof review matches compact references and legacy references through normalized comparison.
- Modify: `docs/release-notes/2026-08-08-paynow-payment-proof-review.md`
  - Records the OCR-safe reference change and validation evidence.

---

### Task 1: Compact OCR-Safe Reference Generation

**Files:**
- Modify: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferenceGeneratorTest.java`
- Modify: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferenceGenerator.java`

**Interfaces:**
- Consumes: `PaymentReferenceGenerator.generate(String prefix, YearMonth period, int sequence)`.
- Produces: compact references shaped like `QP2026080040ACDE`.
- Produces: private checksum alphabet `ACDEFGHJKMNPQRTVWXY`.

- [ ] **Step 1: Write failing generator tests**

Update `PaymentReferenceGeneratorTest` with these tests:

```java
@Test
void generatesCompactReferenceWithOcrSafeCheckSegment() {
    String result = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 40);

    assertThat(result).matches("QP2026080040[ACDEFGHJKMNPQRTVWXY]{4}");
    assertThat(result).doesNotContain("-", ".", "O", "I", "L", "B", "S", "Z");
}

@Test
void supportsPitPrefixForCompactReference() {
    String result = PaymentReferenceGenerator.generate("PIT", YearMonth.of(2026, 8), 21);

    assertThat(result).matches("PIT2026080021[ACDEFGHJKMNPQRTVWXY]{4}");
}

@Test
void compactReferenceIsDeterministicForSameInputs() {
    String first = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 13);
    String second = PaymentReferenceGenerator.generate("qp", YearMonth.of(2026, 8), 13);

    assertThat(second).isEqualTo(first);
}
```

Keep the existing invalid-prefix test.

- [ ] **Step 2: Run generator tests and verify failure**

Run:

```powershell
mvn -q "-Dtest=PaymentReferenceGeneratorTest" test
```

Expected: FAIL because generated references still include `-` and base36 checksum characters.

- [ ] **Step 3: Implement compact generator**

Update `PaymentReferenceGenerator`:

```java
private static final char[] CHECK_ALPHABET = "ACDEFGHJKMNPQRTVWXY".toCharArray();
```

Change `generate` so the base and return value are compact:

```java
String sequenceText = "%04d".formatted(sequence);
String base = cleanPrefix + period.format(PERIOD_FORMATTER) + sequenceText;
return base + checksum(base);
```

Change `checksum` to use `CHECK_ALPHABET`:

```java
private static String checksum(String base) {
    CRC32 crc = new CRC32();
    crc.update(base.getBytes(StandardCharsets.UTF_8));
    long value = crc.getValue();
    char[] out = new char[4];
    for (int i = 3; i >= 0; i--) {
        out[i] = CHECK_ALPHABET[(int) (value % CHECK_ALPHABET.length)];
        value = value / CHECK_ALPHABET.length;
    }
    return new String(out);
}
```

- [ ] **Step 4: Run generator tests and verify pass**

Run:

```powershell
mvn -q "-Dtest=PaymentReferenceGeneratorTest" test
```

Expected: PASS.

- [ ] **Step 5: Review impact before moving on**

Run:

```powershell
git diff -- src\main\java\com\rpb\reservation\payment\domain\PaymentReferenceGenerator.java src\test\java\com\rpb\reservation\payment\domain\PaymentReferenceGeneratorTest.java
```

Expected: only generator and generator tests changed.

---

### Task 2: Compact And Legacy Reference Extraction

**Files:**
- Modify: `src/test/java/com/rpb/reservation/payment/domain/PaymentReferencePatternTest.java`
- Modify: `src/main/java/com/rpb/reservation/payment/domain/PaymentReferencePattern.java`
- Modify: `src/test/java/com/rpb/reservation/payment/provider/TesseractPaymentProofOcrAdapterTest.java`

**Interfaces:**
- Consumes: `PaymentReferencePattern.extractSystemReference(String rawText)`.
- Consumes: `PaymentReferencePattern.normalize(String value)`.
- Produces: compact extraction while preserving old separated reference extraction.

- [ ] **Step 1: Write failing pattern tests**

Add to `PaymentReferencePatternTest`:

```java
@Test
void extractsCompactOcrSafeReferenceFromChineseBankReceiptText() {
    String raw = """
        您已支付 1.00 SGD
        讯息
        QP2026080013ACDE
        交易编号：2608080118181271
        """;

    assertThat(PaymentReferencePattern.extractSystemReference(raw))
        .contains("QP2026080013ACDE");
}

@Test
void extractsCompactReferenceWhenOcrAddsSeparators() {
    assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP2026080013.ACDE 您已支付 1.00 SGD"))
        .contains("QP2026080013ACDE");
    assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP 202608 0013 ACDE 您已支付 1.00 SGD"))
        .contains("QP2026080013ACDE");
    assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP.202608.0013.ACDE 您已支付 1.00 SGD"))
        .contains("QP2026080013ACDE");
}

@Test
void normalizesCompactReferenceSeparators() {
    assertThat(PaymentReferencePattern.normalize(" qp.202608.0013.acde "))
        .isEqualTo("QP2026080013ACDE");
}
```

Keep existing tests for old `PIT-202608-0021`, `QP-202608-0040-87D0`, long bank transaction IDs, and legacy normalization.

- [ ] **Step 2: Write failing OCR adapter parser tests**

Add to `TesseractPaymentProofOcrAdapterTest`:

```java
@Test
void parserExtractsCompactReferenceFromReceiptText() {
    String raw = """
        您已支付 1.00 SGD
        讯息
        QP2026080013ACDE
        交易编号：2608080118181271
        """;

    assertThat(PaymentReferencePattern.extractSystemReference(raw))
        .contains("QP2026080013ACDE");
}

@Test
void parserExtractsCompactReferenceWhenOcrAddsDotsAndSpaces() {
    assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP.202608.0013.ACDE 您已支付 1.00 SGD"))
        .contains("QP2026080013ACDE");
    assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP 202608 0013 ACDE 您已支付 1.00 SGD"))
        .contains("QP2026080013ACDE");
}
```

- [ ] **Step 3: Run parser tests and verify failure**

Run:

```powershell
mvn -q "-Dtest=PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest" test
```

Expected: FAIL because compact references are not extracted yet.

- [ ] **Step 4: Implement compact extraction**

Update `PaymentReferencePattern` by adding compact patterns before legacy extraction:

```java
private static final String OCR_SAFE_CHECK = "[ACDEFGHJKMNPQRTVWXY]{4}";
private static final Pattern COMPACT_SYSTEM_REFERENCE = Pattern.compile(
    "\\b([A-Z0-9]{2,8}\\d{6}\\d{4}" + OCR_SAFE_CHECK + ")\\b",
    Pattern.CASE_INSENSITIVE
);
private static final Pattern COMPACT_SYSTEM_REFERENCE_WITH_OCR_SEPARATORS = Pattern.compile(
    "\\b([A-Z0-9]{2,8}(?:\\s*[-.]?\\s*)\\d{6}(?:\\s*[-.]?\\s*)\\d{4}(?:\\s*[-.]?\\s*)[ACDEFGHJKMNPQRTVWXY](?:\\s*[ACDEFGHJKMNPQRTVWXY]){3})\\b",
    Pattern.CASE_INSENSITIVE
);
```

Change extraction order:

```java
return extractWith(COMPACT_SYSTEM_REFERENCE_WITH_OCR_SEPARATORS, normalizedText)
    .or(() -> extractWith(COMPACT_SYSTEM_REFERENCE, normalizedText))
    .or(() -> extractWith(SYSTEM_REFERENCE_WITH_OCR_SPACES, normalizedText))
    .or(() -> extractWith(SYSTEM_REFERENCE, normalizedText));
```

Change `normalize` so compact references lose separators but legacy references keep hyphens:

```java
public static String normalize(String value) {
    String text = value == null ? "" : value.trim().toUpperCase();
    text = normalizeHyphens(text);
    String separatorNormalized = text.replaceAll("\\s*[-.]\\s*", "-").replaceAll("\\s+", "");
    separatorNormalized = separatorNormalized.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
    String compactCandidate = separatorNormalized.replace("-", "");
    if (COMPACT_SYSTEM_REFERENCE.matcher(compactCandidate).matches()) {
        return compactCandidate;
    }
    return separatorNormalized;
}
```

- [ ] **Step 5: Run parser tests and verify pass**

Run:

```powershell
mvn -q "-Dtest=PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest" test
```

Expected: PASS.

---

### Task 3: Proof Review Matching With Compact References

**Files:**
- Modify: `src/test/java/com/rpb/reservation/payment/application/PaymentProofReviewServiceTest.java`
- Inspect: `src/main/java/com/rpb/reservation/payment/application/PaymentProofReviewService.java`
- Inspect: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofReviewRepository.java`

**Interfaces:**
- Consumes: `PaymentProofReviewService.scanAndMatch(StoreScope, PaymentProofScanCommand, CurrentActor)`.
- Consumes: repository method `findActiveCandidateByReference(StoreScope scope, String paymentReference, LocalDate businessDate, String terminalCode)`.
- Produces: proof review auto-confirm behavior for compact references.

- [ ] **Step 1: Write failing service test for compact reference**

Add to `PaymentProofReviewServiceTest`:

```java
@Test
void autoConfirmsCompactReferenceWhenExtractedReferenceAndAmountMatch() {
    PaymentProofCandidate candidate = candidate("QP2026080013ACDE", new BigDecimal("1.00"));
    repository.candidate = Optional.of(candidate);
    ocr.fields = new PaymentProofOcrFields(
        "QP2026080013ACDE",
        new BigDecimal("1.00"),
        null,
        "ocbc",
        true,
        new BigDecimal("0.9600"),
        "讯息 QP2026080013ACDE 您已支付 1.00 SGD",
        "{}"
    );

    PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-compact-001"), actor);

    assertThat(result.outcome()).isEqualTo("auto_confirmed");
    assertThat(result.paymentReference()).isEqualTo("QP2026080013ACDE");
    assertThat(repository.confirmedIntentId).isEqualTo(candidate.intentId());
    assertThat(repository.createdProofStatus).isEqualTo("confirmed");
    assertThat(repository.createdVerificationStatus).isEqualTo("confirmed");
}
```

- [ ] **Step 2: Write service test for separator-noisy compact OCR**

Add:

```java
@Test
void autoConfirmsCompactReferenceWhenOcrAddsSeparators() {
    PaymentProofCandidate candidate = candidate("QP2026080013ACDE", new BigDecimal("1.00"));
    repository.candidate = Optional.of(candidate);
    ocr.fields = new PaymentProofOcrFields(
        "QP.202608.0013.ACDE",
        new BigDecimal("1.00"),
        null,
        "ocbc",
        true,
        new BigDecimal("0.9200"),
        "讯息 QP.202608.0013.ACDE 您已支付 1.00 SGD",
        "{}"
    );

    PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-compact-002"), actor);

    assertThat(result.outcome()).isEqualTo("auto_confirmed");
    assertThat(repository.confirmedIntentId).isEqualTo(candidate.intentId());
}
```

- [ ] **Step 3: Run service tests**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewServiceTest" test
```

Expected after Task 2: PASS if service already normalizes extracted and candidate references. If it fails, continue to Step 4.

- [ ] **Step 4: Apply minimal service or repository fix only if Step 3 fails**

If the in-memory repository test fails because the fake repository compares raw strings, update the test fake method:

```java
return candidate.filter(value ->
    PaymentReferencePattern.normalize(value.paymentReference())
        .equals(PaymentReferencePattern.normalize(paymentReference))
);
```

If production repository uses raw equality, keep it because it receives normalized extracted references and stored compact references exactly for new records. Do not add SQL `replace` expressions unless a test proves production code cannot find active compact candidates.

- [ ] **Step 5: Run service tests and verify pass**

Run:

```powershell
mvn -q "-Dtest=PaymentProofReviewServiceTest" test
```

Expected: PASS.

---

### Task 4: Release Note And UI Acceptance Guard

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PayNowPaymentUiAcceptanceValidationTest.java`
- Modify: `docs/release-notes/2026-08-08-paynow-payment-proof-review.md`

**Interfaces:**
- Consumes: existing Proof Review UI acceptance test.
- Produces: release note section documenting compact OCR-safe references.

- [ ] **Step 1: Inspect current UI acceptance coverage**

Run:

```powershell
rg -n "payment-proof-review|photoInputRef|albumInputRef|PaymentReferenceGenerator|paymentReference" src\test\java\com\rpb\reservation\appgate\ui\PayNowPaymentUiAcceptanceValidationTest.java
```

Expected: proof review route, scan/upload controls, and generated i18n keys remain covered. No UI text change is required by this reference-format change.

- [ ] **Step 2: Add a generator usage guard if missing**

If `PayNowPaymentUiAcceptanceValidationTest` does not assert that Quick Pay creation uses the generator indirectly, add a lightweight source assertion:

```java
assertThat(Files.readString(Path.of("src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java")))
    .contains("PaymentReferenceGenerator.generate");
```

If this assertion already exists elsewhere, do not duplicate it.

- [ ] **Step 3: Append release-note section**

Append to `docs/release-notes/2026-08-08-paynow-payment-proof-review.md`:

```markdown
## 2026-08-08 OCR-Safe Reference Format Patch

- New PayNow payment references use compact separator-free format such as `QP2026080013ACDE`.
- The 4-character check segment uses OCR-safe letters from `ACDEFGHJKMNPQRTVWXY`.
- The check segment excludes `O`, `I`, `L`, `B`, `S`, `Z`, and digits to reduce screen-to-screen OCR confusion.
- Existing separated references remain accepted by Payment Proof Review.
- No database migration, permission change, or PayNow QR payload schema change is required.
```

- [ ] **Step 4: Run UI acceptance test**

Run:

```powershell
mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: PASS.

---

### Task 5: Final Verification And Commit

**Files:**
- Review all files changed in Tasks 1-4.

**Interfaces:**
- Consumes: generator, parser, OCR adapter parser tests, service tests, UI acceptance test, frontend build.
- Produces: one implementation commit ready for deployment.

- [ ] **Step 1: Run focused backend verification**

Run:

```powershell
mvn -q "-Dtest=PaymentReferenceGeneratorTest,PaymentReferencePatternTest,TesseractPaymentProofOcrAdapterTest,PaymentProofReviewServiceTest,PaymentProofReviewControllerTest,PayNowPaymentUiAcceptanceValidationTest" test
```

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run diff checks**

Run:

```powershell
git diff --check
git diff --stat
git status --short
```

Expected: no whitespace errors; only planned files changed.

- [ ] **Step 4: Apply RPB review skills**

Use:

```text
docs/skills/tdd-review/SKILL.md
docs/skills/code-review/SKILL.md
docs/skills/release-note/SKILL.md
```

Expected decisions:

- TDD review: tests cover new generation, parsing, OCR extraction, proof-review matching, legacy compatibility.
- Code review: approve if no API, DB, permission, tenant isolation, or controller boundary changes are introduced.
- Release note: no migration, no permission change, rollback via previous jar/frontend backup.

- [ ] **Step 5: Commit implementation**

Run:

```powershell
git add src\main\java\com\rpb\reservation\payment\domain\PaymentReferenceGenerator.java `
  src\main\java\com\rpb\reservation\payment\domain\PaymentReferencePattern.java `
  src\test\java\com\rpb\reservation\payment\domain\PaymentReferenceGeneratorTest.java `
  src\test\java\com\rpb\reservation\payment\domain\PaymentReferencePatternTest.java `
  src\test\java\com\rpb\reservation\payment\provider\TesseractPaymentProofOcrAdapterTest.java `
  src\test\java\com\rpb\reservation\payment\application\PaymentProofReviewServiceTest.java `
  src\test\java\com\rpb\reservation\appgate\ui\PayNowPaymentUiAcceptanceValidationTest.java `
  docs\release-notes\2026-08-08-paynow-payment-proof-review.md
git commit -m "fix: use ocr safe paynow references"
```

Expected: one implementation commit.

- [ ] **Step 6: Push branch**

Run:

```powershell
git push origin codex/paynow-payment-product-line-staging
```

Expected: push succeeds.

---

## Self-Review

- Spec coverage: compact format, OCR-safe alphabet, legacy compatibility, no migration, no tenant setting, no QR schema change, and rollback notes are covered.
- Placeholder scan: no unresolved markers or vague implementation placeholders remain.
- Type consistency: plan uses existing `PaymentReferenceGenerator.generate`, `PaymentReferencePattern.extractSystemReference`, `PaymentReferencePattern.normalize`, and `PaymentProofReviewService.scanAndMatch` signatures.
- Scope check: this plan changes payment reference generation and parsing only; it does not add new UI workflows, permissions, migrations, or APIs.
