# Reservation Public Share H5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build tokenized public reservation share links and a customer-facing H5 page with direct sharing first and copy fallback.

**Architecture:** Staff share-info remains protected by App Gate and now creates/reuses one active public share token for the reservation. A new public token endpoint resolves only customer-safe read-model fields without staff actor context. Vue adds a public H5 route and updates the staff share panel to call the Web Share API before falling back to copy.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/Flyway, JUnit/MockMvc, Vue 3, TypeScript, Vite.

---

## File Structure

- Create `src/main/resources/db/migration/V017__reservation_public_share_tokens.sql`: token table, status check, unique token, unique active reservation token.
- Create backend application/read-model files under `src/main/java/com/rpb/reservation/reservation/application`: public share token/result/error records and service.
- Create/extend persistence under `src/main/java/com/rpb/reservation/reservation/persistence`: JDBC repository for token creation/reuse and public token lookup.
- Extend `ReservationShareInfo` and its response mapper to expose `shareToken`, `sharePath`, `shareTitle`, and `shareSummary`.
- Create `ReservationPublicShareController` and public response/error DTOs under `src/main/java/com/rpb/reservation/reservation/api`.
- Add tests in existing reservation test packages for application behavior, migration shape, public API behavior, and UI source validation.
- Create frontend `src/api/reservationPublicShareApi.ts`, `src/types/reservationPublicShare.ts`, and `src/pages/ReservationPublicSharePage.vue`.
- Modify `src/router/index.ts`, `ReservationShareCopyPanel.vue`, `ReservationTodayListItem.vue`, and `CreateReservationDialog.vue` for public route/share-first behavior.

## API And Database Review Notes

- Public endpoint: `GET /api/v1/public/reservation-shares/{token}`.
- Staff endpoint remains `GET /api/v1/stores/{storeId}/reservations/{reservationId}/share-info`.
- Staff endpoint remains protected. Public endpoint is intentionally unauthenticated and token-scoped only.
- Token table is store-operational and requires `tenant_id`, `store_id`, and `reservation_id`.
- No idempotency key is required because both endpoints are read-style operations; token creation/reuse is internally idempotent by unique active reservation token.
- No business audit row is required because this does not mutate Reservation, QueueTicket, Seating, Cleaning, or staff permissions.

## TDD Checklist

### Task 1: Migration Contract

**Files:**
- Create: `src/main/resources/db/migration/V017__reservation_public_share_tokens.sql`
- Create: `src/test/java/com/rpb/reservation/reservation/persistence/ReservationPublicShareTokenMigrationTest.java`

- [ ] **Step 1: Write failing migration source validation test**

Create `ReservationPublicShareTokenMigrationTest` that reads `V017__reservation_public_share_tokens.sql` and asserts it contains `reservation_public_share_tokens`, `tenant_id uuid not null`, `store_id uuid not null`, `reservation_id uuid not null`, `token text not null`, `status text not null`, `expires_at timestamptz null`, `created_at timestamptz not null`, `updated_at timestamptz not null`, `uq_reservation_public_share_tokens_token`, and a partial active unique index.

- [ ] **Step 2: Run red test**

Run: `mvn -q "-Dtest=ReservationPublicShareTokenMigrationTest" test`
Expected: FAIL because the migration file does not exist.

- [ ] **Step 3: Add migration**

Create the V017 migration with the required table, FK to `reservations(tenant_id, store_id, id)`, status check for `active`/`revoked`, unique token constraint, partial unique active index on `(tenant_id, store_id, reservation_id)`, and lookup index.

- [ ] **Step 4: Run green test**

Run: `mvn -q "-Dtest=ReservationPublicShareTokenMigrationTest" test`
Expected: PASS.

### Task 2: Backend Application And Persistence

**Files:**
- Modify: `src/main/java/com/rpb/reservation/reservation/application/ReservationShareInfo.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationShareInfoApplicationService.java`
- Create: public share application records and repository ports.
- Create/modify: JDBC token repository.
- Modify tests: `ReservationShareInfoApplicationServiceTest`.

- [ ] **Step 1: Write failing application tests**

Add tests that assert staff share info includes token/path/title/summary, reuses an existing active token, and public token lookup returns customer-safe fields while missing/revoked/expired tokens fail with stable errors.

- [ ] **Step 2: Run red test**

Run: `mvn -q "-Dtest=ReservationShareInfoApplicationServiceTest,ReservationPublicShareApplicationServiceTest" test`
Expected: FAIL because token/public share types and service do not exist yet.

- [ ] **Step 3: Implement application records, ports, service, and token generator**

Add minimal records and service methods:

```java
ensureShareToken(StoreScope scope, UUID reservationId)
getPublicShare(String token)
```

Generate 32 random bytes and encode with base64url without padding. Build `sharePath` as `/reservation-share/{token}`.

- [ ] **Step 4: Implement JDBC repository**

Use PostgreSQL `insert ... on conflict ... do update returning ...` or select-then-insert with unique conflict handling to reuse one active token per reservation. Public lookup joins token, reservation, store, customer, and preassignment data and filters by token only.

