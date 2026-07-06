# Tenant Store Switching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tenant administrators and staff use one account across explicitly authorized stores, manage ordinary staff store authorization from tenant admin, and switch stores by changing `/stores/:storeId/...` routes.

**Architecture:** Keep store authorization in Identity/RBAC data (`auth_account_store_access` and `auth_accounts.default_store_id`). Add a narrow `/api/v1/me/stores` read API for the current account's authorized store list. Tenant admin staff management updates ordinary staff authorization only; tenant admins cannot self-expand authorization through tenant backend. Store operation modules continue consuming `CurrentActor.canAccessStore(storeId)` and do not own switching state.

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL/JdbcTemplate, Vue 3, TypeScript, Pinia, Vue Router, vue-i18n, JUnit/MockMvc, static UI validation tests.

## Global Constraints

- No new database table is required; reuse `auth_accounts.default_store_id` and `auth_account_store_access`.
- Store authorization must stay tenant-scoped and must never allow cross-tenant store access.
- `tenant_admin` must not bypass store authorization in `CurrentActor.canAccessStore`.
- Platform administrators stay on platform routes; `/api/v1/me/stores` returns no tenant store switch targets for platform accounts.
- Tenant-prefixed hosts remain tenant identity context only; switching uses existing `/stores/:storeId/...` paths under the same host.
- User-facing frontend text must use `zh-CN` and `en-SG` i18n locale keys.
- Existing API calls that omit staff `storeIds` remain backward compatible: create defaults to the route store; update preserves existing authorization.
- Permission changes are critical and require audit coverage where the local module already has staff audit support.

---

## Files

