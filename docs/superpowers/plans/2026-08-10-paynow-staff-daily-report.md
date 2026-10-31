# PayNow Staff Daily Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted staff-facing daily PayNow report that defaults to current terminal plus current cashier and can switch to whole-terminal totals.

**Architecture:** Extend the existing Quick Pay records query with an optional `cashierName` filter and additive summary fields. Reuse the same endpoint from the Quick Payment page, rendering a compact report panel above the keypad and storing the selected report mode per store and terminal.

**Tech Stack:** Spring Boot 3.5, Java 21, JdbcTemplate/PostgreSQL, Vue 3, TypeScript, Vite, existing App Gate `payment.intent.view`.

## Global Constraints

- No database migration.
- No new App Gate permission.
- Only Quick Pay records with source type `quick_pay` are included.
- `paid` amount is the real collected amount.
- `pending` is waiting for payment.
- `awaiting_verification` is waiting for receipt verification confirmation.
- Default UI scope is current `terminalCode` plus current authenticated user's `username`.
- Alternate UI scope is current `terminalCode` for all cashiers.
- Report mode persists per `storeId` and `terminalCode`.

---

## File Structure

- Modify `src/main/java/com/rpb/reservation/payment/application/QuickPayRecordQuery.java` to add `cashierName`.
- Modify `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java` to normalize and pass `cashierName`.
- Modify `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java` to filter exact cashier name.
- Modify `src/main/java/com/rpb/reservation/payment/api/PaymentIntentController.java` to accept `cashierName`.
- Modify `src/main/java/com/rpb/reservation/payment/api/PaymentIntentResponses.java` to add summary counts and amounts.
- Modify `src/types/payment.ts` and `src/api/paymentApi.ts` for the additive query/summary fields.
- Modify `src/pages/PaymentQuickPayPage.vue` to load, render, refresh, and persist the report.
- Modify `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java` and `src/test/java/com/rpb/reservation/payment/api/PaymentIntentControllerTest.java`.
- Create `docs/api/PAYNOW_QUICK_PAYMENT_RECORDS_API_CONTRACT.md`.
- Create `docs/release-notes/2026-08-10-paynow-staff-daily-report.md`.

### Task 1: API Contract And Backend Test Surface

**Files:**
- Create: `docs/api/PAYNOW_QUICK_PAYMENT_RECORDS_API_CONTRACT.md`
- Modify: `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java`
- Modify: `src/test/java/com/rpb/reservation/payment/api/PaymentIntentControllerTest.java`

**Interfaces:**
- Produces: expected Java record constructor `new QuickPayRecordQuery(LocalDate businessDate, String status, String terminalCode, String cashierName, String search, int limit)`.
- Produces: controller method parameter order `getQuickPayRecords(UUID, LocalDate, String, String, String, String, int)`.

- [ ] **Step 1: Write the API contract**

Add `docs/api/PAYNOW_QUICK_PAYMENT_RECORDS_API_CONTRACT.md` with:

```markdown
# PayNow Quick Payment Records API Contract

## Endpoint

`GET /api/v1/stores/{storeId}/payments/intents/quick-pay-records`

Permission: App Gate `payment.intent.view`.

## Query

- `businessDate`: optional ISO date.
- `status`: optional, one of `pending`, `awaiting_verification`, `paid`, `expired`, `cancelled`, `failed`.
- `terminalCode`: optional exact terminal code after trimming.
- `cashierName`: optional exact cashier name after trimming.
- `q`: optional keyword search.
- `limit`: optional integer, default `80`, min `1`, max `200`.

## Summary

`paidAmount` is the real collected amount. `pendingAmount` and `awaitingVerificationAmount` are operational follow-up totals and are not collected revenue.
```

- [ ] **Step 2: Update service test for cashier normalization**

Change `findsQuickPayRecordsWithNormalizedQuery` to construct and assert:

```java
new QuickPayRecordQuery(
    LocalDate.parse("2026-08-05"),
    "PENDING",
    " T1 ",
    " Alice ",
    " QP-202608 ",
    500
)
```

and expected:

```java
new QuickPayRecordQuery(
    LocalDate.parse("2026-08-05"),
    "pending",
    "T1",
    "Alice",
    "QP-202608",
    200
)
```

- [ ] **Step 3: Update controller permission/signature test**

Change reflection for `getQuickPayRecords` to:

```java
PaymentIntentController.class.getMethod(
    "getQuickPayRecords",
    UUID.class,
    java.time.LocalDate.class,
    String.class,
    String.class,
    String.class,
    String.class,
    int.class
);
```

- [ ] **Step 4: Run tests to verify failure**

Run: `mvn -q "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest" test`

Expected: compile failure because `QuickPayRecordQuery` and controller signatures still use the old shape.

### Task 2: Backend Query And Summary Implementation

