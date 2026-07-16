# Public Restaurant Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a public restaurant booking H5 flow where customers log in, choose date and meal-period slot, enter phone/remarks, and create a confirmed Reservation through the existing Reservation state machine.

**Architecture:** Keep Reservation creation and state transitions inside the existing `reservation` module. Add `customerauth` for customer email/social login sessions, `publicbooking` for customer-facing store profile/slot/create APIs, and tenant-admin configuration for public booking capacity. Capacity policy remains modular: staff reservations keep the current staff path, while customer-source reservations consume public booking quota.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/Flyway, JdbcTemplate/JPA, Vue 3, TypeScript, Vite, JUnit/MockMvc.

---

### Task 1: Public Booking Schema

**Files:**
- Create: `src/main/resources/db/migration/V024__public_booking_customer_auth.sql`
- Create: `src/test/java/com/rpb/reservation/publicbooking/persistence/PublicBookingMigrationTest.java`

- [ ] Write a failing source-validation test that asserts the migration creates customer auth tables, public booking settings, quota overrides, tenant/store constraints, status checks, provider checks, session indexes, and email code indexes.
- [ ] Run `mvn -q "-Dtest=PublicBookingMigrationTest" test` and confirm it fails because `V024__public_booking_customer_auth.sql` does not exist.
- [ ] Add the migration with:
  - `customer_auth_accounts`
  - `customer_auth_identities`
  - `customer_auth_sessions`
  - `customer_email_login_codes`
  - `store_public_booking_settings`
  - `store_public_booking_quota_overrides`
- [ ] Run the migration test and confirm it passes.

### Task 2: Reservation Capacity Policy

**Files:**
- Create: `src/main/java/com/rpb/reservation/reservation/application/port/out/ReservationCapacityPolicyPort.java`
- Create: `src/main/java/com/rpb/reservation/reservation/application/ReservationCapacityDecision.java`
- Create: `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingCapacityPolicy.java`
- Create: `src/main/java/com/rpb/reservation/publicbooking/application/PublicBookingCapacityQuery.java`
- Create: `src/main/java/com/rpb/reservation/publicbooking/application/port/out/PublicBookingSettingsRepositoryPort.java`
- Create: `src/main/java/com/rpb/reservation/publicbooking/application/port/out/PublicBookingTableCapacityRepositoryPort.java`
- Modify: `src/main/java/com/rpb/reservation/reservation/application/service/ReservationCreateApplicationService.java`
- Test: `src/test/java/com/rpb/reservation/reservation/application/ReservationCreateApplicationServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/publicbooking/application/PublicBookingCapacityPolicyTest.java`

- [ ] Write failing tests proving customer-source reservations are rejected when public quota is exhausted and staff-source reservations still use staff capacity behavior.
- [ ] Run focused tests and confirm failures.
- [ ] Implement a small capacity policy interface and default fallback decision.
- [ ] Implement public quota calculation: percentage of active table capacity, fixed table count, or fixed guest count.
- [ ] Inject the policy into `ReservationCreateApplicationService` while preserving existing constructors.
- [ ] Run focused tests and confirm they pass.

### Task 3: Customer Auth

**Files:**
- Create package: `src/main/java/com/rpb/reservation/customerauth/**`
- Modify: `pom.xml`
- Modify: `src/main/java/com/rpb/reservation/auth/security/AuthSecurityConfiguration.java`
- Test: `src/test/java/com/rpb/reservation/customerauth/application/CustomerAuthApplicationServiceTest.java`
- Test: `src/test/java/com/rpb/reservation/customerauth/api/CustomerAuthControllerTest.java`

- [ ] Write failing tests for email-code start/verify, session cookie authentication, provider identity upsert, and invalid/expired code rejection.
- [ ] Run focused tests and confirm failures.
- [ ] Implement customer-auth records, service, JDBC repository, cookie service, controller, current-customer provider, and optional OAuth success handler.
- [ ] Add optional Spring OAuth2 client support for Google/Facebook. Provider credentials remain environment configuration.
- [ ] Run focused tests and confirm they pass.

### Task 4: Public Booking API

**Files:**
- Create package: `src/main/java/com/rpb/reservation/publicbooking/api/**`
- Create package: `src/main/java/com/rpb/reservation/publicbooking/persistence/**`
- Test: `src/test/java/com/rpb/reservation/publicbooking/api/PublicBookingControllerTest.java`

- [ ] Write failing controller tests for profile, slots, unauthenticated reservation create, authenticated reservation create, disabled booking, and capacity exhausted.
- [ ] Run focused tests and confirm failures.
- [ ] Implement profile, slot, and create controllers with explicit DTOs and stable public error envelopes.
- [ ] Map public create to `CreateReservationCommand` with `actorType=customer`, `source=customer`, no table assignment.
- [ ] Run focused tests and confirm they pass.

### Task 5: Tenant Admin Public Booking Settings

**Files:**
- Extend or create tenant-admin backend settings endpoints.
- Create: `src/api/tenantAdminPublicBookingApi.ts`
- Create: `src/types/tenantAdminPublicBooking.ts`
- Modify: `src/router/index.ts`
- Modify: `src/components/tenant-admin/TenantAdminNav.vue`
- Create: `src/pages/TenantAdminPublicBookingPage.vue`
- Test: static UI validation under `src/test/java/com/rpb/reservation/appgate/ui`.

- [ ] Write failing backend/API/UI validation tests for get/update settings and route presence.
- [ ] Run focused tests and confirm failures.
- [ ] Implement settings endpoints and tenant-admin page.
- [ ] Run focused tests and frontend build.

### Task 6: Public Booking H5

**Files:**
- Create: `src/api/publicBookingApi.ts`
- Create: `src/api/customerAuthApi.ts`
- Create: `src/types/publicBooking.ts`
- Create: `src/types/customerAuth.ts`
- Create: `src/pages/PublicBookingPage.vue`
- Modify: `src/router/index.ts`
- Test: static UI validation under `src/test/java/com/rpb/reservation/appgate/ui`.

- [ ] Write failing UI validation tests for public route, date/meal-period slots, email login, Google/Facebook actions, phone/remark fields, and reservation success link.
- [ ] Run focused UI validation and confirm failures.
- [ ] Implement H5 page with loading/error/empty/success states.
- [ ] Use public slot API and public reservation create API with idempotency key.
- [ ] Run frontend build and focused UI validation.

### Task 7: Verification And Release

**Files:**
- Create: `docs/api/PUBLIC_BOOKING_API_CONTRACT.md`
- Create: `docs/release-notes/2026-06-29-public-restaurant-booking.md`

- [ ] Run focused backend tests:
  `mvn -q "-Dtest=PublicBooking*Test,CustomerAuth*Test,ReservationCreateApplicationServiceTest" test`
- [ ] Run frontend build: `npm run build`
- [ ] Run local source/security UI validations for tenant and public booking pages.
- [ ] Apply database-review, api-review, tdd-review, code-review, and release-note checklists.
- [ ] Document migration impact, OAuth environment requirements, rollback notes, and remaining production prerequisites.