- Modify: `src/main/java/com/rpb/reservation/walkin/api/CurrentActor.java` to remove the tenant-admin store bypass.
- Create: `src/main/java/com/rpb/reservation/auth/application/AuthStoreAccess.java` for current-account store switch rows.
- Modify: `src/main/java/com/rpb/reservation/auth/persistence/AuthRepository.java` to query authorized store switch rows.
- Create: `src/main/java/com/rpb/reservation/auth/api/AuthStoreAccessResponse.java` for `/api/v1/me/stores`.
- Modify: `src/main/java/com/rpb/reservation/auth/api/AuthController.java` to expose `GET /api/v1/me/stores`.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminStaff.java` to include `defaultStoreId` and `storeIds`.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminStaffItemResponse.java` to return authorization fields.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminStaffMutationRequest.java` to accept `defaultStoreId` and `storeIds`.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminStaffMutationCommand.java` to carry authorization input.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminStaffService.java` to validate and apply authorization.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/persistence/TenantAdminStaffRepository.java` to read/replace store access rows.
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java` to map the new request fields.
- Modify: `src/api/authApi.ts`, `src/types/auth.ts`, and `src/stores/authSession.ts` for current store list API/client state.
- Create: `src/components/common/StoreSwitcher.vue` as a reusable OOD UI component that only knows store switch rows and target mode.
- Modify: `src/components/tenant-admin/TenantAdminNav.vue` to render the switcher in admin mode.
- Modify: `src/components/staff-home/StaffHomeTopBar.vue` to render the switcher in staff mode.
- Modify: `src/pages/TenantAdminStaffFormPage.vue` for authorization checkboxes and default store select.
- Modify: `src/i18n/locales/zh-CN.ts` and `src/i18n/locales/en-SG.ts` for all new copy.
- Test: `src/test/java/com/rpb/reservation/auth/integration/AuthApiIntegrationTest.java`.
- Test: `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`.
- Test: controller/App Gate tests that currently rely on tenant-admin bypass.
- Test: static UI validation under `src/test/java/com/rpb/reservation/appgate/ui`.
- Add release note: `docs/release-notes/2026-07-07-tenant-store-switching.md`.

## Task 1: Backend Store Authorization Contract

**Interfaces:**
- Produces `GET /api/v1/me/stores`.
- Produces `CurrentActor.canAccessStore(UUID storeId)` that requires `storeIds.contains(storeId)`.

- [ ] **Step 1: Write failing tests**

Add tests proving `/api/v1/me/stores` returns the login account's authorized stores with `defaultStore`, and proving `tenant_admin` without a store id is denied by a protected store operation or App Gate path.

- [ ] **Step 2: Verify red**

Run: `mvn "-Dtest=AuthApiIntegrationTest,AppGateServiceTest" test`

Expected: FAIL because `/api/v1/me/stores` is not mapped and tenant admin still bypasses `storeIds`.

- [ ] **Step 3: Implement minimal backend**

Add `AuthStoreAccess`, response DTO, repository query joining `auth_account_store_access` to `stores`, controller endpoint, and strict `CurrentActor.canAccessStore`.

- [ ] **Step 4: Verify green**

Run: `mvn "-Dtest=AuthApiIntegrationTest,AppGateServiceTest" test`

Expected: PASS.

## Task 2: Tenant Admin Staff Store Authorization

**Interfaces:**
- Consumes `TenantAdminStaffMutationRequest.defaultStoreId`.
- Consumes `TenantAdminStaffMutationRequest.storeIds`.
- Produces staff responses with `defaultStoreId` and `storeIds`.

- [ ] **Step 1: Write failing tests**

Add integration tests that create a second store in the same tenant, create staff with two authorized stores and a default store, log in as that staff, and assert `storeIds` plus `/api/v1/me/stores`. Add update tests that remove one store and prove the removed store is rejected. Add a cross-tenant store id rejection test.

- [ ] **Step 2: Verify red**

Run: `mvn "-Dtest=TenantAdminApiIntegrationTest,AuthApiIntegrationTest" test`

Expected: FAIL because staff requests cannot carry store authorization yet.

- [ ] **Step 3: Implement minimal backend**

Extend command/request/response records, validate same-tenant active stores, replace ordinary staff store access rows in one transaction, update `default_store_id`, and preserve existing access when update omits authorization fields.

- [ ] **Step 4: Verify green**

Run: `mvn "-Dtest=TenantAdminApiIntegrationTest,AuthApiIntegrationTest" test`

Expected: PASS.

## Task 3: Frontend Store Switcher And Staff Authorization UI

**Interfaces:**
- Consumes `AuthStoreAccess[]` from `useAuthSessionStore`.
- Produces route-only switching to `/stores/:storeId/admin/profile` or `/stores/:storeId/staff`.
- Produces staff form `storeIds/defaultStoreId` payload.

- [ ] **Step 1: Write failing static validation tests**

Add UI validation requiring `StoreSwitcher.vue`, i18n keys, auth API client for `/api/v1/me/stores`, tenant admin nav integration, staff top bar integration, and staff form authorization controls.

- [ ] **Step 2: Verify red**

Run: `mvn "-Dtest=TenantStoreSwitchingUiValidationTest" test`

Expected: FAIL because files and references do not exist.

- [ ] **Step 3: Implement minimal frontend**

Add store switcher API/types/store state, reusable component, tenant admin nav use, staff top bar use, staff form authorization fields, and locale keys.

- [ ] **Step 4: Verify green**

Run: `mvn "-Dtest=TenantStoreSwitchingUiValidationTest" test`

Expected: PASS.

## Task 4: Release Note And Full Verification

**Interfaces:**
- Produces release documentation and final verification evidence.

- [ ] **Step 1: Add release note**

Document scope, API impact, migration statement, permission risk, tests, rollback, and known limits.

- [ ] **Step 2: Run targeted backend tests**

Run: `mvn "-Dtest=AuthApiIntegrationTest,TenantAdminApiIntegrationTest,AppGateServiceTest" test`

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run: `npm run build`

Expected: PASS.

- [ ] **Step 4: Run final review skills**

Use RPB `tdd-review`, `api-review`, `database-review`, `code-review`, and `release-note` checks before final response.
