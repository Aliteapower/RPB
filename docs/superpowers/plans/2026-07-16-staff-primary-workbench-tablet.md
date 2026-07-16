# Tenant Staff Primary Workbench Tablet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the tenant staff Home, Reservation, Queue, and Table primary H5 pages work well on tablet portrait and landscape while leaving the existing mobile H5 UI and every business behavior unchanged.

**Architecture:** Add a presentation-only `StaffPrimaryWorkbench` shell that composes the four primary pages and opts the existing `StaffBottomNav` into an adaptive left-rail mode. Keep the global 520px mobile shell as the default, scope tablet width to the new component, and let each page own only its semantic tablet grids through CSS media queries.

**Tech Stack:** Vue 3 SFC, TypeScript, Vue Router, scoped CSS, Java 21 JUnit 5 source-validation tests, AssertJ, Maven, Vite, vue-tsc.

## Global Constraints

- Only `StoreStaffHomePage.vue`, `ReservationTodayViewPage.vue`, `QueueTicketListPage.vue`, and `TableResourceListPage.vue` opt into the new primary workbench shell.
- `<768px` must retain the current mobile H5 presentation and fixed bottom navigation.
- `768-1023px` uses the 88px left rail and tablet portrait layouts.
- `>=1024px` uses landscape split work areas; Queue switches to two ticket columns only at `>=1200px`.
- The primary workbench maximum width is exactly 1200px.
- Primary tablet actions remain at least 40px high, with 44px preferred where the existing control is a primary action.
- Login and staff secondary-operation pages remain unchanged.
- Do not modify APIs, routes, permissions, Pinia state, business handlers, state machines, i18n copy, database schema, migrations, dependencies, or runtime configuration.
- Keep Reservation, QueueTicket, Table, TableGroup, CheckIn, Seating, and Cleaning business rules out of the shared shell.
- Browser validation that needs a backend must use the PostgreSQL runtime in `target/local-postgres-current.txt`; never use a hard-coded database port.

---

## File Structure

- Create `src/components/staff/StaffPrimaryWorkbench.vue`: presentation-only primary shell, content slot, and adaptive navigation assembly.
- Modify `src/components/staff/StaffBottomNav.vue`: explicit `displayMode` interface and tablet left-rail CSS owned by the navigation component.
- Modify `src/pages/StoreStaffHomePage.vue`: adopt the shell and add Home portrait/landscape grids.
- Modify `src/pages/ReservationTodayViewPage.vue`: adopt the shell, add placement classes, and add Reservation portrait/landscape grids.
- Modify `src/pages/QueueTicketListPage.vue`: adopt the shell and add Queue management/ticket grids.
- Modify `src/pages/TableResourceListPage.vue`: adopt the shell and add Table summary/resource/temporary-group grids.
- Create `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`: focused TDD contract for scope, component boundaries, breakpoints, and page layouts.
- Create `docs/release-notes/2026-07-16-staff-primary-workbench-tablet.md`: UI-only release note and verification evidence.

The base `src/styles/staffWorkbench.css` remains unchanged so its 520px default continues to protect mobile and secondary staff flows.

---

### Task 1: Lock the responsive contract with a failing source-validation test

