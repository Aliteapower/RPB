# Tenant Administrator Self-Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tenant administrator self-maintenance in tenant backend Employee Management with server-side tenant and account isolation.

**Architecture:** Add explicit `staff/me` tenant-admin endpoints backed by a repository method that selects the authenticated account id with `actor_type = 'tenant_admin'`. Keep ordinary staff endpoints limited to `actor_type = 'staff'`, and make the Vue staff page render the self-admin account as a protected row.

**Tech Stack:** Spring Boot, JdbcTemplate, PostgreSQL, MockMvc integration tests, Vue 3, Pinia, TypeScript, Vite.

---

### Task 1: Backend Contract Tests

**Files:**
- Modify: `src/test/java/com/rpb/reservation/auth/integration/TenantAdminApiIntegrationTest.java`

- [x] Add a failing integration test that logs in as `20000000`, calls `GET /api/v1/stores/{storeId}/tenant-admin/staff/me`, and expects `accountType = tenant_admin`, `self = true`, and employee number `20000000`.
- [x] Extend the test to patch `staff/me` with name, phone, email, and password `ADM123`, then verify login works with `adm123`.
- [x] Add a failing integration assertion that `PATCH /staff/{tenantAdminAccountId}` returns `STAFF_NOT_FOUND`, proving normal staff endpoints do not update tenant admins.
- [x] Add a failing integration assertion that staff user `1000` calling `GET /staff/me` returns `FORBIDDEN`.

Run: `mvn "-Dtest=TenantAdminApiIntegrationTest" test`
Expected before implementation: FAIL because `staff/me` is not mapped.

### Task 2: Backend Implementation

**Files:**
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminController.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminStaffItemResponse.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminStaff.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/application/TenantAdminStaffService.java`
- Modify: `src/main/java/com/rpb/reservation/tenantadmin/persistence/TenantAdminStaffRepository.java`
- Modify if needed: `src/main/java/com/rpb/reservation/tenantadmin/api/TenantAdminScopeResolver.java`

- [x] Add `CurrentActor` access from the controller or resolver so the service receives the authenticated account id.
- [x] Add `getCurrentTenantAdmin(scope, actorId)` and `updateCurrentTenantAdmin(scope, actorId, command)` service methods.
- [x] Add repository methods that select/update `auth_accounts` where `id = actorId`, `tenant_id = scope.tenantId`, `actor_type = 'tenant_admin'`, and active store access includes `scope.storeId`.
- [x] Add additive response fields `accountType`, `self`, `editable`, and `statusEditable`.
- [x] Reject self-update payloads that try to change `employeeNo` or `status`; accept name, phone, email, and optional valid password.

Run: `mvn "-Dtest=TenantAdminApiIntegrationTest" test`
Expected after implementation: PASS.

### Task 3: Frontend API and Routes

**Files:**
- Modify: `src/api/tenantAdminApi.ts`
- Modify: `src/router/index.ts`

- [x] Extend `TenantAdminStaff` with `accountType`, `self`, `editable`, and `statusEditable`.
- [x] Add `getCurrentTenantAdminStaff(storeId)` and `updateCurrentTenantAdminStaff(storeId, request)`.
- [x] Add route `/stores/:storeId/admin/staff/me/edit` named `tenant-admin-staff-self-edit`.

Run: `npm run build`
Expected: TypeScript passes.

### Task 4: Frontend Staff UI

**Files:**
- Modify: `src/pages/TenantAdminStaffPage.vue`
- Modify: `src/pages/TenantAdminStaffFormPage.vue`
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`

- [x] Make the staff list load the self-admin row and render it above ordinary staff.
- [x] Route protected admin row edit action to `tenant-admin-staff-self-edit`.
- [x] In self-edit mode, load/save through `staff/me`, keep employee number read-only, and hide or disable status editing.
- [x] Add static UI validation tests for the self-admin row, self route, and self endpoint calls.

Run: `mvn "-Dtest=AuthLoginUiValidationTest" test`
Expected: PASS.

### Task 5: Release, Verification, Deployment

**Files:**
- Create: `docs/release-notes/2026-07-05-tenant-admin-self-maintenance.md`

- [x] Write release notes with no-migration statement, permission impact, risk, and rollback.
- [x] Run `mvn "-Dtest=TenantAdminApiIntegrationTest,AuthLoginUiValidationTest" test`.
- [x] Run `npm run build`.
- [x] Run `git diff --check`.
- [x] Commit and push the branch.
- [x] Deploy backend jar and frontend dist to `booking.yumstone.sg`.
- [x] Smoke test production `/api/v1/auth/me`, tenant admin staff page asset, and nginx/backend service status.