- [ ] **Step 5: Run green tests**

Run: `mvn -q "-Dtest=ReservationShareInfoApplicationServiceTest,ReservationPublicShareApplicationServiceTest" test`
Expected: PASS.

### Task 3: Public API

**Files:**
- Create: `ReservationPublicShareController.java`
- Create: `ReservationPublicShareResponse.java`
- Create: `ReservationPublicShareApiErrorResponse.java`
- Create/modify tests: `ReservationPublicShareApiIntegrationTest.java`
- Modify: `ReservationShareInfoResponse.java`, `ReservationShareInfoApiMapper.java`

- [ ] **Step 1: Write failing API integration tests**

Add tests for:

- Staff share-info includes `shareToken`, `sharePath`, `shareTitle`, `shareSummary`, and existing `shareText`.
- Public token endpoint returns safe fields without auth.
- Missing token returns `TOKEN_NOT_FOUND` with 404.
- Revoked and expired token return 410.
- Public endpoint is read-only for reservation/queue/seating/cleaning/idempotency/audit state.

- [ ] **Step 2: Run red tests**

Run: `mvn -q "-Dtest=ReservationShareInfoApiIntegrationTest,ReservationPublicShareApiIntegrationTest" test`
Expected: FAIL because public API and response fields are missing.

- [ ] **Step 3: Implement controller and response mapping**

Map public service errors to stable HTTP statuses and response bodies. Keep response DTO explicit; do not expose application rows or persistence rows directly.

- [ ] **Step 4: Run green tests**

Run: `mvn -q "-Dtest=ReservationShareInfoApiIntegrationTest,ReservationPublicShareApiIntegrationTest" test`
Expected: PASS.

### Task 4: Frontend API And H5 Page

**Files:**
- Create: `src/types/reservationPublicShare.ts`
- Create: `src/api/reservationPublicShareApi.ts`
- Create: `src/pages/ReservationPublicSharePage.vue`
- Modify: `src/router/index.ts`
- Modify validation test: `src/test/java/com/rpb/reservation/appgate/ui/ReservationShareInfoUiValidationTest.java`

- [ ] **Step 1: Write failing UI validation test**

Extend the UI validation test to assert the public route has `meta: { public: true }`, the H5 page and API files exist, the public API path is `/api/v1/public/reservation-shares/`, and staff shell text/actions are not present in the public page.

- [ ] **Step 2: Run red test**

Run: `mvn -q "-Dtest=ReservationShareInfoUiValidationTest" test`
Expected: FAIL because the route/page/API are missing.

- [ ] **Step 3: Implement frontend API/types/page/router**

The page loads by token, renders success/loading/not-found/expired/error states, uses `navigator.share` when available, and copy fallback otherwise.

- [ ] **Step 4: Run green test**

Run: `mvn -q "-Dtest=ReservationShareInfoUiValidationTest" test`
Expected: PASS.

### Task 5: Staff Share-First Interaction

**Files:**
- Modify: `src/types/reservationShareInfo.ts`
- Modify: `src/api/reservationShareInfoApi.ts`
- Modify: `src/components/reservation-workbench/ReservationShareCopyPanel.vue`
- Modify: `src/components/reservation-workbench/ReservationTodayListItem.vue`
- Modify: `src/components/reservation-workbench/CreateReservationDialog.vue`
- Modify validation test: `ReservationShareInfoUiValidationTest`.

- [ ] **Step 1: Write failing UI validation for share-first behavior**

Assert source contains `转发订位链接`, `navigator.share`, `sharePath`, `shareTitle`, `shareSummary`, and copy fallback text. Preserve existing manual `shareText` support.

- [ ] **Step 2: Run red test**

Run: `mvn -q "-Dtest=ReservationShareInfoUiValidationTest" test`
Expected: FAIL because staff components still copy plaintext first.

- [ ] **Step 3: Implement share-first behavior**

Update the component contract so callers pass the share URL metadata; component emits share requests. Callers load share info, build absolute URL from `window.location.origin + sharePath`, call Web Share API if available, and copy the link when sharing is unsupported.

- [ ] **Step 4: Run green test**

Run: `mvn -q "-Dtest=ReservationShareInfoUiValidationTest" test`
Expected: PASS.

### Task 6: Verification And Review

**Files:**
- Update any release notes or implementation report only if needed by final handoff.

- [ ] **Step 1: Run focused backend tests**

Run: `mvn -q "-Dtest=ReservationPublicShare*Test,ReservationShareInfo*Test,ReservationShareInfoUiValidationTest" test`
Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 3: Run local smoke check**

Restart backend if needed, apply V017 to local DB if Flyway is disabled, create or fetch a staff share token through the staff endpoint, then open `http://127.0.0.1:5176/reservation-share/{token}` and confirm HTTP 200 and customer-safe page.

- [ ] **Step 4: Review**

Use local API, DB, UI, TDD, code-review, and release-note skills for final report.