**Files:**
- Create: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`
- Reference: `docs/superpowers/specs/2026-07-16-staff-primary-workbench-tablet-design.md`

**Interfaces:**
- Consumes: `FrontendSourceSupport.readString(Path)` from the existing UI test package.
- Produces: four focused JUnit test methods that later tasks can run independently.

- [ ] **Step 1: Write the complete failing test**

```java
package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffPrimaryWorkbenchTabletUiValidationTest {

    private static final Path SHELL = Path.of(
        "src", "components", "staff", "StaffPrimaryWorkbench.vue"
    );

    @Test
    void sharedPrimaryWorkbenchOwnsOnlyAdaptiveShellAndNavigation() throws Exception {
        String shell = FrontendSourceSupport.readString(SHELL);
        String nav = FrontendSourceSupport.readString(
            Path.of("src", "components", "staff", "StaffBottomNav.vue")
        );
        String baseShell = FrontendSourceSupport.readString(
            Path.of("src", "styles", "staffWorkbench.css")
        );

        assertThat(shell)
            .contains("storeId: string")
            .contains("activeTab: StaffBottomNavTab")
            .contains("<slot />")
            .contains("display-mode=\"adaptive-primary\"")
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: 88px minmax(0, 1fr);")
            .contains("max-width: 1200px;")
            .doesNotContain("fetch(")
            .doesNotContain("watch(")
            .doesNotContain("onMounted(");

        assertThat(nav)
            .contains("displayMode?: 'bottom' | 'adaptive-primary'")
            .contains("staff-bottom-nav--adaptive-primary")
            .contains("position: sticky;")
            .contains("height: 100dvh;")
            .contains("grid-template-columns: minmax(0, 1fr);");

        assertThat(baseShell)
            .contains("max-width: 520px;")
            .doesNotContain("max-width: 1200px;");
    }

    @Test
    void exactlyFourPrimaryPagesAdoptTheSharedWorkbench() throws Exception {
        List<Path> primaryPages = List.of(
            Path.of("src", "pages", "StoreStaffHomePage.vue"),
            Path.of("src", "pages", "ReservationTodayViewPage.vue"),
            Path.of("src", "pages", "QueueTicketListPage.vue"),
            Path.of("src", "pages", "TableResourceListPage.vue")
        );
        List<Path> excludedPages = List.of(
            Path.of("src", "pages", "LoginPage.vue"),
            Path.of("src", "pages", "WalkInQueuePage.vue"),
            Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
            Path.of("src", "pages", "ReservationCheckInPage.vue"),
            Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")
        );

        for (Path page : primaryPages) {
            assertThat(FrontendSourceSupport.readString(page))
                .as("%s should use the primary workbench", page)
                .contains("StaffPrimaryWorkbench")
                .doesNotContain("<StaffBottomNav");
        }
        for (Path page : excludedPages) {
            assertThat(FrontendSourceSupport.readString(page))
                .as("%s should stay outside the primary workbench", page)
                .doesNotContain("StaffPrimaryWorkbench");
        }

        List<Path> adopters = new ArrayList<>();
        try (var pages = Files.list(Path.of("src", "pages"))) {
            for (Path page : pages.filter(path -> path.toString().endsWith("Page.vue")).toList()) {
                if (FrontendSourceSupport.readString(page).contains("StaffPrimaryWorkbench")) {
                    adopters.add(page);
                }
            }
        }
        assertThat(adopters).containsExactlyInAnyOrderElementsOf(primaryPages);
    }

    @Test
    void homeAndReservationDefinePortraitAndLandscapeLayouts() throws Exception {
        String home = FrontendSourceSupport.readString(
            Path.of("src", "pages", "StoreStaffHomePage.vue")
        );
        String reservation = FrontendSourceSupport.readString(
            Path.of("src", "pages", "ReservationTodayViewPage.vue")
        );

        assertThat(home)
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr));")
            .contains("@media (min-width: 1024px)")
            .contains(".overview-section")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");

        assertThat(reservation)
            .contains("class=\"reservation-workbench__date-panel\"")
            .contains("class=\"reservation-workbench__quick-panel\"")
            .contains("class=\"reservation-workbench__list-panel\"")
            .contains("@media (min-width: 768px)")
            .contains("@media (min-width: 1024px)")
            .contains("grid-template-columns: minmax(280px, 0.38fr) minmax(0, 0.62fr);");
    }

    @Test
    void queueAndTableDefineDeterministicTabletGrids() throws Exception {
        String queue = FrontendSourceSupport.readString(
            Path.of("src", "pages", "QueueTicketListPage.vue")
        );
        String table = FrontendSourceSupport.readString(
            Path.of("src", "pages", "TableResourceListPage.vue")
        );

        assertThat(queue)
            .contains("@media (min-width: 768px)")
            .contains("@media (min-width: 1024px)")
            .contains("grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);")
            .contains("@media (min-width: 1200px)")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr));");

        assertThat(table)
            .contains("@media (min-width: 768px)")
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr));")
            .contains("@media (min-width: 1024px)")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr));")
            .contains("grid-template-columns: minmax(160px, 0.8fr) minmax(220px, 1fr) minmax(260px, 1.4fr);");
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest" test
```

Expected: FAIL because `src/components/staff/StaffPrimaryWorkbench.vue` does not exist.

- [ ] **Step 3: Commit the failing contract**

```powershell
git add -- src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java
git commit -m "test: define staff tablet workbench contract"
```

---

### Task 2: Add the shared primary workbench shell and adaptive navigation rail

**Files:**
- Create: `src/components/staff/StaffPrimaryWorkbench.vue`
- Modify: `src/components/staff/StaffBottomNav.vue`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`