**Files:**
- Modify: `src/main/java/com/rpb/reservation/payment/application/QuickPayRecordQuery.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentController.java`
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentResponses.java`

**Interfaces:**
- Consumes: `QuickPayRecordQuery(LocalDate, String, String, String, String, int)`.
- Produces: summary fields `awaitingVerificationCount`, `pendingAmount`, `awaitingVerificationAmount`.

- [ ] **Step 1: Extend `QuickPayRecordQuery`**

```java
public record QuickPayRecordQuery(
    LocalDate businessDate,
    String status,
    String terminalCode,
    String cashierName,
    String search,
    int limit
) {
}
```

- [ ] **Step 2: Normalize `cashierName` in `PaymentIntentService`**

In default query construction use:

```java
new QuickPayRecordQuery(null, null, null, null, null, 80)
```

In the returned normalized query pass:

```java
trim(source.cashierName()),
trim(source.search()),
limit
```

- [ ] **Step 3: Add repository filter**

In `JdbcPaymentIntentRepository.findQuickPayRecords`, after `terminalCode`:

```java
if (!isBlank(query.cashierName())) {
    sql.append(" and s.cashier_name = ?");
    args.add(query.cashierName());
}
```

- [ ] **Step 4: Add controller query parameter**

Add `@RequestParam(required = false) String cashierName` before `search`, and construct:

```java
new QuickPayRecordQuery(
    businessDate,
    status,
    terminalCode,
    cashierName,
    search,
    limit
)
```

- [ ] **Step 5: Extend summary response**

Add `awaitingVerificationCount`, `pendingAmount`, and `awaitingVerificationAmount` to `QuickPayRecordSummary`, computing them by filtering `intentStatus`.

- [ ] **Step 6: Run backend tests**

Run: `mvn -q "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest" test`

Expected: PASS.

### Task 3: Frontend Report Panel

**Files:**
- Modify: `src/types/payment.ts`
- Modify: `src/api/paymentApi.ts`
- Modify: `src/pages/PaymentQuickPayPage.vue`

**Interfaces:**
- Consumes: `cashierName?: string` in `QuickPayRecordsQuery`.
- Consumes: summary fields from Task 2.
- Produces: Quick Pay report panel with persisted mode key `rpb.payment.quickPay.reportMode.{storeId}.{terminalCode}`.

- [ ] **Step 1: Update TypeScript payment types**

Add to `QuickPayRecordSummary`:

```ts
awaitingVerificationCount: number
pendingAmount: string
awaitingVerificationAmount: string
```

Add to `QuickPayRecordsQuery`:

```ts
cashierName?: string
```

- [ ] **Step 2: Add report state in `PaymentQuickPayPage.vue`**

Add:

```ts
type PaymentReportMode = 'mine' | 'terminal'

const reportMode = ref<PaymentReportMode>('mine')
const reportLoading = ref(false)
const reportErrorText = ref('')
const reportSummary = ref<QuickPayRecordSummary>({
  count: 0,
  pendingCount: 0,
  awaitingVerificationCount: 0,
  paidCount: 0,
  totalAmount: '0',
  pendingAmount: '0',
  awaitingVerificationAmount: '0',
  paidAmount: '0',
  currency: 'SGD'
})
```

- [ ] **Step 3: Implement persistence helpers**

Use:

```ts
function reportModeStorageKey(): string {
  return `rpb.payment.quickPay.reportMode.${storeId.value || 'unknown'}.${normalizedTerminalCode.value}`
}
```

Store only `mine` or `terminal`; invalid stored values fall back to `mine`.

- [ ] **Step 4: Implement report loading**

Call `getQuickPayRecords(storeId.value, { businessDate: displayedBusinessDate.value, terminalCode: normalizedTerminalCode.value, cashierName: reportMode.value === 'mine' ? cashierName.value || undefined : undefined, limit: 200 })`.

- [ ] **Step 5: Render report panel**

Place the panel below `.payment-options-shell`. Use three stable cards for paid, pending, and awaiting verification. Use compact buttons for `我的收款` and `${normalizedTerminalCode} 全线`.

- [ ] **Step 6: Refresh after payment state changes**

Call `void loadPaymentReport()` after successful `submitQuickPay`, after successful `confirmRecentPayment`, after `loadPaymentBusinessDay`, and when `storeId`, `normalizedTerminalCode`, `displayedBusinessDate`, or `reportMode` changes.

- [ ] **Step 7: Run frontend build**

Run: `npm run build`

Expected: PASS.

### Task 4: Release Notes, Review, And Full Validation

**Files:**
- Create: `docs/release-notes/2026-08-10-paynow-staff-daily-report.md`

**Interfaces:**
- Consumes: completed backend and frontend changes.
- Produces: release note with validation commands and rollback notes.

- [ ] **Step 1: Write release note**

Include:

```markdown
# PayNow Staff Daily Report

## Version / Date

- Date: 2026-08-10

## New

- Added a staff-facing daily PayNow report on Quick Payment.
- Staff default to current terminal plus their own cashier name and can switch to whole-terminal totals.

## Migration

- No database migration.

## Permission

- Reuses `payment.intent.view`.
```

- [ ] **Step 2: Run targeted backend validation**

Run: `mvn -q "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`

Expected: PASS.

- [ ] **Step 3: Run frontend validation**

Run: `npm run build`

Expected: PASS.

- [ ] **Step 4: Run diff hygiene**

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 5: Review changed files**

Check:

```powershell
git diff --stat
git diff -- src\main\java\com\rpb\reservation\payment src\api\paymentApi.ts src\types\payment.ts src\pages\PaymentQuickPayPage.vue docs
```

Expected: changes stay within the PayNow staff report scope.
