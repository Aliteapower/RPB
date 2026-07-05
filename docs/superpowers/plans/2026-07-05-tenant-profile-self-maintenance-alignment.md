# Tenant Profile Self-Maintenance Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align tenant backend self-maintenance with platform Tenant Management profile fields.

**Architecture:** Reuse existing tenant profile and tenant administrator self-account APIs. Keep backend contracts unchanged and compose both API results in `TenantAdminStaffFormPage.vue` only when route mode is `self`.

**Tech Stack:** Vue 3, TypeScript, Vite, Spring Boot static UI validation tests.

---

### Task 1: UI Contract Test

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/AuthLoginUiValidationTest.java`

- [x] Extend the self-admin staff management UI validation test so it requires `TenantAdminStaffFormPage.vue` to import and use `getTenantProfile`, `updateTenantProfile`, `uploadTenantProfileLogo`, and `clearTenantProfileLogo`.
- [x] Require the self page source to contain tenant profile field labels: `租户资料`, `租户名称`, `默认语言`, `负责人`, `电话`, `租户地址`, and `租户 LOGO`.
- [x] Require the self page source to contain account section labels: `管理员账号`, `员工号`, `姓名`, `电邮`, and `修改密码`.

Run: `mvn "-Dtest=AuthLoginUiValidationTest#tenantAdminStaffManagementSupportsProtectedSelfAdminMaintenance" test`

Expected before implementation: FAIL because self mode does not load tenant profile data.

### Task 2: Combined Self Page

**Files:**
- Modify: `src/pages/TenantAdminStaffFormPage.vue`

- [x] Add tenant profile state, logo file state, local logo preview, and helper methods equivalent to `TenantAdminProfilePage.vue`.
- [x] In self mode, load both tenant profile and current administrator account with `Promise.all`.
- [x] In self mode, save tenant profile and current administrator account together, then upload a selected logo if present.
- [x] Render two sections in self mode: `租户资料` and `管理员账号`.
- [x] Hide account phone in self mode and use tenant profile `contactPhone` as the tenant phone.

Run: `npm run build`

Expected after implementation: PASS.

### Task 3: Verification And Release Notes

**Files:**
- Create: `docs/release-notes/2026-07-05-tenant-profile-self-maintenance-alignment.md`

- [x] Run `mvn "-Dtest=AuthLoginUiValidationTest,TenantAdminApiIntegrationTest" test`.
- [x] Run `npm run build`.
- [x] Run `git diff --check`.
- [x] Write release notes with validation, deployment, and rollback notes.