**Interfaces:**
- Consumes: `StaffBottomNavTab`, existing `StaffBottomNav` route construction, and the global `.staff-workbench-shell` mobile base.
- Produces: `StaffPrimaryWorkbench` props `{ storeId: string; activeTab: StaffBottomNavTab }` and `StaffBottomNav.displayMode?: 'bottom' | 'adaptive-primary'`.

- [ ] **Step 1: Create `StaffPrimaryWorkbench.vue` with no business state**

```vue
<script setup lang="ts">
import StaffBottomNav from './StaffBottomNav.vue'
import type { StaffBottomNavTab } from './staffBottomNavItems'

defineProps<{
  storeId: string
  activeTab: StaffBottomNavTab
}>()
</script>

<template>
  <main class="staff-workbench-shell staff-primary-workbench">
    <div class="staff-primary-workbench__surface">
      <slot />
    </div>
    <StaffBottomNav
      :store-id="storeId"
      :active-tab="activeTab"
      display-mode="adaptive-primary"
    />
  </main>
</template>

<style scoped>
.staff-primary-workbench__surface {
  min-width: 0;
}

@media (min-width: 768px) {
  .staff-workbench-shell.staff-primary-workbench {
    display: grid;
    grid-template-columns: 88px minmax(0, 1fr);
    max-width: 1200px;
  }

  .staff-primary-workbench__surface {
    grid-column: 2;
    grid-row: 1;
    min-height: 100dvh;
  }

  .staff-primary-workbench__surface :deep(.staff-topbar) {
    margin-left: 0;
    margin-right: 0;
  }
}
</style>
```

- [ ] **Step 2: Add the explicit presentation interface to `StaffBottomNav.vue`**

Update the props and navigation class without changing item construction:

```ts
const props = withDefaults(defineProps<{
  storeId: string
  activeTab: StaffBottomNavTab
  displayMode?: 'bottom' | 'adaptive-primary'
}>(), {
  displayMode: 'bottom'
})
```

```vue
<nav
  class="staff-bottom-nav"
  :class="`staff-bottom-nav--${displayMode}`"
  :aria-label="t('nav.staff.aria')"
>
```

Append after the existing 720px rule:

```css
@media (min-width: 768px) {
  .staff-bottom-nav--adaptive-primary {
    align-content: center;
    align-self: start;
    border-bottom: 0;
    border-left: 0;
    border-right: 1px solid #dbe3ee;
    border-top: 0;
    bottom: auto;
    box-shadow: 8px 0 24px rgba(15, 23, 42, 0.07);
    grid-column: 1;
    grid-row: 1;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: repeat(4, minmax(72px, auto));
    height: 100dvh;
    left: auto;
    max-width: none;
    padding: 20px 10px;
    position: sticky;
    top: 0;
    transform: none;
    width: 88px;
  }

  .staff-bottom-nav--adaptive-primary .staff-bottom-nav__item {
    gap: 6px;
    min-height: 72px;
    padding: 8px 4px;
  }

  .staff-bottom-nav--adaptive-primary .staff-bottom-nav__label {
    font-size: 0.78rem;
  }
}
```

- [ ] **Step 3: Run the shared-shell test method and verify GREEN**

Run:

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest#sharedPrimaryWorkbenchOwnsOnlyAdaptiveShellAndNavigation" test
```

Expected: PASS. The other test methods are allowed to remain red until Tasks 3 and 4.

- [ ] **Step 4: Run the frontend build**

```powershell
npm run build
```

Expected: PASS with vue-tsc and Vite completing successfully.

- [ ] **Step 5: Commit the shared UI module**

```powershell
git add -- src/components/staff/StaffPrimaryWorkbench.vue src/components/staff/StaffBottomNav.vue
git commit -m "feat: add adaptive staff workbench shell"
```

---

### Task 3: Adopt the shell and add Home and Reservation tablet layouts

**Files:**
- Modify: `src/pages/StoreStaffHomePage.vue`
- Modify: `src/pages/ReservationTodayViewPage.vue`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`

