# Platform Tenant Structure Mobile Usability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make platform group / tenant management and the operating entity / store structure flow easier to use on mobile while keeping the existing backend API contract.

**Architecture:** Keep API boundaries unchanged and improve the existing Vue components in place. `PlatformTenantTable.vue` owns list presentation; `PlatformTenantStructurePanel.vue` owns operating entity and store workflow presentation; locale files own all new text.

**Tech Stack:** Vue 3 SFC, vue-i18n, existing Java frontend-source validation tests, Maven test runner.

## Global Constraints

- Do not change platform tenant API paths, database schema, Flyway migrations, or backend authorization in this UI-only pass.
- Keep the platform list action model: edit, store structure, billing, delete/restore.
- Keep group onboarding behavior: `group_multi_store` redirects to `#tenant-structure`; `single_store` remains compatible.
- Maintain Chinese and English i18n coverage.
- Mobile layout must avoid horizontal table squeezing and action text overlap.

---

### Task 1: Lock UI Contract With Source Validation

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/PlatformGroupTenantOnboardingUiValidationTest.java`

**Interfaces:**
- Consumes: existing source validation helper `FrontendSourceSupport.readString`.
- Produces: assertions for mobile cards, structure summary, conditional forms, and updated i18n keys.

- [ ] **Step 1: Add assertions before implementation**

Add expectations that the tenant table exposes a mobile card list and that the structure panel exposes summary metrics and conditional forms.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=PlatformGroupTenantOnboardingUiValidationTest test`

Expected: failure mentioning missing mobile/structure strings.

- [ ] **Step 3: Keep test scoped**

The test should assert implementation-relevant strings/classes only, not pixel dimensions.

### Task 2: Improve Tenant List Mobile Presentation

**Files:**
- Modify: `src/components/platform/PlatformTenantTable.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`

**Interfaces:**
- Consumes: existing `PlatformTenant` data and table emits.
- Produces: desktop table unchanged and a mobile `.tenant-card-list` that uses the same emits.

- [ ] **Step 1: Add mobile card markup**

Render code, name, status, principal/phone/address summary, updated time, and actions.

- [ ] **Step 2: Add responsive CSS**

Hide the wide table and show cards under mobile breakpoints; keep desktop table intact.

- [ ] **Step 3: Update action copy**

Change the structure action copy from “门店 / Stores” to “门店结构 / Store structure”.

### Task 3: Rework Structure Panel Around Business Flow

**Files:**
- Modify: `src/components/platform/PlatformTenantStructurePanel.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`

**Interfaces:**
- Consumes: existing `operatingEntities`, `stores`, `saving`, and save emits.
- Produces: summary metrics, empty-state next actions, mobile tabs, and forms shown only after add/edit.

- [ ] **Step 1: Add local view state**

Track active mobile panel and whether the entity/store form is open.

- [ ] **Step 2: Add summary and empty-state actions**

Show counts for operating entities, stores, and active stores. Empty state should guide “create entity first, then store”.

- [ ] **Step 3: Make forms conditional**

Open forms via add/edit buttons. Keep required fields first and place secondary fields in `details` sections.

### Task 4: Verify and Review

**Files:**
- Modify only files from Tasks 1-3 unless validation reveals a directly related issue.

**Interfaces:**
- Consumes: changed Vue/i18n/test files.
- Produces: passing focused validation and frontend build.

- [ ] **Step 1: Run focused Java validation**

Run: `./mvnw -Dtest=PlatformGroupTenantOnboardingUiValidationTest,AuthLoginUiValidationTest test`

- [ ] **Step 2: Run frontend build**

Run the repository frontend build command from `package.json`.

- [ ] **Step 3: Review UI against mobile screenshots**

Confirm no overlapping row actions, first screen exposes business next actions, and Chinese/English labels fit.
