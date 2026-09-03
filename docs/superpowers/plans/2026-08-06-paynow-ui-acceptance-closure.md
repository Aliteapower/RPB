# PayNow UI Acceptance Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the tenant PayNow settings page, staff quick-pay calculator page, and authenticated QR display page needed to close PayNow production acceptance.

**Architecture:** Reuse the existing RPB-native `payment` backend module and App Gate. Add one read-only session lookup API so the standalone display route can reload safely. Build focused Vue API/type/page files that follow existing staff and tenant-admin route patterns.

**Tech Stack:** Java Spring Boot, PostgreSQL through existing JDBC repositories, Vue 3, vue-router, vue-i18n, existing `DownloadableQrCode`.

## Global Constraints

- Do not run `D:\payment_runtime` as a sidecar.
- Keep PayNow runtime inside the RPB-native `payment` product line.
- Do not add public unauthenticated QR display routes in this phase.
- Use `/api/v1` store-scoped endpoints and existing App Gate permissions.
- Do not create new database migrations for this UI closure.

---

### Task 1: Read-Only Session Lookup API

**Files:**
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/PaymentIntentRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentIntentRepository.java`
- Modify: `src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java`
- Modify: `src/main/java/com/rpb/reservation/payment/api/PaymentIntentController.java`
- Test: `src/test/java/com/rpb/reservation/payment/api/PaymentIntentControllerTest.java`
- Test: `src/test/java/com/rpb/reservation/payment/application/PaymentIntentServiceTest.java`

**Interfaces:**
- Produces: `PaymentIntentService.findSessionByNo(StoreScope scope, String sessionNo, CurrentActor actor): PaymentSession`.
- Produces: `GET /api/v1/stores/{storeId}/payments/intents/sessions/{sessionNo}` returning `PaymentIntentResponses.SessionResponse`.

- [ ] **Step 1: Add failing permission and contract assertions**
  - Assert `PaymentIntentController#getSession` exists.
  - Assert it is annotated with `@GetMapping("/sessions/{sessionNo}")`.
  - Assert it is annotated with `@RequireAppGate(appKey = "payment", permission = "payment.intent.view")`.

- [ ] **Step 2: Add service/repository lookup contract**
  - Add repository method `findSessionByNo(StoreScope, String)`.
  - Add service test that returns a stored session for the same tenant/store.
  - Add service test that rejects actors outside the store scope.

- [ ] **Step 3: Implement API and repository**
  - Query `payment_sessions` by `tenant_id`, `store_id`, and `session_no`.
  - Return `PAYMENT_SESSION_NOT_FOUND` when missing.
  - Keep response DTO as `PaymentIntentResponses.SessionResponse`.

- [ ] **Step 4: Verify**
  - Run `mvn -Dtest=PaymentIntentControllerTest,PaymentIntentServiceTest test`.

### Task 2: Frontend Payment API Types

**Files:**
- Create: `src/types/payment.ts`
- Create: `src/api/paymentApi.ts`

**Interfaces:**
- Produces: `getPaymentProfile(storeId)`, `updatePaymentProfile(storeId, request)`, `createQuickPayIntent(storeId, request)`, `getPaymentSession(storeId, sessionNo)`.

- [ ] **Step 1: Define explicit TypeScript contracts**
  - Mirror existing backend response shape for profile, intent, and session.
  - Represent `qrPayloadsJson` as a string returned by the backend.

- [ ] **Step 2: Implement fetch wrapper**
  - Use `credentials: 'include'`.
  - Throw `PaymentApiError` for backend error responses.
  - Support `GET`, `PATCH`, and `POST`.

### Task 3: Tenant Admin Payment Settings Page

**Files:**
- Create: `src/pages/TenantAdminPaymentSettingsPage.vue`
- Modify: `src/router/index.ts`
- Modify: `src/components/tenant-admin/TenantAdminNav.vue`

**Interfaces:**
- Consumes: `getPaymentProfile`, `updatePaymentProfile`.
- Produces route: `/stores/:storeId/admin/payment/settings`.

- [ ] **Step 1: Add page**
  - Load profile on mount.
  - Show missing profile as editable default form.
  - Save PayNow mobile/UEN, merchant name, currency `SGD`, status active/disabled, and version.

- [ ] **Step 2: Add route and nav**
  - Add tenant admin route with `requiresTenantAdmin`.
  - Add nav item labeled directly as `PayNow`.

### Task 4: Staff Quick Pay Page And QR Display Page

**Files:**
- Create: `src/pages/PaymentQuickPayPage.vue`
- Create: `src/pages/PaymentDisplayPage.vue`
- Modify: `src/router/index.ts`
- Modify: `src/components/staff/staffBottomNavItems.ts`
- Modify: `src/pages/StoreStaffHomePage.vue`

**Interfaces:**
- Consumes: `createQuickPayIntent`, `getPaymentSession`.
- Produces route: `/stores/:storeId/payments`.
- Produces route: `/stores/:storeId/payments/display/:sessionNo`.

- [ ] **Step 1: Build quick calculator**
  - Numeric amount entry.
  - Quick amount buttons.
  - Create intent with `sourceType = quick_pay`, `method = paynow`, `currency = SGD`, generated idempotency key.
  - Show QR inline after success and link to display page.

- [ ] **Step 2: Build display page**
  - Fetch session by `sessionNo`.
  - Parse `qrPayloadsJson.payloads.paynow.payload`.
  - Show QR, display number, expiry, and status.

- [ ] **Step 3: Add entries**
  - Add bottom nav `payment` tab.
  - Add staff home operation tile when actor has `payment.intent.create`.

### Task 5: Validation, Review, And Release Notes

**Files:**
- Modify: `docs/release-notes/2026-08-05-paynow-payment-product-line-foundation.md`

**Interfaces:**
- Consumes all previous tasks.

- [ ] **Step 1: Run focused tests**
  - `mvn -Dtest=PaymentIntentControllerTest,PaymentIntentServiceTest,PaymentProfileControllerTest test`

- [ ] **Step 2: Run frontend build**
  - `npm run build`

- [ ] **Step 3: Review**
  - Use `api-review`, `ui-review`, `tdd-review`, and `code-review`.

- [ ] **Step 4: Release note**
  - Record the tenant settings page, staff quick-pay page, display page, and validation commands.