**Interfaces:**
- Consumes: `StaffPrimaryWorkbench(storeId, activeTab)` from Task 2.
- Produces: Home KPI/overview tablet grids and Reservation left/right component placement classes.

- [ ] **Step 1: Verify the Home/Reservation contract is still RED**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest#homeAndReservationDefinePortraitAndLandscapeLayouts" test
```

Expected: FAIL because the two pages do not yet use the shared shell or tablet media queries.

- [ ] **Step 2: Replace direct navigation assembly in `StoreStaffHomePage.vue`**

Replace the `StaffBottomNav` import with:

```ts
import StaffPrimaryWorkbench from '../components/staff/StaffPrimaryWorkbench.vue'
```

Change only the template wrapper and remove the direct bottom-navigation node. Replace:

```vue
<main class="staff-workbench-shell staff-shell">
```

with:

```vue
<StaffPrimaryWorkbench :store-id="storeId" active-tab="home" class="staff-shell">
```

Delete this exact node:

```vue
<StaffBottomNav :store-id="storeId" active-tab="home" />
```

Replace the root closing `</main>` immediately after the existing home body with:

```vue
</StaffPrimaryWorkbench>
```

Append the following styles after the existing mobile rule:

```css
@media (min-width: 768px) {
  .home-overview-body {
    padding: 16px 18px 24px;
  }

  .kpi-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .home-overview-body {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .date-strip,
  .overview-error,
  .operation-toolbar,
  .empty-state,
  .kpi-grid {
    grid-column: 1 / -1;
  }

  .overview-section {
    min-width: 0;
  }

  .status-grid--tables {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1200px) {
  .status-grid--tables {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
```

- [ ] **Step 3: Replace direct navigation assembly and add placement hooks in `ReservationTodayViewPage.vue`**

Replace the `StaffBottomNav` import with:

```ts
import StaffPrimaryWorkbench from '../components/staff/StaffPrimaryWorkbench.vue'
```

Replace the root opening tag:

```vue
<main class="staff-workbench-shell reservation-workbench">
```

with:

```vue
<StaffPrimaryWorkbench
  :store-id="storeId"
  active-tab="reservation"
  class="reservation-workbench"
>
```

Add these exact class attributes to the existing component opening tags while leaving every existing prop and event binding in place:

```vue
<StaffBusinessDateSwitcher
  class="reservation-workbench__date-panel"
```

```vue
<ReservationQuickActionPanel
  class="reservation-workbench__quick-panel"
```

```vue
<ReservationTodayListPanel
  class="reservation-workbench__list-panel"
```

Delete this exact node:

```vue
<StaffBottomNav :store-id="storeId" active-tab="reservation" />
```

Replace the root closing `</main>` after the existing dialogs with:

```vue
</StaffPrimaryWorkbench>
```

Append:

```css
@media (min-width: 768px) {
  .reservation-workbench-body {
    padding: 16px 18px 24px;
  }
}

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

- [ ] **Step 4: Run the Home/Reservation test and build**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest#homeAndReservationDefinePortraitAndLandscapeLayouts" test
npm run build
```

Expected: both commands PASS.

- [ ] **Step 5: Commit the first two page layouts**

```powershell
git add -- src/pages/StoreStaffHomePage.vue src/pages/ReservationTodayViewPage.vue
git commit -m "feat: adapt staff home and reservations for tablet"
```

---

### Task 4: Adopt the shell and add Queue and Table tablet layouts

**Files:**
- Modify: `src/pages/QueueTicketListPage.vue`
- Modify: `src/pages/TableResourceListPage.vue`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java`

**Interfaces:**
- Consumes: `StaffPrimaryWorkbench(storeId, activeTab)` from Task 2.
- Produces: deterministic Queue 1024/1200 grids and Table 768/1024 grids; completes adoption by exactly four pages.

- [ ] **Step 1: Verify the Queue/Table contract is still RED**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest#queueAndTableDefineDeterministicTabletGrids" test
```

Expected: FAIL because Queue and Table do not yet define the accepted tablet grids.

- [ ] **Step 2: Replace direct navigation assembly and add Queue grids**

Replace the `StaffBottomNav` import with:

```ts
import StaffPrimaryWorkbench from '../components/staff/StaffPrimaryWorkbench.vue'
```

Replace:

```vue
<main class="staff-workbench-shell queue-workbench">
```

with:

```vue
<StaffPrimaryWorkbench :store-id="storeId" active-tab="queue" class="queue-workbench">
```

Delete:

```vue
<StaffBottomNav :store-id="storeId" active-tab="queue" />
```

Replace the root closing `</main>` after the existing queue body with:

```vue
</StaffPrimaryWorkbench>
```

Replace the current 720px tablet rule with these deterministic rules:

```css
@media (min-width: 768px) {
  .queue-workbench-body {
    padding: 16px 18px 24px;
  }

  .queue-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .compact-filter-controls {
    align-items: end;
    grid-template-columns: minmax(0, 1fr) auto;
  }
}

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

@media (min-width: 1200px) {
  .queue-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
```

- [ ] **Step 3: Replace direct navigation assembly and add Table grids**

Replace the `StaffBottomNav` import with:

```ts
import StaffPrimaryWorkbench from '../components/staff/StaffPrimaryWorkbench.vue'
```

Replace:

```vue
<main class="staff-workbench-shell table-page">
```

with:

```vue
<StaffPrimaryWorkbench :store-id="storeId" active-tab="table" class="table-page">
```

Delete:

```vue
<StaffBottomNav :store-id="storeId" active-tab="table" />
```

Replace the root closing `</main>` after the existing table-switch dialog with:

```vue
</StaffPrimaryWorkbench>
```

Append after the existing mobile rule:

```css
@media (min-width: 768px) {
  .table-page-body {
    padding: 16px 18px 24px;
  }

  .table-page__resource-grid,
  .table-page__resource-grid--groups {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .temporary-group-panel {
    grid-template-columns: minmax(160px, 0.8fr) minmax(220px, 1fr) minmax(260px, 1.4fr);
  }

  .temporary-group-panel__actions {
    justify-content: flex-end;
  }

  .table-page__resource-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .table-page__resource-grid--groups {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
```

- [ ] **Step 4: Run the full responsive contract and build**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest" test
npm run build
```

Expected: all four responsive test methods PASS and the frontend build succeeds.

- [ ] **Step 5: Commit the final two page layouts**

```powershell
git add -- src/pages/QueueTicketListPage.vue src/pages/TableResourceListPage.vue
git commit -m "feat: adapt staff queue and tables for tablet"
```

---

### Task 5: Run regression and visual verification, review, and document the release

**Files:**
- Create: `docs/release-notes/2026-07-16-staff-primary-workbench-tablet.md`
- Review: all files changed in Tasks 1-4

**Interfaces:**
- Consumes: the completed four-page UI change and existing local runtime pointer.
- Produces: automated/build/browser evidence and a UI-only release note with rollback instructions.

- [ ] **Step 1: Run focused staff UI regression tests**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest,StaffUiV12TableSelectionValidationTest,FrontendI18nFoundationValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest,ReservationCreateDialogUiValidationTest,StaffReceptionClosedLoopUiValidationTest,TemporaryTableGroupUiValidationTest" test
```

Expected: all selected test classes PASS with zero failures and errors.

- [ ] **Step 2: Run final frontend verification**

```powershell
npm run build
git diff --check
```

Expected: the build succeeds and `git diff --check` prints no errors.

- [ ] **Step 3: Prepare the approved local validation runtime**

Read:

```powershell
Get-Content -LiteralPath 'target/local-postgres-current.txt'
```

Expected: a current pointer containing the local PostgreSQL port and runtime metadata. If it is missing or stale, follow `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md` before browser testing. Start the backend only with:

```text
jdbc:postgresql://127.0.0.1:<pointer-port>/postgres?stringtype=unspecified
username=postgres
password=<blank>
```

- [ ] **Step 4: Validate all four pages in the browser**

Use the existing local staff account/session and inspect every primary route at these exact viewport sizes:

```text
390x844
768x1024
1024x768
1366x1024
```

For each size, verify:

```text
Home: KPI columns, queue/table placement, top bar, navigation active state
Reservation: date/quick/list placement and all three dialogs opening/closing
Queue: filter placement, local chip scrolling, ticket actions, one/two-column threshold
Table: six summaries, three/four-column resources, filters, temporary group controls, dialog
All pages: no page-level horizontal scrollbar and no covered focus/touch targets
390x844 only: unchanged fixed bottom navigation and mobile reading order
```

- [ ] **Step 5: Perform repository code and UI review**

Read `docs/skills/code-review/SKILL.md` and `docs/skills/ui-review/SKILL.md`, then review the diff for:

```text
shared shell contains no business data or handlers
only four primary pages adopt the shell
base 520px shell is unchanged
no duplicated API calls, actions, messages, or dialogs
no route, permission, i18n copy, dependency, migration, or backend production edits
tablet selectors do not override widths below 768px
focus, active state, touch size, wrapping, and overflow remain usable
```

Fix every P0/P1/P2 finding and rerun the affected focused test plus `npm run build`.

- [ ] **Step 6: Write the UI-only release note**

Create `docs/release-notes/2026-07-16-staff-primary-workbench-tablet.md` with this content after every listed check has passed:

```markdown
# 2026-07-16 - Tenant staff primary workbench tablet UI

## Summary

The tenant staff Home, Reservation, Queue, and Table primary H5 pages now use a shared responsive workbench that preserves the mobile UI and adds tablet portrait and landscape layouts.

## Scope

- Added the shared `StaffPrimaryWorkbench` visual shell.
- Added the adaptive left-rail presentation to `StaffBottomNav`.
- Adapted only the four primary staff pages.
- Kept Login and staff secondary-operation pages outside the new shell.

## Shared UI Architecture

The shared shell owns only the 88px tablet rail, 1200px width cap, content surface, and navigation assembly. Each page continues to own its data, events, dialogs, and semantic content grid.

## Mobile Compatibility

Widths below 768px continue to use the base 520px H5 shell and fixed bottom navigation. Validation at 390x844 confirmed the existing reading order and navigation placement.

## Tablet Portrait and Landscape Behavior

- 768x1024 uses the tablet portrait rail and wider single-column work areas.
- 1024x768 uses split work areas for Home, Reservation, and Queue, plus four-column Table resources.
- 1366x1024 keeps the workbench centered at the 1200px maximum and uses two Queue ticket columns.

## Business and Data Impact

Frontend UI only. No API, database, migration, dependency, permission, route, or runtime configuration change.

## Validation

- `mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest,StaffUiV12TableSelectionValidationTest,FrontendI18nFoundationValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest,ReservationCreateDialogUiValidationTest,StaffReceptionClosedLoopUiValidationTest,TemporaryTableGroupUiValidationTest" test`
- `npm run build`
- `git diff --check`

## Browser Matrix

| Viewport | Result |
|---|---|
| 390x844 | Passed: mobile bottom navigation and reading order preserved. |
| 768x1024 | Passed: portrait rail and grids, no page overflow. |
| 1024x768 | Passed: landscape work areas, no page overflow. |
| 1366x1024 | Passed: capped shell and large-tablet grids, no page overflow. |

## Risk and Rollback

The primary risk is a scoped-CSS or wrapper regression in mobile sticky behavior. Revert the shared shell/navigation commit and the four page-layout commits together; no data rollback is required.
```

- [ ] **Step 7: Run final verification and commit the evidence**

```powershell
mvn "-Dtest=StaffPrimaryWorkbenchTabletUiValidationTest,StaffUiV12TableSelectionValidationTest,FrontendI18nFoundationValidationTest,StoreStaffHomePageAppGateRuntimeValidationTest,ReservationCreateDialogUiValidationTest,StaffReceptionClosedLoopUiValidationTest,TemporaryTableGroupUiValidationTest" test
npm run build
git diff --check
git add -- src/components/staff/StaffPrimaryWorkbench.vue src/components/staff/StaffBottomNav.vue src/pages/StoreStaffHomePage.vue src/pages/ReservationTodayViewPage.vue src/pages/QueueTicketListPage.vue src/pages/TableResourceListPage.vue src/test/java/com/rpb/reservation/appgate/ui/StaffPrimaryWorkbenchTabletUiValidationTest.java docs/release-notes/2026-07-16-staff-primary-workbench-tablet.md
git commit -m "docs: record staff tablet workbench release"
```

Expected: all verification commands PASS and the release-note commit succeeds.

---

## Completion Gate

Before claiming completion:

- Run the `superpowers:verification-before-completion` skill.
- Confirm `git status --short` is clean.
- Report the commits, exact automated commands, browser viewport results, and any validation that could not be performed.
- Do not claim mobile preservation or tablet correctness without the 390x844 and tablet browser checks.
