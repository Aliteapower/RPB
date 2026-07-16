# Staff Reservation and Queue Single-Column Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Reservation and Queue employee H5 pages use a full-width vertical outer content flow at every viewport width while preserving all other UI and behavior.

**Architecture:** Keep `StaffPrimaryWorkbench` unchanged and retain page ownership of page-specific composition. Update only the focused source-contract test and the obsolete 1024px outer-grid CSS in the Reservation and Queue pages; keep Queue's internal 1200px result-card grid.

**Tech Stack:** Vue 3 single-file components, scoped CSS, Java 21/JUnit 5/AssertJ source-contract tests, Maven, Vite, Playwright browser verification.

## Global Constraints

- Reservation order is date switcher, quick actions, today list, then action error.
- Queue order is management/filters, messages, then empty state or ticket list.
- The outer content flow is single-column on phones, tablet portrait, tablet landscape, and wider viewports.
- Queue ticket cards retain their internal two-column layout at 1200px and wider.
- Do not change templates, shared shell/navigation, Home, Table, business logic, APIs, permissions, translations, dependencies, database, migrations, or runtime configuration.

---

### Task 1: Add the single-column regression contract

**Files:**
- Modify: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java:95-142`

**Interfaces:**
- Consumes: Vue page source strings through `FrontendSourceSupport.readString(Path)`.
- Produces: a source-level contract that rejects the two obsolete outer grid definitions while preserving Queue's internal result-card grid.

- [ ] **Step 1: Change the Reservation assertion before production CSS**

Replace the old required two-column assertion with this rejection:

```java
assertThat(reservation)
    .contains("class=\"reservation-workbench__date-panel\"")
    .contains("class=\"reservation-workbench__quick-panel\"")
    .contains("class=\"reservation-workbench__list-panel\"")
    .contains("@media (min-width: 768px)")
    .doesNotContain("grid-template-columns: minmax(280px, 0.38fr) minmax(0, 0.62fr);")
    .doesNotContain("grid-column: 2;");
```

- [ ] **Step 2: Change the Queue assertion before production CSS**

Keep the internal result-grid assertions and reject only the old outer split:

```java
assertThat(queue)
    .contains("@media (min-width: 768px)")
    .doesNotContain("grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);")
    .contains("@media (min-width: 1200px)")
    .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest" test
```

Expected: FAIL because both Vue pages still contain their obsolete large-screen outer grid strings. Confirm the failure is an assertion failure, not a compilation or fixture error.

---

### Task 2: Remove only the obsolete outer two-column CSS

**Files:**
- Modify: `src/pages/ReservationTodayViewPage.vue:615-642`
- Modify: `src/pages/QueueTicketListPage.vue:1888-1906`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`

**Interfaces:**
- Consumes: existing DOM source order and the shared `StaffPrimaryWorkbench` content surface.
- Produces: single-column outer grids at all widths with unchanged component props, emits, state, and routes.

- [ ] **Step 1: Remove the Reservation 1024px outer-grid block**

Delete the complete rule below and do not change the preceding 768px padding rule:

```css
@media (min-width: 1024px) {
  .reservation-workbench-body {
    align-items: start;
    grid-template-columns: minmax(280px, 0.38fr) minmax(0, 0.62fr);
  }

  .reservation-workbench__date-panel,
  .reservation-workbench__quick-panel {
    grid-column: 1;
  }

  .reservation-workbench__date-panel {
    grid-row: 1;
  }

  .reservation-workbench__quick-panel {
    grid-row: 2;
  }

  .reservation-workbench__list-panel,
  .reservation-workbench__action-error {
    grid-column: 2;
  }

  .reservation-workbench__list-panel {
    grid-row: 1 / span 3;
    min-width: 0;
  }
}
```

- [ ] **Step 2: Remove the Queue 1024px outer-grid block**

Delete the complete rule below and retain the following 1200px internal `.queue-list` rule:

```css
@media (min-width: 1024px) {
  .queue-workbench-body {
    align-items: start;
    grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  }

  .queue-management-panel {
    grid-column: 1;
    grid-row: 1;
    min-width: 0;
  }

  .queue-message-stack,
  .empty-queue-panel,
  .queue-list {
    grid-column: 2;
    min-width: 0;
  }
}
```

- [ ] **Step 3: Run the focused contract and verify GREEN**

Run:

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest" test
```

Expected: 4 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.

- [ ] **Step 4: Run the focused regression suite**

Run:

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest,StaffUiV12TableSelectionValidationTest,FrontendI18nFoundationValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest,ReservationCreateDialogUiValidationTest,StaffReceptionClosedLoopUiValidationTest,TemporaryTableGroupUiValidationTest" test
```

Expected: 38 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.

- [ ] **Step 5: Run the frontend production build**

Run:

```powershell
npm run build
```

Expected: `vue-tsc --noEmit` and `vite build` succeed, including the Reservation and Queue chunks.

---

### Task 3: Verify responsive behavior and document delivery

**Files:**
- Create: `docs/release-notes/2026-07-16-staff-reservation-queue-single-column.md`
- Verify: `src/pages/ReservationTodayViewPage.vue`
- Verify: `src/pages/QueueTicketListPage.vue`

**Interfaces:**
- Consumes: the built frontend and existing employee authentication/runtime behavior.
- Produces: visual evidence and a release note describing scope, validation, deployment status, and rollback.

- [ ] **Step 1: Run browser geometry checks**

Open Reservation and Queue in a local authenticated runtime at these viewports:

```text
390x844
768x1024
1024x768
1366x1024
```

For every viewport, assert:

```text
document.documentElement.scrollWidth <= document.documentElement.clientWidth
top region width is approximately equal to lower region width
top region bottom <= lower region top
locale switcher does not overlap topbar actions
```

Also visually inspect one portrait and one landscape screenshot for each page.

- [ ] **Step 2: Write the release note**

Include these sections with concrete results: `New`, `Changed`, `Migration`, `Permission`, `Risk`, `Verification`, `Deployment`, and `Rollback Notes`. State that the change is frontend-only and not yet deployed unless production deployment is separately authorized and completed.

- [ ] **Step 3: Review scope and whitespace**

Run:

```powershell
git diff --check
git diff --stat
git status --short
```

Expected: only the two pages, focused test, plan/release documentation, and no unrelated files.

- [ ] **Step 4: Commit and Push**

Run:

```powershell
git add -- src/pages/ReservationTodayViewPage.vue src/pages/QueueTicketListPage.vue src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java docs/superpowers/plans/2026-07-16-staff-reservation-queue-single-column.md docs/release-notes/2026-07-16-staff-reservation-queue-single-column.md
git commit -m "fix: stack staff reservation and queue workbenches"
git push origin master
```

Expected: commit succeeds, `origin/master` matches local `master`, and the final working tree is clean.
